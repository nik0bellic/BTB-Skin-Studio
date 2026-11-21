package de.behindtheblocks.skinstudio.server;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticJson;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.CosmeticChunk;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.Hello;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.PlayerLoadout;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.RemovePlayer;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.RequestCosmetic;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.SetLoadout;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads.SyncLoadouts;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Server side of the cosmetic sync. The server never renders anything; it only
 * <ul>
 *   <li>validates and relays loadouts (ids + colors + hashes),</li>
 *   <li>brokers on-demand transfers of cosmetics other clients do not have, with an in-memory cache,
 *       so every archive crosses the network at most once per server session and client cache.</li>
 * </ul>
 * Vanilla clients and clients without the mod are simply skipped.
 */
public final class ServerCosmeticSync {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Sync");
	private static final Pattern HASH = Pattern.compile("[0-9a-f]{32}");
	private static final long CACHE_LIMIT_BYTES = 32L * 1024 * 1024;
	private static final long OWNER_ASK_COOLDOWN_MS = 30_000;
	private static final int MAX_LOADOUT_UPDATES_PER_10S = 20;

	private final Map<UUID, List<Loadout.Entry>> loadouts = new HashMap<>();
	private final Map<UUID, SkinStudioPayloads.SkinInfo> skins = new HashMap<>();
	private static final Pattern SKIN_ID = Pattern.compile("[a-z0-9_.-]{1,48}:[a-z0-9_./-]{1,128}");
	private final Set<UUID> modded = new HashSet<>();
	private final Map<UUID, long[]> rate = new HashMap<>();
	/** content hash → validated .btbcosmetic archive (LRU). */
	private final LinkedHashMap<String, byte[]> blobs = new LinkedHashMap<>(16, 0.75f, true);
	private long blobBytes;
	private final Map<String, Set<UUID>> waiting = new HashMap<>();
	private final Map<String, Long> askedOwner = new HashMap<>();
	private final Map<String, SkinStudioPayloads.Assembly> uploads = new HashMap<>();
	private boolean transferAllowed = true;

	private static ServerCosmeticSync instance;

	public static ServerCosmeticSync get() {
		return instance;
	}

