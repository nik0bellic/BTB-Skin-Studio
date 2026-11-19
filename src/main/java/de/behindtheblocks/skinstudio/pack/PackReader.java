package de.behindtheblocks.skinstudio.pack;

import de.behindtheblocks.skinstudio.anim.AnimationIO;
import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticJson;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Reads and validates {@code .btbcosmeticpack} and {@code .btbcosmetic} contents. Never throws for broken content. */
public final class PackReader {
	public static final String PACK_JSON = "pack.json";
	public static final String COSMETIC_JSON = "cosmetic.json";
	public static final String COSMETICS_DIR = "cosmetics/";

	public enum Kind {
		BTB_PACK, BTB_COSMETIC, BEDROCK, UNKNOWN
	}

	private PackReader() {
	}

	public static Kind detect(ArchiveFiles raw) {
		ArchiveFiles f = raw.unwrapSingleFolder();
		if (f.has(PACK_JSON)) {
			return Kind.BTB_PACK;
		}
		if (f.has(COSMETIC_JSON)) {
			return Kind.BTB_COSMETIC;
		}
		for (String p : f.paths()) {
			String l = p.toLowerCase(Locale.ROOT);
			if (l.endsWith("manifest.json") || l.endsWith("skins.json") || l.endsWith(".meta.json") || l.endsWith(".geo.json")
					|| l.endsWith("geometry.json") || l.endsWith(".png") || l.endsWith("contents.json")) {
				return Kind.BEDROCK;
			}
		}
		return Kind.UNKNOWN;
	}

	public static LoadedPack readPack(ArchiveFiles raw) {
		ArchiveFiles f = raw.unwrapSingleFolder();
		byte[] json = f.get(PACK_JSON);
		if (json == null) {
			LoadedPack broken = new LoadedPack(null);
			broken.usable = false;
			broken.problems.add(PackProblem.error(PACK_JSON, "fehlt – das ist kein BTB-Cosmetic-Pack"));
			return broken;
		}
		PackManifest manifest;
		try {
			manifest = PackManifest.read(Json.parseObject(json));
		} catch (FormatException e) {
			LoadedPack broken = new LoadedPack(null);
			broken.usable = false;
			broken.problems.add(PackProblem.error(PACK_JSON, e.getMessage()));
			return broken;
		}
		LoadedPack pack = new LoadedPack(manifest);
		if (manifest.icon != null) {
			pack.icon = f.get(manifest.icon);
		} else if (f.has("pack.png")) {
			pack.icon = f.get("pack.png");
		}
		Set<String> folders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		if (!manifest.cosmetics.isEmpty()) {
			for (String c : manifest.cosmetics) {
				folders.add(c.endsWith("/") ? c.substring(0, c.length() - 1) : c);
			}
		} else {
			for (String p : f.paths()) {
				if (p.regionMatches(true, 0, COSMETICS_DIR, 0, COSMETICS_DIR.length()) && p.toLowerCase(Locale.ROOT).endsWith("/" + COSMETIC_JSON)) {
					folders.add(p.substring(0, p.length() - COSMETIC_JSON.length() - 1));
				}
			}
		}
		if (folders.isEmpty()) {
			pack.problems.add(PackProblem.warning("", "Pack enthält keine Cosmetics"));
		}
		Set<String> ids = new HashSet<>();
		for (String folder : folders) {
			ArchiveFiles sub = f.sub(folder);
			try {
				CosmeticAsset asset = readCosmetic(sub, manifest.id, folder, pack.problems);
				if (!ids.add(asset.id())) {
					pack.problems.add(PackProblem.error(folder, "doppelte ID '" + asset.id() + "' im selben Pack – übersprungen"));
					continue;
				}
				pack.cosmetics.add(asset);
			} catch (FormatException e) {
				pack.problems.add(PackProblem.error(folder, e.getMessage()));
			}
		}
		return pack;
	}

	/** Reads a single cosmetic folder/archive ({@code .btbcosmetic}). */
	public static CosmeticAsset readSingle(ArchiveFiles raw, String packId, List<PackProblem> problems) {
		ArchiveFiles f = raw.unwrapSingleFolder();
		if (!f.has(COSMETIC_JSON)) {
			throw new FormatException("cosmetic.json fehlt – das ist kein BTB-Cosmetic");
		}
		return readCosmetic(f, packId, "", problems);
	}

	public static CosmeticAsset readCosmetic(ArchiveFiles f, String packId, String where, List<PackProblem> problems) {
		byte[] json = f.get(COSMETIC_JSON);
		if (json == null) {
			throw new FormatException("cosmetic.json fehlt");
		}
		CosmeticDefinition def = CosmeticJson.read(Json.parseObject(json));
		String at = where.isEmpty() ? def.id : where;

		byte[] modelBytes = f.get(def.model);
		if (modelBytes == null) {
			throw new FormatException("Modelldatei '" + def.model + "' fehlt");
		}
		GeometryIO.Result geo;
		try {
			geo = GeometryIO.read(modelBytes);
		} catch (FormatException e) {
			throw new FormatException(def.model + ": " + e.getMessage());
		}
		geo.warnings().forEach(w -> problems.add(PackProblem.warning(at, w)));
		GeoModel model = geo.find(def.geometry);
		if (model == null) {
			throw new FormatException("Geometrie '" + def.geometry + "' nicht in " + def.model + " gefunden");
		}
		if (model.cubeCount() == 0) {
			throw new FormatException("Modell '" + model.identifier + "' enthält keine Würfel");
		}

		byte[] tex = f.get(def.texture);
		if (tex == null) {
			throw new FormatException("Textur '" + def.texture + "' fehlt");
		}
		Png.Info info = Png.info(tex, def.texture);
		if (info.width() * model.textureHeight != info.height() * model.textureWidth) {
			problems.add(PackProblem.warning(at, "Textur " + info.width() + "×" + info.height() + " passt nicht zum Seitenverhältnis des Modells ("
					+ model.textureWidth + "×" + model.textureHeight + ") – UVs können verschoben sein"));
		}
		for (String optional : new String[]{def.emissiveTexture, def.tintMask, def.preview}) {
			if (optional != null) {
				byte[] b = f.get(optional);
				if (b == null) {
					throw new FormatException("Datei '" + optional + "' fehlt");
				}
				Png.info(b, optional);
			}
		}

		Map<String, CosmeticAnimation> animations = new LinkedHashMap<>();
		if (def.animationFile != null) {
			byte[] anim = f.get(def.animationFile);
			if (anim == null) {
				throw new FormatException("Animationsdatei '" + def.animationFile + "' fehlt");
			}
			try {
				AnimationIO.Result r = AnimationIO.read(anim);
				r.warnings().forEach(w -> problems.add(PackProblem.warning(at, w)));
				animations.putAll(r.animations());
			} catch (FormatException e) {
				problems.add(PackProblem.warning(at, def.animationFile + ": " + e.getMessage() + " – ohne Animation geladen"));
			}
			for (String name : def.animations) {
				if (!animations.containsKey(name)) {
					problems.add(PackProblem.warning(at, "Animation '" + name + "' nicht gefunden"));
				}
			}
		}
		if (def.particles != null && !def.particles.type.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+|[a-z0-9_/.-]+")) {
			problems.add(PackProblem.warning(at, "Partikeltyp '" + def.particles.type + "' ungültig – Partikel deaktiviert"));
			def.particles = null;
		}
		return new CosmeticAsset(def, model, animations, f, packId);
	}
}
