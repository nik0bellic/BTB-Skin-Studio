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
	/** Emotes of replay puppets (mannequins), keyed by the profile they wear – separate from live players. */
	private static final Map<UUID, Playing> PUPPETS = new ConcurrentHashMap<>();
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
		ClientPlayNetworking.registerGlobalReceiver(SkinStudioPayloads.PuppetEmote.TYPE, (p, ctx) -> {
			if (p.id().isEmpty()) {
				PUPPETS.remove(p.profile());
			} else if (Emotes.exists(p.id())) {
				PUPPETS.put(p.profile(), new Playing(p.id(), System.nanoTime()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
			PLAYING.clear();
			PUPPETS.clear();
		});
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
		PUPPETS.entrySet().removeIf(e -> sample(e.getValue()) == null);
		particles(mc);
	}

	/** Current pose of a player (see {@link Emotes#sample}) or null. */
	public static float[] pose(UUID player) {
		Playing p = player == null ? null : PLAYING.get(player);
		return p == null ? null : sample(p);
	}

	/** Pose of a replay puppet wearing this profile (or null). */
	public static float[] puppetPose(UUID profile) {
		Playing p = profile == null ? null : PUPPETS.get(profile);
		return p == null ? null : sample(p);
	}

	public static net.minecraft.world.item.ItemStack puppetItem(UUID profile) {
		Playing p = profile == null ? null : PUPPETS.get(profile);
		return itemOf(p == null ? null : p.id());
	}

	/** Game item held in the right hand while the player's emote plays (or null). */
	public static net.minecraft.world.item.ItemStack item(UUID player) {
		Playing p = player == null ? null : PLAYING.get(player);
		return itemOf(p == null ? null : p.id());
	}

	public static net.minecraft.world.item.ItemStack itemOf(String emote) {
		Emotes.Emote e = Emotes.get(emote);
		if (e == null || e.item() == null) {
			return null;
		}
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.item()));
		return item == null ? null : new net.minecraft.world.item.ItemStack(item);
	}

	/** Small particle effects for some emotes (chips while mining, crumbs, sparkles, hearts …). */
	private static void particles(Minecraft mc) {
		if (mc.level == null || !de.behindtheblocks.skinstudio.client.data.ClientSettings.get().particles) {
			return;
		}
		long tick = mc.level.getGameTime();
		for (var e : PLAYING.entrySet()) {
			var player = mc.level.getPlayerByUUID(e.getKey());
			if (player == null || (player == mc.player && mc.options.getCameraType().isFirstPerson())) {
				continue;
			}
			float t = (System.nanoTime() - e.getValue().startNanos()) / 1e9f;
			double yaw = Math.toRadians(player.yBodyRot);
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			double hx = player.getX() + fx * 0.55 - fz * 0.35, hz = player.getZ() + fz * 0.55 + fx * 0.35;
			double hy = player.getY() + 1.1;
			var rnd = mc.level.getRandom();
			switch (e.getValue().id()) {
				case "mine" -> {
					if (tick % 4 == 0) {
						for (int i = 0; i < 4; i++) {
							mc.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
									net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), hx + fx * 0.3, hy - 0.5, hz + fz * 0.3,
									(rnd.nextDouble() - 0.5) * 0.2, 0.1, (rnd.nextDouble() - 0.5) * 0.2);
						}
					}
				}
				case "woodpunch" -> {
					if (tick % 5 == 0) {
						for (int i = 0; i < 3; i++) {
							mc.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
									net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()), hx + fx * 0.4, hy, hz + fz * 0.4,
									(rnd.nextDouble() - 0.5) * 0.15, 0.08, (rnd.nextDouble() - 0.5) * 0.15);
						}
					}
				}
				case "cookie" -> {
					if (tick % 6 == 0 && t > 0.4f) {
						mc.level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
								net.minecraft.world.item.Items.COOKIE), player.getX() + fx * 0.3, player.getEyeY() - 0.15, player.getZ() + fz * 0.3,
								(rnd.nextDouble() - 0.5) * 0.08, 0.02, (rnd.nextDouble() - 0.5) * 0.08);
					}
				}
				case "potion" -> {
					if (tick % 4 == 0) {
						mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, player.getX() + fx * 0.3, player.getEyeY(), player.getZ() + fz * 0.3, 0, 0.05, 0);
					}
				}
				case "diamonds" -> {
					if (tick % 3 == 0) {
						mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, hx + fx * 0.2 + (rnd.nextDouble() - 0.5) * 0.3, hy + 0.1 + rnd.nextDouble() * 0.3,
								hz + fz * 0.2 + (rnd.nextDouble() - 0.5) * 0.3, 0, 0, 0);
					}
				}
				case "heart" -> {
					if (tick % 8 == 0) {
						mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.HEART, player.getX(), player.getY() + 2.3, player.getZ(), 0, 0.05, 0);
					}
				}
				case "dance", "ghast_dance" -> {
					if (tick % 10 == 0) {
						mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, player.getX() + (rnd.nextDouble() - 0.5), player.getY() + 2.2,
								player.getZ() + (rnd.nextDouble() - 0.5), rnd.nextInt(24) / 24.0, 0, 0);
					}
				}
				case "cheer" -> {
					if (tick % 6 == 0) {
						mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK, player.getX() + (rnd.nextDouble() - 0.5) * 0.6, player.getY() + 2.4,
								player.getZ() + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.08, 0);
					}
				}
				case "creeper_scare" -> {
					if (t < 0.15f && tick % 2 == 0) {
						for (int i = 0; i < 6; i++) {
							mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.POOF, player.getX() + fx * 1.2 + (rnd.nextDouble() - 0.5), player.getY() + 0.8,
									player.getZ() + fz * 1.2 + (rnd.nextDouble() - 0.5), 0, 0.05, 0);
						}
					}
				}
				default -> {
				}
			}
		}
	}

	private static float[] sample(Playing p) {
		return Emotes.sample(p.id(), (System.nanoTime() - p.startNanos()) / 1e9f);
	}
}