	public void register() {
		instance = this;
		CinematicsCompat.registerCommand(this);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, Hello.TYPE)) {
				ServerPlayNetworking.send(handler.player, new Hello(SkinStudioPayloads.PROTOCOL, BtbSkinStudio.version(), transferAllowed));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(handler.player, server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());

		ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE, (payload, ctx) -> onHello(ctx.player(), payload, ctx.server()));
		ServerPlayNetworking.registerGlobalReceiver(SetLoadout.TYPE, (payload, ctx) -> onSetLoadout(ctx.player(), payload, ctx.server()));
		ServerPlayNetworking.registerGlobalReceiver(SkinStudioPayloads.SetSkin.TYPE, (payload, ctx) -> onSetSkin(ctx.player(), payload.skin(), ctx.server()));
		ServerPlayNetworking.registerGlobalReceiver(SkinStudioPayloads.SetBody.TYPE, (payload, ctx) -> onBody(ctx.player(), payload.body(), ctx.server()));
		ServerPlayNetworking.registerGlobalReceiver(SkinStudioPayloads.PlayEmote.TYPE, (payload, ctx) -> onEmote(ctx.player(), payload.id(), payload.data(), ctx.server()));
		ServerPlayNetworking.registerGlobalReceiver(RequestCosmetic.TYPE, (payload, ctx) -> onRequest(ctx.player(), payload.hash(), ctx.server()));
		ServerPlayNetworking.registerGlobalReceiver(CosmeticChunk.TYPE, (payload, ctx) -> onUploadChunk(ctx.player(), payload, ctx.server()));
	}

	private void reset() {
		loadouts.clear();
		skins.clear();
		modded.clear();
		rate.clear();
		blobs.clear();
		blobBytes = 0;
		waiting.clear();
		askedOwner.clear();
		uploads.clear();
	}

	private void onHello(ServerPlayer player, Hello hello, MinecraftServer server) {
		if (hello.protocol() != SkinStudioPayloads.PROTOCOL) {
			LOG.warn("{} nutzt BTB Skin Studio {} (Protokoll {}), Server erwartet Protokoll {} – Cosmetics für diesen Spieler deaktiviert",
					player.getName().getString(), hello.modVersion(), hello.protocol(), SkinStudioPayloads.PROTOCOL);
			return;
		}
		modded.add(player.getUUID());
		List<PlayerLoadout> all = new ArrayList<>();
		loadouts.forEach((id, entries) -> all.add(new PlayerLoadout(id, entries)));
		if (!all.isEmpty()) {
			ServerPlayNetworking.send(player, new SyncLoadouts(all));
		}
		List<SkinStudioPayloads.PlayerSkinInfo> allSkins = new ArrayList<>();
		skins.forEach((id, info) -> allSkins.add(new SkinStudioPayloads.PlayerSkinInfo(id, info)));
		List<SkinStudioPayloads.PlayerBody> allBodies = new ArrayList<>();
		bodies.forEach((id, b) -> allBodies.add(new SkinStudioPayloads.PlayerBody(id, b)));
		if (!allBodies.isEmpty()) {
			ServerPlayNetworking.send(player, new SkinStudioPayloads.SyncBodies(allBodies));
		}
		if (!allSkins.isEmpty()) {
			ServerPlayNetworking.send(player, new SkinStudioPayloads.SyncSkins(allSkins));
		}
	}

	private void onLeave(ServerPlayer player, MinecraftServer server) {
		UUID id = player.getUUID();
		modded.remove(id);
		lastEmote.remove(id);
		bodies.remove(id);
		rate.remove(id);
		uploads.keySet().removeIf(k -> k.startsWith(id + "/"));
		waiting.values().forEach(s -> s.remove(id));
		boolean had = loadouts.remove(id) != null;
		had |= skins.remove(id) != null;
		if (had) {
			broadcast(server, new RemovePlayer(id), null);
		}
	}

	/** Validates ids, colors and hashes; drops anything malformed instead of trusting the client. */
	static List<Loadout.Entry> sanitize(List<Loadout.Entry> in) {
		List<Loadout.Entry> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Loadout.Entry e : in) {
			if (out.size() >= Loadout.MAX_ENTRIES) {
				break;
			}
			if (!CosmeticJson.isValidId(e.id()) || !seen.add(e.id())) {
				continue;
			}
			String hash = HASH.matcher(e.hash()).matches() ? e.hash() : "";
			out.add(new Loadout.Entry(e.id(), e.color(), hash, e.adjust()));
		}
		return out;
	}

	private boolean rateLimited(UUID id) {
		long now = System.currentTimeMillis();
		long[] r = rate.computeIfAbsent(id, k -> new long[]{now, 0});
		if (now - r[0] > 10_000) {
			r[0] = now;
			r[1] = 0;
		}
		return ++r[1] > MAX_LOADOUT_UPDATES_PER_10S;
	}

	private void onSetLoadout(ServerPlayer player, SetLoadout payload, MinecraftServer server) {
		if (!modded.contains(player.getUUID()) || rateLimited(player.getUUID())) {
			return;
		}
		List<Loadout.Entry> clean = sanitize(payload.entries());
		if (clean.equals(loadouts.get(player.getUUID()))) {
			return;
		}
		loadouts.put(player.getUUID(), clean);
		broadcast(server, new SyncLoadouts(List.of(new PlayerLoadout(player.getUUID(), clean))), null);
	}

	private void onSetSkin(ServerPlayer player, SkinStudioPayloads.SkinInfo info, MinecraftServer server) {
		if (!modded.contains(player.getUUID()) || rateLimited(player.getUUID())) {
			return;
		}
		SkinStudioPayloads.SkinInfo clean;
		if (info.id().isEmpty()) {
			clean = new SkinStudioPayloads.SkinInfo("", "", false);
		} else if (SKIN_ID.matcher(info.id()).matches()) {
			clean = new SkinStudioPayloads.SkinInfo(info.id(), HASH.matcher(info.hash()).matches() ? info.hash() : "", info.slim());
		} else {
			return;
		}
		if (clean.equals(skins.get(player.getUUID()))) {
			return;
		}
		if (clean.id().isEmpty()) {
			skins.remove(player.getUUID());
		} else {
			skins.put(player.getUUID(), clean);
		}
		broadcast(server, new SkinStudioPayloads.SyncSkins(List.of(new SkinStudioPayloads.PlayerSkinInfo(player.getUUID(), clean))), null);
	}

	private final Map<UUID, Long> lastEmote = new HashMap<>();
	private final Map<UUID, SkinStudioPayloads.Body> bodies = new HashMap<>();

	private void onBody(ServerPlayer player, SkinStudioPayloads.Body body, MinecraftServer server) {
		if (!modded.contains(player.getUUID()) || rateLimited(player.getUUID())) {
			return;
		}
		SkinStudioPayloads.Body clean = body.clamp();
		if (clean.equals(bodies.getOrDefault(player.getUUID(), SkinStudioPayloads.Body.NORMAL))) {
			return;
		}
		if (clean.isNormal()) {
			bodies.remove(player.getUUID());
		} else {
			bodies.put(player.getUUID(), clean);
		}
		broadcast(server, new SkinStudioPayloads.SyncBodies(List.of(new SkinStudioPayloads.PlayerBody(player.getUUID(), clean))), null);
	}

	private void onEmote(ServerPlayer player, String id, String data, MinecraftServer server) {
		if (!modded.contains(player.getUUID())) {
			return;
		}
		String json = "";
		if (de.behindtheblocks.skinstudio.emote.CustomEmote.isNetworkId(id)) {
			// an emote from the editor: the data must match its id (the id is a hash of the data)
			try {
				var e = de.behindtheblocks.skinstudio.emote.CustomEmote.parse(data);
				json = e.toJson();
				if (!json.equals(data) || !de.behindtheblocks.skinstudio.emote.CustomEmote.idOf(json).equals(id)) {
					return;
				}
				de.behindtheblocks.skinstudio.emote.Emotes.registerCustom(id, e);
				CustomEmoteStore.save(server, id, json);
			} catch (RuntimeException e) {
				return;
			}
		} else if (!id.isEmpty() && !de.behindtheblocks.skinstudio.emote.Emotes.isBuiltIn(id)) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastEmote.get(player.getUUID());
		if (last != null && now - last < 250) {
			return;
		}
		lastEmote.put(player.getUUID(), now);
		broadcast(server, new SkinStudioPayloads.EmotePlayed(player.getUUID(), id, json), player.getUUID());
		CinematicsCompat.logEmote(player.getUUID(), id);
	}

	/** Plays an emote on the replay puppets of {@code profile} for everyone with the mod (command, see {@link CinematicsCompat}). */
	public void puppetEmote(MinecraftServer server, UUID profile, String id) {
		String data = "";
		if (de.behindtheblocks.skinstudio.emote.CustomEmote.isNetworkId(id)) {
			String json = CustomEmoteStore.load(server, id);
			if (json == null) {
				return;
			}
			data = json;
		}
		broadcast(server, new SkinStudioPayloads.PuppetEmote(profile, id, data), null);
	}

	private void broadcast(MinecraftServer server, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload, UUID except) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (modded.contains(p.getUUID()) && !p.getUUID().equals(except)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	// ------------------------------------------------------------------ transfer broker

	private void onRequest(ServerPlayer requester, String hash, MinecraftServer server) {
		if (!transferAllowed || !modded.contains(requester.getUUID()) || !HASH.matcher(hash).matches()) {
			return;
		}
		byte[] blob = blobs.get(hash);
		if (blob != null) {
			send(requester, hash, blob);
			return;
		}
		ServerPlayer owner = findOwner(hash, server);
		if (owner == null || owner == requester) {
			return;
		}
		Set<UUID> w = waiting.computeIfAbsent(hash, k -> new HashSet<>());
		if (w.size() > 256) {
			return;
		}
		w.add(requester.getUUID());
		long now = System.currentTimeMillis();
		Long asked = askedOwner.get(hash);
		if (asked == null || now - asked > OWNER_ASK_COOLDOWN_MS) {
			askedOwner.put(hash, now);
			ServerPlayNetworking.send(owner, new RequestCosmetic(hash));
		}
	}

	private boolean owns(UUID player, String hash) {
		SkinStudioPayloads.SkinInfo sk = skins.get(player);
		return sk != null && sk.hash().equals(hash)
				|| loadouts.getOrDefault(player, List.of()).stream().anyMatch(e -> e.hash().equals(hash));
	}

	private ServerPlayer findOwner(String hash, MinecraftServer server) {
		for (Map.Entry<UUID, SkinStudioPayloads.SkinInfo> e : skins.entrySet()) {
			if (e.getValue().hash().equals(hash)) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					return p;
				}
			}
		}
		for (Map.Entry<UUID, List<Loadout.Entry>> e : loadouts.entrySet()) {
			for (Loadout.Entry entry : e.getValue()) {
				if (entry.hash().equals(hash)) {
					ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
					if (p != null) {
						return p;
					}
				}
			}
		}
		return null;
	}

	private void onUploadChunk(ServerPlayer owner, CosmeticChunk chunk, MinecraftServer server) {
		String hash = chunk.hash();
		if (!transferAllowed || !HASH.matcher(hash).matches() || !askedOwner.containsKey(hash) || blobs.containsKey(hash)) {
			return;
		}
		boolean ownsIt = owns(owner.getUUID(), hash);
		if (!ownsIt) {
			return;
		}
		String key = owner.getUUID() + "/" + hash;
		byte[] complete;
		try {
			SkinStudioPayloads.Assembly a = uploads.computeIfAbsent(key, k -> new SkinStudioPayloads.Assembly(chunk.total()));
			complete = a.add(chunk);
		} catch (IllegalArgumentException e) {
			uploads.remove(key);
			LOG.warn("Ungültiger Upload von {}: {}", owner.getName().getString(), e.getMessage());
			return;
		}
		if (complete == null) {
			return;
		}
		uploads.remove(key);
		String who = owner.getName().getString();
		CompletableFuture.supplyAsync(() -> verify(complete, hash)).thenAcceptAsync(ok -> {
			if (!ok) {
				LOG.warn("Upload von {} verworfen: Inhalt ungültig oder Hash stimmt nicht", who);
				waiting.remove(hash);
				return;
			}
			store(hash, complete);
			Set<UUID> targets = waiting.remove(hash);
			if (targets != null) {
				for (UUID t : targets) {
					ServerPlayer p = server.getPlayerList().getPlayer(t);
					if (p != null) {
						send(p, hash, complete);
					}
				}
			}
		}, server);
	}

	/** Parses the archive like a client would and checks that the content hash matches. */
	static boolean verify(byte[] zip, String hash) {
		try {
			ArchiveFiles files = ArchiveFiles.readZip(zip);
			if (de.behindtheblocks.skinstudio.skin.SkinIO.isSkinArchive(files)) {
				return de.behindtheblocks.skinstudio.skin.SkinIO.read(files, de.behindtheblocks.skinstudio.skin.SkinData.Source.REMOTE, "").hash.equals(hash);
			}
			CosmeticAsset a = PackReader.readSingle(files, "remote", new ArrayList<>());
			return a.contentHash.equals(hash);
		} catch (Exception e) {
			return false;
		}
	}

	private void store(String hash, byte[] data) {
		blobs.put(hash, data);
		blobBytes += data.length;
		var it = blobs.entrySet().iterator();
		while (blobBytes > CACHE_LIMIT_BYTES && it.hasNext()) {
			blobBytes -= it.next().getValue().length;
			it.remove();
		}
	}

	private static void send(ServerPlayer p, String hash, byte[] data) {
		for (CosmeticChunk c : SkinStudioPayloads.split(hash, data)) {
			ServerPlayNetworking.send(p, c);
		}
	}
}
