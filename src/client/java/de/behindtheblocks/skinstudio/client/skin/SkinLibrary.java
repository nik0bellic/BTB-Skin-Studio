package de.behindtheblocks.skinstudio.client.skin;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.bedrock.BedrockLocator;
import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.Tr;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * All skins the client can wear: Minecraft's default skins, own skins ({@code btb_skinstudio/skins/}),
 * Bedrock skin packs ({@code btb_skinstudio/skin_packs/} and {@code <game dir>/skin_packs/}, the folder
 * the Bedrock Skins mod uses) and skins received from other players. Loading runs on the IO thread.
 */
public final class SkinLibrary {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Skins");
	private static final String[] DEFAULT_NAMES = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
	private static SkinLibrary instance;

	public final Path skinsDir;
	public final Path skinPacksDir;
	public final Path sharedSkinPacksDir;
	private volatile Map<String, SkinData> byId = Map.of();
	private volatile List<String> groups = List.of();
	private final List<PackProblem> problems = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, SkinData> remote = new ConcurrentHashMap<>();
	private volatile int revision;

	private SkinLibrary() {
		Path root = FabricLoader.getInstance().getGameDir().resolve(BtbSkinStudio.MOD_ID);
		skinsDir = root.resolve("skins");
		skinPacksDir = root.resolve("skin_packs");
		sharedSkinPacksDir = FabricLoader.getInstance().getGameDir().resolve("skin_packs");
	}

	public static SkinLibrary get() {
		if (instance == null) {
			instance = new SkinLibrary();
		}
		return instance;
	}

	public int revision() {
		return revision;
	}

	public SkinData find(String id) {
		return id == null ? null : byId.get(id);
	}

