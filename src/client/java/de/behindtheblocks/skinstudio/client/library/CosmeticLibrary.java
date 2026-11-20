package de.behindtheblocks.skinstudio.client.library;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackReader;
import de.behindtheblocks.skinstudio.pack.PackStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Every cosmetic the client knows: built-in packs, installed packs, own cosmetics and cosmetics
 * received from other players. Loading happens on a background thread; the finished snapshot is
 * swapped in on the client thread, so renderers always see a consistent state.
 */
public final class CosmeticLibrary {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Library");
	public static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BTB Skin Studio IO");
		t.setDaemon(true);
		return t;
	});
	private static final String[] BUILTIN_PACKS = {"btb_starter"};

	private static CosmeticLibrary instance;

	public final PackStore store;
	private volatile Map<String, CosmeticAsset> byId = Map.of();
	private volatile List<LoadedPack> packs = List.of();
	/** Cosmetics received over the network, by content hash. */
	private final Map<String, CosmeticAsset> remote = new ConcurrentHashMap<>();
	private final List<Consumer<CosmeticLibrary>> listeners = new ArrayList<>();
	private volatile int revision;
	private volatile boolean loading;

	private CosmeticLibrary(PackStore store) {
		this.store = store;
	}

	public static CosmeticLibrary get() {
		if (instance == null) {
			instance = new CosmeticLibrary(new PackStore(FabricLoader.getInstance().getGameDir().resolve(BtbSkinStudio.MOD_ID)));
		}
		return instance;
	}

	public int revision() {
		return revision;
	}

	public boolean isLoading() {
		return loading;
	}

	public CosmeticAsset find(String id) {
		return byId.get(id);
	}

	public CosmeticDefinition definition(String id) {
		CosmeticAsset a = byId.get(id);
		return a == null ? null : a.definition;
	}

	/** Local cosmetic by id, else a received one by hash, else null (unknown cosmetics are skipped, never crash). */
	public CosmeticAsset resolve(String id, String hash) {
		CosmeticAsset a = byId.get(id);
		if (a != null) {
			return a;
		}
		if (hash != null && !hash.isEmpty()) {
			CosmeticAsset r = remote.get(hash);
			if (r != null && r.id().equals(id)) {
				return r;
			}
		}
		return null;
	}

	public boolean hasRemote(String hash) {
		return remote.containsKey(hash);
	}

	public Collection<CosmeticAsset> all() {
		return byId.values();
	}

	public List<LoadedPack> packs() {
		return packs;
	}

	public void addListener(Consumer<CosmeticLibrary> l) {
		listeners.add(l);
	}

	/** Reloads everything in the background. */
	public CompletableFuture<Void> reload() {
		loading = true;
		return CompletableFuture.supplyAsync(this::loadAll, IO).thenAcceptAsync(result -> {
			packs = Collections.unmodifiableList(result.packs);
			byId = Collections.unmodifiableMap(result.byId);
			revision++;
			loading = false;
			LOG.info("{} Cosmetics aus {} Packs bereit", byId.size(), packs.size());
			for (Consumer<CosmeticLibrary> l : listeners) {
				l.accept(this);
			}
		}, Minecraft.getInstance()).exceptionally(t -> {
			loading = false;
			LOG.error("Cosmetics konnten nicht geladen werden", t);
			return null;
		});
	}

	private record Snapshot(List<LoadedPack> packs, Map<String, CosmeticAsset> byId) {
	}

	private Snapshot loadAll() {
		try {
			store.ensureDirectories();
		} catch (IOException e) {
			LOG.error("Ordner {} kann nicht angelegt werden", store.root, e);
		}
		List<LoadedPack> list = new ArrayList<>();
		for (String name : BUILTIN_PACKS) {
			LoadedPack p = loadBuiltin(name);
			if (p != null) {
				list.add(p);
			}
		}
		LoadedPack persona = bedrockCharacterCreator();
		if (persona != null) {
			list.add(persona);
		}
		list.addAll(store.loadInstalled());
		list.add(store.loadCustom());
		Map<String, CosmeticAsset> ids = new LinkedHashMap<>();
		Map<String, String> owner = new HashMap<>();
		for (LoadedPack p : list) {
			List<CosmeticAsset> kept = new ArrayList<>();
			for (CosmeticAsset a : p.cosmetics) {
				String existing = owner.get(a.id());
				if (existing != null) {
					p.problems.add(PackProblem.error(a.id(), Tr.t("btb.t.id_bereits_durch_pack") + existing + Tr.t("btb.t.belegt_nicht_geladen")));
					LOG.warn("Doppelte Cosmetic-ID {} in Pack {} (bereits in {})", a.id(), p.id(), existing);
					continue;
				}
				owner.put(a.id(), p.id());
				ids.put(a.id(), a);
				kept.add(a);
			}
			p.cosmetics.retainAll(kept);
		}
		return new Snapshot(list, ids);
	}

	/**
	 * Character Creator pieces (shirts, overalls, shoes …) that ship unencrypted with an installed Bedrock
	 * edition, read-only from the game files, shown as one pack. Nothing is copied.
	 */
	private LoadedPack bedrockCharacterCreator() {
		if (!de.behindtheblocks.skinstudio.client.data.ClientSettings.get().bedrockBuiltinPacks) {
			return null;
		}
		List<CosmeticAsset> found = new ArrayList<>();
		for (java.nio.file.Path dir : de.behindtheblocks.skinstudio.bedrock.BedrockLocator.builtInSkinPackDirs()) {
			for (java.nio.file.Path p : de.behindtheblocks.skinstudio.bedrock.BedrockLocator.personaFolders(dir)) {
				try {
					var r = de.behindtheblocks.skinstudio.bedrock.BedrockImporter.importFiles(ArchiveFiles.readDirectory(p), p.getFileName().toString());
					if (!r.protectedContent) {
						found.addAll(r.cosmetics);
					}
				} catch (Exception e) {
					LOG.debug("Character-Creator-Teile in {} übersprungen: {}", p, e.getMessage());
				}
			}
		}
		if (found.isEmpty()) {
			return null;
		}
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("format", 1);
		o.addProperty("id", "bedrock_character_creator");
		o.addProperty("version", "1.0.0");
		com.google.gson.JsonObject n = new com.google.gson.JsonObject();
		n.addProperty("de", "Bedrock Charakter-Editor");
		n.addProperty("en", "Bedrock Character Creator");
		o.add("name", n);
		LoadedPack p = new LoadedPack(de.behindtheblocks.skinstudio.pack.PackManifest.read(o));
		p.origin = LoadedPack.Origin.BUILTIN;
		p.cosmetics.addAll(found);
		LOG.info("{} Character-Creator-Teile aus der Bedrock-Installation geladen", found.size());
		return p;
	}

	private LoadedPack loadBuiltin(String name) {
		Path root = FabricLoader.getInstance().getModContainer(BtbSkinStudio.MOD_ID)
				.flatMap(c -> c.findPath("assets/" + BtbSkinStudio.MOD_ID + "/builtin/" + name)).orElse(null);
		if (root == null) {
			LOG.error("Eingebautes Pack {} fehlt im Mod-JAR", name);
			return null;
		}
		try {
			LoadedPack p = PackReader.readPack(ArchiveFiles.readDirectory(root));
			p.origin = LoadedPack.Origin.BUILTIN;
			if (!p.problems.isEmpty()) {
				p.problems.forEach(x -> LOG.warn("Eingebautes Pack {}: {}", name, x));
			}
			return p;
		} catch (IOException e) {
			LOG.error("Eingebautes Pack {} nicht lesbar", name, e);
			return null;
		}
	}

	// ----------------------------------------------------------------- network cache

	/** Loads a received cosmetic from the disk cache (background thread). */
	public CompletableFuture<Boolean> loadCached(String hash) {
		return CompletableFuture.supplyAsync(() -> {
			Path skinFile = store.cache.resolve(hash + ".btbskin");
			if (Files.isRegularFile(skinFile)) {
				try {
					return de.behindtheblocks.skinstudio.client.skin.SkinLibrary.get().acceptRemote(hash, ArchiveFiles.read(skinFile)) != null;
				} catch (Exception e) {
					return false;
				}
			}
			Path file = store.cache.resolve(hash + PackStore.COSMETIC_EXT);
			if (!Files.isRegularFile(file)) {
				return false;
			}
			try {
				CosmeticAsset a = PackReader.readSingle(ArchiveFiles.read(file), "remote", new ArrayList<>());
				if (!a.contentHash.equals(hash)) {
					Files.deleteIfExists(file);
					return false;
				}
				remote.put(hash, a);
				return true;
			} catch (Exception e) {
				LOG.warn("Zwischengespeichertes Cosmetic {} ist defekt und wird gelöscht", hash);
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// best effort
				}
				return false;
			}
		}, IO);
	}

	/** Stores a received archive after validating it (background thread). */
	public CompletableFuture<CosmeticAsset> acceptReceived(String hash, byte[] zip) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				ArchiveFiles files = ArchiveFiles.readZip(zip);
				if (de.behindtheblocks.skinstudio.skin.SkinIO.isSkinArchive(files)) {
					var skin = de.behindtheblocks.skinstudio.client.skin.SkinLibrary.get().acceptRemote(hash, files);
					if (skin != null) {
						store.ensureDirectories();
						Files.write(store.cache.resolve(hash + ".btbskin"), zip);
						LOG.info("Skin {} von einem Mitspieler empfangen", skin.id);
					}
					return null;
				}
				CosmeticAsset a = PackReader.readSingle(ArchiveFiles.readZip(zip), "remote", new ArrayList<>());
				if (!a.contentHash.equals(hash)) {
					LOG.warn("Empfangenes Cosmetic hat falschen Hash – verworfen");
					return null;
				}
				store.ensureDirectories();
				Files.write(store.cache.resolve(hash + PackStore.COSMETIC_EXT), zip);
				remote.put(hash, a);
				return a;
			} catch (Exception e) {
				LOG.warn("Empfangenes Cosmetic ist ungültig: {}", e.getMessage());
				return null;
			}
		}, IO);
	}

	/** Lists cached hashes once at startup so known remote cosmetics need no transfer. */
	public void preloadCache() {
		IO.execute(() -> {
			if (!Files.isDirectory(store.cache)) {
				return;
			}
			try (Stream<Path> s = Files.list(store.cache)) {
				s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(PackStore.COSMETIC_EXT) || n.endsWith(".btbskin"))
						.map(n -> n.substring(0, n.lastIndexOf('.')))
						.forEach(this::loadCached);
			} catch (IOException e) {
				LOG.warn("Cache nicht lesbar: {}", e.getMessage());
			}
		});
	}

	public static String language() {
		try {
			return Minecraft.getInstance().getLanguageManager().getSelected();
		} catch (RuntimeException e) {
			return "de_de";
		}
	}
}
