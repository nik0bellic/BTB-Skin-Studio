package de.behindtheblocks.skinstudio.client.importer;

import de.behindtheblocks.skinstudio.bedrock.BedrockImporter;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackReader;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.pack.PackWriter;
import de.behindtheblocks.skinstudio.util.FormatException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What would happen if one dropped/selected file were imported. Built off-thread, shown in the
 * import preview, executed only after confirmation. Nothing is written before {@link #execute}.
 */
public final class ImportPlan {
	public enum Kind {
		BTB_PACK("BTB-Cosmetic-Pack"), BTB_COSMETIC("BTB-Cosmetic"), BEDROCK("Bedrock-Ressourcen"), INVALID("Nicht importierbar");

		public final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	public final Path source;
	public Kind kind = Kind.INVALID;
	public String title;
	public final List<CosmeticAsset> cosmetics = new ArrayList<>();
	public final List<PackProblem> problems = new ArrayList<>();
	public boolean allowed;
	public String actionLabel = "Importieren";
	public String summary = "";
	LoadedPack pack;
	ArchiveFiles packFiles;
	Path previousSource;

	private ImportPlan(Path source) {
		this.source = source;
		this.title = source.getFileName() == null ? source.toString() : source.getFileName().toString();
	}

	/** Reads and validates (blocking; call on the IO thread). */
	public static ImportPlan analyze(Path path) {
		ImportPlan plan = new ImportPlan(path);
		CosmeticLibrary lib = CosmeticLibrary.get();
		ArchiveFiles files;
		try {
			files = ArchiveFiles.read(path);
		} catch (Exception e) {
			plan.problems.add(PackProblem.error("", "Datei kann nicht gelesen werden: " + e.getMessage()));
			return plan;
		}
		Map<String, String> owners = new HashMap<>();
		Map<String, String> versions = new HashMap<>();
		for (LoadedPack p : lib.packs()) {
			if (p.manifest != null) {
				versions.put(p.id(), p.manifest.version);
			}
			for (CosmeticAsset a : p.cosmetics) {
				owners.put(a.id(), p.id());
			}
		}
		switch (PackReader.detect(files)) {
			case BTB_PACK -> {
				plan.kind = Kind.BTB_PACK;
				LoadedPack pack = PackReader.readPack(files);
				plan.problems.addAll(pack.problems);
				plan.cosmetics.addAll(pack.cosmetics);
				if (pack.manifest != null) {
					plan.title = pack.manifest.name(CosmeticLibrary.language()) + "  v" + pack.manifest.version;
					for (LoadedPack p : lib.packs()) {
						if (p.id().equals(pack.id()) && p.origin == LoadedPack.Origin.INSTALLED) {
							plan.previousSource = p.source;
						}
					}
				}
				PackStore.InstallCheck check = PackStore.checkInstall(pack, owners, versions);
				plan.problems.addAll(check.problems());
				plan.allowed = check.allowed();
				plan.actionLabel = check.isUpdate() ? "Aktualisieren" : "Installieren";
				plan.summary = check.isUpdate() ? "Update von " + check.installedVersion() + " auf " + (pack.manifest == null ? "?" : pack.manifest.version)
						: pack.cosmetics.size() + " Cosmetics";
				plan.pack = pack;
				plan.packFiles = files;
			}
			case BTB_COSMETIC -> {
				plan.kind = Kind.BTB_COSMETIC;
				try {
					CosmeticAsset a = PackReader.readSingle(files, PackStore.CUSTOM_PACK_ID, plan.problems);
					plan.title = a.definition.name(CosmeticLibrary.language());
					plan.cosmetics.add(renameIfTaken(a, owners, plan.problems));
					plan.allowed = true;
					plan.actionLabel = "Zu meinen Cosmetics";
					plan.summary = a.id();
				} catch (FormatException e) {
					plan.problems.add(PackProblem.error("", e.getMessage()));
				}
			}
			case BEDROCK, UNKNOWN -> {
				plan.kind = Kind.BEDROCK;
				try {
					BedrockImporter.Result r = BedrockImporter.importFiles(files, stripExtension(plan.title));
					plan.problems.addAll(r.problems);
					for (CosmeticAsset a : r.cosmetics) {
						plan.cosmetics.add(renameIfTaken(a, owners, plan.problems));
					}
					plan.allowed = !plan.cosmetics.isEmpty();
					plan.actionLabel = "Zu meinen Cosmetics";
					plan.summary = r.protectedContent ? "geschützt" : plan.cosmetics.size() + " Cosmetics erkannt";
				} catch (FormatException e) {
					plan.problems.add(PackProblem.error("", e.getMessage()));
				}
			}
		}
		if (plan.kind == Kind.BEDROCK && plan.cosmetics.isEmpty()) {
			plan.kind = Kind.INVALID;
		}
		return plan;
	}

	/** Gives imported own cosmetics a free id instead of clashing with an installed one. */
	private static CosmeticAsset renameIfTaken(CosmeticAsset a, Map<String, String> owners, List<PackProblem> problems) {
		String owner = owners.get(a.id());
		if (owner == null || owner.equals(PackStore.CUSTOM_PACK_ID)) {
			if (owner != null) {
				problems.add(PackProblem.info(a.id(), "existiert bereits in „Meine Cosmetics“ und wird ersetzt"));
			}
			return a;
		}
		Set<String> taken = new HashSet<>(owners.keySet());
		String base = a.id();
		String id = base + "_import";
		int n = 2;
		while (taken.contains(id)) {
			id = base + "_import" + n++;
		}
		problems.add(PackProblem.info(a.id(), "ID ist durch Pack '" + owner + "' belegt – wird als " + id + " importiert"));
		CosmeticDefinition d = a.definition.copy();
		d.id = id;
		ArchiveFiles files = ArchiveFiles.empty();
		for (String p : a.files.paths()) {
			if (!p.equalsIgnoreCase(PackReader.COSMETIC_JSON)) {
				files.put(p, a.files.get(p));
			}
		}
		ArchiveFiles out = PackWriter.cosmeticFiles(d, a.model, a.animations, files);
		return PackReader.readSingle(out, PackStore.CUSTOM_PACK_ID, new ArrayList<>());
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/** Writes to disk (blocking; IO thread). */
	public String execute() throws Exception {
		PackStore store = CosmeticLibrary.get().store;
		if (!allowed) {
			throw new FormatException("Import nicht möglich");
		}
		if (kind == Kind.BTB_PACK) {
			store.install(pack, packFiles, previousSource);
			return "Pack „" + pack.manifest.name(CosmeticLibrary.language()) + "“ " + (actionLabel.equals("Aktualisieren") ? "aktualisiert" : "installiert");
		}
		for (CosmeticAsset a : cosmetics) {
			store.saveCustom(a);
		}
		return cosmetics.size() == 1 ? "„" + cosmetics.getFirst().definition.name(CosmeticLibrary.language()) + "“ importiert"
				: cosmetics.size() + " Cosmetics importiert";
	}
}
