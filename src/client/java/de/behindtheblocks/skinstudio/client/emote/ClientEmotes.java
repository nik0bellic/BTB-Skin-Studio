package de.behindtheblocks.skinstudio.client.emote;

import de.behindtheblocks.skinstudio.emote.Emotes;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who is playing which emote right now (own and received), plus starting/stopping the own one. */
public final class ClientEmotes {
	private record Playing(String id, long startNanos) {
	}

	private static final Map<UUID, Playing> PLAYING = new ConcurrentHashMap<>();
	private static double startX, startZ;

	private ClientEmotes() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SkinStudioPayloads.EmotePlayed.TYPE, (p, ctx) -> {
			if (p.id().isEmpty()) {
				PLAYING.remove(p.player());
			} else if (Emotes.exists(p.id())) {
				PLAYING.put(p.player(), new Playing(p.id(), System.nanoTime()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> PLAYING.clear());
		ClientTickEvents.END_CLIENT_TICK.register(ClientEmotes::tick);
	}

	/** Starts an emote for the own player and tells the server (if it has the mod). */
	public static void play(String id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !Emotes.exists(id)) {
			return;
		}
		PLAYING.put(mc.player.getUUID(), new Playing(id, System.nanoTime()));
		startX = mc.player.getX();
		startZ = mc.player.getZ();
		send(id);
	}

	public static void stopOwn() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && PLAYING.remove(mc.player.getUUID()) != null) {
			send("");
		}
	}

	private static void send(String id) {
		if (ClientPlayNetworking.canSend(SkinStudioPayloads.PlayEmote.TYPE)) {
			ClientPlayNetworking.send(new SkinStudioPayloads.PlayEmote(id));
		}
	}

	/** Moving, jumping, sneaking or swimming ends the own emote; finished emotes are dropped. */
	private static void tick(Minecraft mc) {
		if (mc.player != null) {
			Playing own = PLAYING.get(mc.player.getUUID());
			if (own != null) {
				double dx = mc.player.getX() - startX, dz = mc.player.getZ() - startZ;
				if (dx * dx + dz * dz > 0.04 || mc.player.isShiftKeyDown() || !mc.player.onGround() || mc.player.isSwimming()) {
					stopOwn();
				}
			}
		}
		PLAYING.entrySet().removeIf(e -> sample(e.getValue()) == null);
	}

	/** Current pose of a player (see {@link Emotes#sample}) or null. */
	public static float[] pose(UUID player) {
		Playing p = player == null ? null : PLAYING.get(player);
		return p == null ? null : sample(p);
	}

	/** Prop cosmetic shown while the player's emote plays (or null). */
	public static de.behindtheblocks.skinstudio.client.render.ActiveCosmetic prop(UUID player) {
		Playing p = player == null ? null : PLAYING.get(player);
		return propOf(p == null ? null : p.id());
	}

	public static de.behindtheblocks.skinstudio.client.render.ActiveCosmetic propOf(String emote) {
		Emotes.Emote e = Emotes.get(emote);
		if (e == null || e.prop() == null) {
			return null;
		}
		var asset = de.behindtheblocks.skinstudio.client.library.CosmeticLibrary.get().prop(e.prop());
		return asset == null ? null : de.behindtheblocks.skinstudio.client.render.ActiveCosmetic.of(asset, 0);
	}

	private static float[] sample(Playing p) {
		return Emotes.sample(p.id(), (System.nanoTime() - p.startNanos()) / 1e9f);
	}
}
