package de.behindtheblocks.skinstudio.client.skin;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
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
		return CompletableFuture.supplyAsync(this::loadAll, CosmeticLibrary.IO).thenAcceptAsync(map -> {
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

	private Map<String, SkinData> loadAll() {
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
		problems.forEach(pr -> LOG.warn("Skins: {}", pr));
		return map;
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
