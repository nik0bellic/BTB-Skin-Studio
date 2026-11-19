package de.behindtheblocks.skinstudio.client.net;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.CosmeticChunk;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.Hello;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.RemovePlayer;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.RequestCosmetic;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.SetLoadout;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.SyncLoadouts;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client side of the cosmetic sync: sends the own loadout, keeps everyone else's,
 * fetches unknown cosmetics once (disk cache first) and answers upload requests.
 * Without the mod on the server, cosmetics are simply local-only.
 */
public final class ClientCosmeticSync {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Sync");

	private static boolean serverHasMod;
	private static boolean transferAllowed;
	private static final Map<UUID, List<Loadout.Entry>> REMOTE = new HashMap<>();
	private static final Set<String> REQUESTED = new HashSet<>();
	private static final Map<String, SkinStudioPayloads.Assembly> INCOMING = new HashMap<>();
	private static boolean dirty;
	private static int cooldown;
	private static int revision;
	private static int wardrobeRevision;

	private record Cached(int libRev, int rev, int wardRev, boolean showOthers, List<ActiveCosmetic> list) {
	}

	private static final Map<UUID, Cached> RESOLVED = new HashMap<>();

	private ClientCosmeticSync() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			serverHasMod = false;
			REMOTE.clear();
			INCOMING.clear();
			REQUESTED.clear();
			revision++;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			serverHasMod = false;
			REMOTE.clear();
			INCOMING.clear();
			revision++;
		});
		ClientPlayNetworking.registerGlobalReceiver(Hello.TYPE, (hello, ctx) -> {
			if (hello.protocol() != SkinStudioPayloads.PROTOCOL) {
				LOG.warn("Server nutzt BTB Skin Studio {} (Protokoll {}), dieser Client Protokoll {} – Sync deaktiviert, Cosmetics nur lokal sichtbar",
						hello.modVersion(), hello.protocol(), SkinStudioPayloads.PROTOCOL);
				return;
			}
			serverHasMod = true;
			transferAllowed = hello.transferAllowed();
			ClientPlayNetworking.send(new Hello(SkinStudioPayloads.PROTOCOL, BtbSkinStudio.version(), false));
			dirty = true;
			cooldown = 0;
		});
		ClientPlayNetworking.registerGlobalReceiver(SyncLoadouts.TYPE, (sync, ctx) -> {
			for (SkinStudioPayloads.PlayerLoadout pl : sync.loadouts()) {
				REMOTE.put(pl.player(), pl.entries());
			}
			revision++;
		});
		ClientPlayNetworking.registerGlobalReceiver(RemovePlayer.TYPE, (rm, ctx) -> {
			REMOTE.remove(rm.player());
			RESOLVED.remove(rm.player());
			revision++;
		});
		ClientPlayNetworking.registerGlobalReceiver(RequestCosmetic.TYPE, (req, ctx) -> uploadOwn(req.hash()));
		ClientPlayNetworking.registerGlobalReceiver(CosmeticChunk.TYPE, (chunk, ctx) -> onChunk(chunk));

		Wardrobe.get().addListener(() -> {
			dirty = true;
			wardrobeRevision++;
		});
		CosmeticLibrary.get().addListener(lib -> dirty = true);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (cooldown > 0) {
				cooldown--;
			}
			if (dirty && cooldown == 0 && serverHasMod && ClientPlayNetworking.canSend(SetLoadout.TYPE)) {
				dirty = false;
				cooldown = 10;
				ClientPlayNetworking.send(new SetLoadout(withHashes(Wardrobe.get().equipped())));
			}
		});
	}

	public static boolean serverHasMod() {
		return serverHasMod;
	}

	private static List<Loadout.Entry> withHashes(Loadout l) {
		List<Loadout.Entry> out = new ArrayList<>();
		for (Loadout.Entry e : l.entries()) {
			CosmeticAsset a = CosmeticLibrary.get().find(e.id());
			out.add(new Loadout.Entry(e.id(), e.color(), a == null ? "" : a.contentHash));
		}
		return out;
	}

	/** Cosmetics to draw for a player; unknown ones are skipped (and fetched once if possible). */
	public static List<ActiveCosmetic> activeFor(UUID player) {
		Minecraft mc = Minecraft.getInstance();
		boolean self = mc.player != null && mc.player.getUUID().equals(player)
				|| mc.getUser() != null && player.equals(mc.getUser().getProfileId());
		ClientSettings s = ClientSettings.get();
		int libRev = CosmeticLibrary.get().revision();
		Cached c = RESOLVED.get(player);
		if (c != null && c.libRev == libRev && c.rev == revision && c.wardRev == wardrobeRevision && c.showOthers == s.showOtherPlayers) {
			return c.list;
		}
		List<Loadout.Entry> entries;
		if (self) {
			entries = Wardrobe.get().equipped().entries();
		} else if (!s.showOtherPlayers) {
			entries = List.of();
		} else {
			entries = REMOTE.getOrDefault(player, List.of());
		}
		List<ActiveCosmetic> list = resolve(entries, !self);
		RESOLVED.put(player, new Cached(libRev, revision, wardrobeRevision, s.showOtherPlayers, list));
		return list;
	}

	public static List<ActiveCosmetic> resolve(List<Loadout.Entry> entries, boolean fetchMissing) {
		List<ActiveCosmetic> list = new ArrayList<>(entries.size());
		for (Loadout.Entry e : entries) {
			CosmeticAsset a = CosmeticLibrary.get().resolve(e.id(), e.hash());
			if (a != null) {
				list.add(ActiveCosmetic.of(a, e.color()));
			} else if (fetchMissing) {
				requestMissing(e.hash());
			}
		}
		return List.copyOf(list);
	}

	private static void requestMissing(String hash) {
		if (hash.isEmpty() || !REQUESTED.add(hash)) {
			return;
		}
		CosmeticLibrary.get().loadCached(hash).thenAcceptAsync(found -> {
			if (found) {
				revision++;
			} else if (serverHasMod && transferAllowed && ClientSettings.get().downloadOthers && ClientPlayNetworking.canSend(RequestCosmetic.TYPE)) {
				ClientPlayNetworking.send(new RequestCosmetic(hash));
			}
		}, Minecraft.getInstance());
	}

	private static void onChunk(CosmeticChunk chunk) {
		if (!REQUESTED.contains(chunk.hash())) {
			return;
		}
		byte[] done;
		try {
			done = INCOMING.computeIfAbsent(chunk.hash(), h -> new SkinStudioPayloads.Assembly(chunk.total())).add(chunk);
		} catch (IllegalArgumentException e) {
			INCOMING.remove(chunk.hash());
			LOG.warn("Fehlerhafte Übertragung verworfen: {}", e.getMessage());
			return;
		}
		if (done == null) {
			return;
		}
		INCOMING.remove(chunk.hash());
		CosmeticLibrary.get().acceptReceived(chunk.hash(), done).thenAcceptAsync(a -> {
			if (a != null) {
				LOG.info("Cosmetic {} von einem Mitspieler empfangen ({} KB)", a.id(), done.length / 1024);
				revision++;
			}
		}, Minecraft.getInstance());
	}

	/** Server asks us for one of our equipped cosmetics. */
	private static void uploadOwn(String hash) {
		if (!ClientSettings.get().shareOwnCosmetics) {
			return;
		}
		CosmeticAsset own = null;
		for (Loadout.Entry e : Wardrobe.get().equipped().entries()) {
			CosmeticAsset a = CosmeticLibrary.get().find(e.id());
			if (a != null && a.contentHash.equals(hash)) {
				own = a;
			}
		}
		if (own == null) {
			return;
		}
		CosmeticAsset asset = own;
		CompletableFuture.supplyAsync(() -> {
			try {
				return asset.files.toZip();
			} catch (Exception e) {
				return null;
			}
		}, CosmeticLibrary.IO).thenAcceptAsync(zip -> {
			if (zip == null || zip.length > SkinStudioPayloads.MAX_TRANSFER_BYTES) {
				LOG.warn("Cosmetic {} ist zu groß zum Teilen ({} KB, max. 512 KB)", asset.id(), zip == null ? -1 : zip.length / 1024);
				return;
			}
			for (CosmeticChunk c : SkinStudioPayloads.split(hash, zip)) {
				ClientPlayNetworking.send(c);
			}
		}, Minecraft.getInstance());
	}
}