	/** Local skin by id, else a received one by hash. */
	public SkinData resolve(String id, String hash) {
		SkinData s = find(id);
		if (s != null) {
			return s;
		}
		if (hash != null && !hash.isEmpty()) {
			SkinData r = remote.get(hash);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	public List<SkinData> all() {
		return new ArrayList<>(byId.values());
	}

	/** Group names in display order: defaults, own skins, then one per Bedrock pack. */
	public List<String> groups() {
		return groups;
	}

	public List<PackProblem> problems() {
		return problems;
	}

	public static String defaultsGroup() {
		return Tr.t("btb.skins.group.default");
	}

	public static String ownGroup() {
		return Tr.t("btb.skins.group.own");
	}

	public CompletableFuture<Void> reload() {
		// resource packs are read on the calling (client) thread, everything else on the IO thread
		Map<String, ArchiveFiles> fromResourcePacks = resourcePackSkinPacks();
		return CompletableFuture.supplyAsync(() -> loadAll(fromResourcePacks), CosmeticLibrary.IO).thenAcceptAsync(map -> {
			byId = Collections.unmodifiableMap(map);
			List<String> g = new ArrayList<>();
			for (SkinData s : map.values()) {
				String group = groupOf(s);
				if (!g.contains(group)) {
					g.add(group);
				}
			}
			groups = List.copyOf(g);
			revision++;
			LOG.info("{} Skins in {} Gruppen bereit", map.size(), g.size());
		}, Minecraft.getInstance());
	}

	public static String groupOf(SkinData s) {
		return switch (s.source) {
			case VANILLA -> defaultsGroup();
			case CUSTOM -> ownGroup();
			default -> s.group.isEmpty() ? "Bedrock" : s.group;
		};
	}

	/**
	 * Skin packs inside resource packs, laid out like in the Bedrock Skins mod:
	 * {@code assets/<namespace>/skin_packs/<pack>/skins.json} plus textures, geometry and {@code texts/*.lang}.
	 */
	private static Map<String, ArchiveFiles> resourcePackSkinPacks() {
		Map<String, ArchiveFiles> out = new LinkedHashMap<>();
		var rm = Minecraft.getInstance().getResourceManager();
		if (rm == null) {
			return out; // very early start: the resource reload listener loads them a moment later
		}
		try {
			for (var e : rm.listResources("skin_packs", id -> true).entrySet()) {
				String path = e.getKey().getPath(); // skin_packs/<pack>/<file...>
				String[] parts = path.split("/", 3);
				if (parts.length < 3) {
					continue;
				}
				String key = e.getKey().getNamespace() + ":" + parts[1];
				try (var in = e.getValue().open()) {
					out.computeIfAbsent(key, k -> ArchiveFiles.empty()).put(parts[2], in.readAllBytes());
				} catch (IOException | RuntimeException ex) {
					LOG.warn("Skin-Pack-Datei {} nicht lesbar: {}", e.getKey(), ex.getMessage());
				}
			}
		} catch (RuntimeException e) {
			LOG.warn("Resource-Pack-Skins nicht lesbar: {}", e.getMessage());
		}
		out.values().removeIf(f -> !f.has("skins.json"));
		// outfit skins name a default skin of the game as their base; its texture is read from the game here
		for (ArchiveFiles f : out.values()) {
			if (!f.has("btb_bases.json")) {
				continue;
			}
			try {
				var bases = de.behindtheblocks.skinstudio.util.Json.parseObject(f.get("btb_bases.json"));
				for (var b : bases.entrySet()) {
					String vanilla = b.getValue().getAsString();
					if (!vanilla.startsWith("vanilla:")) {
						continue;
					}
					String rest = vanilla.substring("vanilla:".length());
					boolean slim = rest.endsWith("_slim");
					String name = rest.replaceAll("_(slim|wide)$", "");
					var id = net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/player/" + (slim ? "slim" : "wide") + "/" + name + ".png");
					try (var in = rm.getResourceOrThrow(id).open()) {
						f.put("__base/" + b.getKey() + (slim ? ".slim" : ".wide") + ".png", in.readAllBytes());
					}
				}
			} catch (Exception e) {
				LOG.warn("Basis-Skins nicht lesbar: {}", e.getMessage());
			}
		}
		return out;
	}

	private Map<String, SkinData> loadAll(Map<String, ArchiveFiles> fromResourcePacks) {
		problems.clear();
		Map<String, SkinData> map = new LinkedHashMap<>();
		for (String name : DEFAULT_NAMES) {
			for (boolean slim : new boolean[]{false, true}) {
				String id = "vanilla:" + name + "_" + (slim ? "slim" : "wide");
				Map<String, String> names = Map.of("en", Character.toUpperCase(name.charAt(0)) + name.substring(1) + (slim ? " (slim)" : ""));
				map.put(id, new SkinData(id, names, slim, null, null, SkinData.Source.VANILLA, "", null));
			}
		}
		try {
			Files.createDirectories(skinsDir);
			Files.createDirectories(skinPacksDir);
		} catch (IOException e) {
			LOG.warn("Skin-Ordner nicht anlegbar: {}", e.getMessage());
		}
		for (Path p : list(skinsDir)) {
			try {
				ArchiveFiles f = ArchiveFiles.read(p);
				SkinData s = SkinIO.read(f, SkinData.Source.CUSTOM, "");
				map.putIfAbsent(s.id, s);
			} catch (Exception e) {
				problems.add(PackProblem.error(p.getFileName().toString(), e.getMessage()));
			}
		}
		for (Path dir : new Path[]{skinPacksDir, sharedSkinPacksDir}) {
			for (Path p : list(dir)) {
				try {
					ArchiveFiles f = ArchiveFiles.read(p);
					for (SkinData s : SkinIO.readBedrockPack(f, p.getFileName().toString(), problems)) {
						map.putIfAbsent(s.id, s);
					}
				} catch (Exception e) {
					problems.add(PackProblem.error(p.getFileName().toString(), e.getMessage()));
				}
			}
		}
		for (var e : fromResourcePacks.entrySet()) {
			try {
				for (SkinData s : SkinIO.readBedrockPack(e.getValue(), e.getKey(), problems)) {
					map.putIfAbsent(s.id, withBase(s, e.getValue()));
				}
			} catch (Exception ex) {
				problems.add(PackProblem.error(e.getKey(), ex.getMessage()));
			}
		}
		if (ClientSettings.get().bedrockBuiltinPacks) {
			int found = 0;
			for (Path dir : BedrockLocator.builtInSkinPackDirs()) {
				for (Path p : BedrockLocator.skinPackFolders(dir)) {
					// the "vanilla" pack only repeats the default skins that Java already has
					if (p.getFileName().toString().equalsIgnoreCase("vanilla")) {
						continue;
					}
					try {
						List<PackProblem> ignored = new ArrayList<>(); // encrypted Marketplace packs are skipped silently
						for (SkinData s : SkinIO.readBedrockPack(ArchiveFiles.readDirectory(p), p.getFileName().toString(), ignored)) {
							if (map.putIfAbsent(s.id, s) == null) {
								found++;
							}
						}
					} catch (Exception ex) {
						LOG.debug("Bedrock-Pack {} übersprungen: {}", p, ex.getMessage());
					}
				}
			}
			if (found > 0) {
				LOG.info("{} Skins aus der Bedrock-Installation geladen", found);
			}
			// the player's own Bedrock skins: imported custom skins and own skin packs in the user folders
			int own = 0;
			String group = Tr.t("btb.skins.group.bedrock_own");
			for (Path root : BedrockLocator.installations()) {
				for (BedrockLocator.Found f : BedrockLocator.scan(root)) {
					try {
						if (f.kind() == BedrockLocator.Kind.SKIN_PNG) {
							byte[] png = java.nio.file.Files.readAllBytes(f.path());
							SkinData base = SkinIO.fromPng(png, f.path().getFileName().toString(), null);
							String id = "bedrock_own:" + Integer.toHexString(f.path().toString().hashCode());
							SkinData s = new SkinData(id, base.names, base.slim, base.texture, null, SkinData.Source.BEDROCK_PACK, group, base.files);
							if (map.putIfAbsent(s.id, s) == null) {
								own++;
							}
						} else if (f.kind() == BedrockLocator.Kind.SKIN_PACK && !f.encrypted()) {
							for (SkinData s : SkinIO.readBedrockPack(ArchiveFiles.readDirectory(f.path()), f.path().getFileName().toString(), new ArrayList<>())) {
								if (map.putIfAbsent(s.id, s) == null) {
									own++;
								}
							}
						}
					} catch (Exception ex) {
						LOG.debug("Eigener Bedrock-Skin {} übersprungen: {}", f.path(), ex.getMessage());
					}
				}
			}
			if (own > 0) {
				LOG.info("{} eigene Bedrock-Skins gefunden", own);
			}
		}
		problems.forEach(pr -> LOG.warn("Skins: {}", pr));
		return map;
	}

	/** Outfit skin composed onto its default base skin, if the pack names one. */
	private static SkinData withBase(SkinData s, ArchiveFiles files) {
		String key = s.id.substring(s.id.lastIndexOf('/') + 1);
		for (String variant : new String[]{".wide", ".slim"}) {
			byte[] base = files.get("__base/" + key + variant + ".png");
			if (base != null) {
				try {
					return SkinIO.withTexture(s, SkinIO.overlay(base, s.texture), variant.equals(".slim"));
				} catch (RuntimeException e) {
					LOG.warn("Outfit {} nicht kombinierbar: {}", s.id, e.getMessage());
				}
			}
		}
		return s;
	}

	private static List<Path> list(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(dir)) {
			return s.filter(p -> Files.isDirectory(p) || p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(zip|mcpack|btbskin)$"))
					.sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** Saves an own skin as folder {@code skins/<id>/} (IO thread). */
	public void saveCustom(SkinData s) throws IOException {
		Path dir = skinsDir.resolve(folder(s.id));
		PackStore.deleteRecursively(dir);
		Files.createDirectories(dir);
		for (String p : s.files.paths()) {
			Files.write(dir.resolve(p), s.files.get(p));
		}
	}

	public void deleteCustom(String id) throws IOException {
		PackStore.deleteRecursively(skinsDir.resolve(folder(id)));
	}

	public static String folder(String id) {
		return id.replace(':', '_').replace('/', '_');
	}

	/** A skin archive received from another player (validated). */
	public SkinData acceptRemote(String hash, ArchiveFiles files) {
		SkinData s = SkinIO.read(files, SkinData.Source.REMOTE, "");
		if (!s.hash.equals(hash)) {
			return null;
		}
		remote.put(hash, s);
		revision++;
		return s;
	}
}
