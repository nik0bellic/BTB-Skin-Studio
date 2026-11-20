package de.behindtheblocks.skinstudio.pack;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.util.FormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Installed packs on disk ({@code <game dir>/btb_skinstudio/}):
 * <pre>
 * packs/    installed *.btbcosmeticpack files (or unpacked folders with pack.json)
 * custom/   cosmetics made in the editor or imported, one folder each (editable with Blockbench)
 * exports/  files written by "Exportieren"
 * cache/    cosmetics received from other players in multiplayer (by content hash)
 * import/   drop folder: files placed here are offered for import
 * </pre>
 * All methods do blocking IO and must run off the render thread.
 */
public final class PackStore {
	public static final String PACK_EXT = ".btbcosmeticpack";
	public static final String COSMETIC_EXT = ".btbcosmetic";
	public static final String CUSTOM_PACK_ID = "custom";
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Packs");

	public final Path root;
	public final Path packs;
	public final Path custom;
	public final Path exports;
	public final Path cache;
	public final Path importDir;

	public PackStore(Path root) {
		this.root = root;
		this.packs = root.resolve("packs");
		this.custom = root.resolve("custom");
		this.exports = root.resolve("exports");
		this.cache = root.resolve("cache");
		this.importDir = root.resolve("import");
	}

	public void ensureDirectories() throws IOException {
		for (Path p : new Path[]{packs, custom, exports, cache, importDir}) {
			Files.createDirectories(p);
		}
	}

	/** Reads every installed pack. Broken packs are returned with problems instead of being dropped. */
	public List<LoadedPack> loadInstalled() {
		List<LoadedPack> out = new ArrayList<>();
		if (!Files.isDirectory(packs)) {
			return out;
		}
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(packs)) {
			List<Path> sorted = new ArrayList<>();
			ds.forEach(sorted::add);
			sorted.sort(Comparator.comparing(p -> p.getFileName().toString()));
			for (Path p : sorted) {
				String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
				if (!(Files.isDirectory(p) || name.endsWith(PACK_EXT) || name.endsWith(".zip"))) {
					continue;
				}
				LoadedPack pack;
				try {
					pack = PackReader.readPack(ArchiveFiles.read(p));
				} catch (IOException | FormatException e) {
					pack = new LoadedPack(null);
					pack.usable = false;
					pack.problems.add(PackProblem.error(p.getFileName().toString(), e.getMessage()));
				}
				pack.source = p;
				pack.origin = LoadedPack.Origin.INSTALLED;
				log(pack);
				out.add(pack);
			}
		} catch (IOException e) {
			LOG.error("Pack-Ordner {} nicht lesbar", packs, e);
		}
		return out;
	}

	/** The user's own cosmetics as one virtual pack. */
	public LoadedPack loadCustom() {
		PackManifest m = new PackManifest();
		m.id = CUSTOM_PACK_ID;
		m.names.put("de", "Meine Cosmetics");
		m.names.put("en", "My Cosmetics");
		LoadedPack pack = new LoadedPack(m);
		pack.origin = LoadedPack.Origin.CUSTOM;
		pack.source = custom;
		if (!Files.isDirectory(custom)) {
			return pack;
		}
		try (Stream<Path> list = Files.list(custom)) {
			for (Path p : list.sorted().toList()) {
				String name = p.getFileName().toString();
				if (!Files.isDirectory(p) && !name.toLowerCase(java.util.Locale.ROOT).endsWith(COSMETIC_EXT)) {
					continue;
				}
				try {
					CosmeticAsset a = PackReader.readSingle(ArchiveFiles.read(p), CUSTOM_PACK_ID, pack.problems);
					if (pack.cosmetics.stream().anyMatch(x -> x.id().equals(a.id()))) {
						pack.problems.add(PackProblem.error(name, Tr.t("btb.t.doppelte_id") + a.id() + Tr.t("btb.t.uebersprungen_2")));
						continue;
					}
					pack.cosmetics.add(a);
				} catch (IOException | FormatException e) {
					pack.problems.add(PackProblem.error(name, e.getMessage()));
				}
			}
		} catch (IOException e) {
			LOG.error("Ordner {} nicht lesbar", custom, e);
		}
		log(pack);
		return pack;
	}

	private static void log(LoadedPack pack) {
		if (pack.problems.isEmpty()) {
			LOG.info("Pack '{}' geladen: {} Cosmetics", pack.id(), pack.cosmetics.size());
			return;
		}
		LOG.warn("Pack '{}' geladen: {} Cosmetics, {} Fehler, {} Warnungen", pack.id(), pack.cosmetics.size(), pack.errorCount(), pack.warningCount());
		for (PackProblem p : pack.problems) {
			LOG.warn("  [{}] {}", p.severity(), p);
		}
	}

	/** Result of {@link #checkInstall}. */
	public record InstallCheck(boolean allowed, boolean isUpdate, String installedVersion, List<PackProblem> problems) {
	}

	/**
	 * Decides whether {@code incoming} may be installed next to the packs in {@code idToPack}
	 * (cosmetic id → id of the pack that currently provides it).
	 */
	public static InstallCheck checkInstall(LoadedPack incoming, Map<String, String> idToPack, Map<String, String> packVersions) {
		List<PackProblem> problems = new ArrayList<>();
		if (!incoming.usable || incoming.manifest == null) {
			problems.add(PackProblem.error("", Tr.t("btb.t.pack_ist_beschaedigt_und_kann_nicht_inst")));
			return new InstallCheck(false, false, null, problems);
		}
		String packId = incoming.manifest.id;
		if (packId.equals(CUSTOM_PACK_ID) || packId.startsWith("btb_builtin")) {
			problems.add(PackProblem.error("", Tr.t("btb.t.pack_id") + packId + Tr.t("btb.t.ist_reserviert")));
			return new InstallCheck(false, false, null, problems);
		}
		if (incoming.cosmetics.isEmpty()) {
			problems.add(PackProblem.error("", Tr.t("btb.t.pack_enthaelt_keine_verwendbaren_cosmeti")));
			return new InstallCheck(false, false, null, problems);
		}
		for (CosmeticAsset a : incoming.cosmetics) {
			String owner = idToPack.get(a.id());
			if (owner != null && !owner.equals(packId)) {
				problems.add(PackProblem.error(a.id(), Tr.t("btb.t.id_ist_bereits_durch_pack") + owner + Tr.t("btb.t.belegt_2")));
			}
		}
		String installed = packVersions.get(packId);
		boolean update = installed != null;
		if (update && PackManifest.compareVersions(incoming.manifest.version, installed) < 0) {
			problems.add(PackProblem.warning("", Tr.t("btb.t.installierte_version") + installed + Tr.t("btb.t.ist_neuer_als") + incoming.manifest.version + Tr.t("btb.t.downgrade")));
		}
		boolean ok = problems.stream().noneMatch(p -> p.severity() == PackProblem.Severity.ERROR);
		return new InstallCheck(ok, update, installed, problems);
	}

	/** Writes the pack into {@code packs/}, replacing an older version with the same id. */
	public Path install(LoadedPack pack, ArchiveFiles files, Path previousSource) throws IOException {
		ensureDirectories();
		Path target = packs.resolve(pack.manifest.id + PACK_EXT);
		Path tmp = packs.resolve(pack.manifest.id + PACK_EXT + ".tmp");
		Files.write(tmp, files.toZip());
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		if (previousSource != null && !previousSource.equals(target) && Files.exists(previousSource)) {
			deleteRecursively(previousSource);
		}
		LOG.info("Pack '{}' {} installiert", pack.manifest.id, pack.manifest.version);
		return target;
	}

	public void uninstall(LoadedPack pack) throws IOException {
		if (pack.origin != LoadedPack.Origin.INSTALLED || pack.source == null) {
			throw new FormatException(Tr.t("btb.t.nur_installierte_packs_koennen_deinstall"));
		}
		deleteRecursively(pack.source);
		LOG.info("Pack '{}' deinstalliert", pack.id());
	}

	/** Saves an own cosmetic as folder {@code custom/<id>/}, replacing an older state. */
	public Path saveCustom(CosmeticAsset asset) throws IOException {
		ensureDirectories();
		Path dir = custom.resolve(PackWriter.folderName(asset.id()));
		Path tmp = custom.resolve(PackWriter.folderName(asset.id()) + ".tmp");
		deleteRecursively(tmp);
		Files.createDirectories(tmp);
		for (String p : asset.files.paths()) {
			Path out = tmp.resolve(p).normalize();
			if (!out.startsWith(tmp)) {
				throw new FormatException(Tr.t("btb.t.ungueltiger_pfad_2") + p);
			}
			Files.createDirectories(out.getParent());
			Files.write(out, asset.files.get(p));
		}
		deleteRecursively(dir);
		Files.move(tmp, dir, StandardCopyOption.ATOMIC_MOVE);
		return dir;
	}

	public void deleteCustom(String id) throws IOException {
		deleteRecursively(custom.resolve(PackWriter.folderName(id)));
		Files.deleteIfExists(custom.resolve(PackWriter.folderName(id) + COSMETIC_EXT));
	}

	public Path export(String fileName, byte[] data) throws IOException {
		ensureDirectories();
		String safe = fileName.replaceAll("[^A-Za-z0-9_.-]", "_");
		Path out = exports.resolve(safe);
		Files.write(out, data);
		return out;
	}

	public static void deleteRecursively(Path p) throws IOException {
		if (p == null || !Files.exists(p)) {
			return;
		}
		if (Files.isDirectory(p)) {
			try (Stream<Path> walk = Files.walk(p)) {
				for (Path x : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(x);
				}
			}
		} else {
			Files.delete(p);
		}
	}
}
