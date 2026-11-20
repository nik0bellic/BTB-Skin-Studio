package de.behindtheblocks.skinstudio.bedrock;

import de.behindtheblocks.skinstudio.util.Tr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.anim.AnimationIO;
import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackReader;
import de.behindtheblocks.skinstudio.pack.PackWriter;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports locally available, unencrypted Bedrock resources as BTB cosmetics:
 * <ul>
 *   <li><b>Character Creator (Persona) pieces</b>: folders with {@code *.meta.json}, geometry sources and texture
 *       sources (PNG/TGA, tint maps → recolorable)</li>
 *   <li><b>Skin packs</b> ({@code skins.json} + {@code geometry.json}): extra bones such as hats, horns, wings or
 *       tails become wearable accessories; plain skins are reported, not converted</li>
 *   <li><b>Blockbench / plain geometry</b>: {@code *.geo.json} + PNG</li>
 * </ul>
 * Encrypted Marketplace content is detected and refused – no copy protection is bypassed.
 * The Persona texture layout mapping follows the open-source mod Bedrock Skins (MIT, © 2025 BrandonItaly).
 */
public final class BedrockImporter {
	public static final String NAMESPACE = "import";

	private static final Set<String> STANDARD_BONES = Set.of("root", "waist", "body", "jacket", "head", "hat", "helmet",
			"leftarm", "rightarm", "leftsleeve", "rightsleeve", "leftleg", "rightleg", "leftpants", "rightpants", "cape",
			"leftitem", "rightitem", "bodyarmor", "belt", "leftboot", "rightboot", "elytra", "lefthand", "righthand");

	public static final class Result {
		public final List<CosmeticAsset> cosmetics = new ArrayList<>();
		public final List<PackProblem> problems = new ArrayList<>();
		public String name;
		public boolean protectedContent;
		public int skippedSkins;
	}

	private BedrockImporter() {
	}

	public static Result importFiles(ArchiveFiles raw, String sourceName) {
		Result r = new Result();
		r.name = sourceName;
		ArchiveFiles f = raw.unwrapSingleFolder();
		if (isProtected(f, r)) {
			return r;
		}
		Map<String, String> lang = readLang(f);
		Set<String> usedGeometryFiles = new LinkedHashSet<>();
		Set<String> ids = new LinkedHashSet<>();

		// 1) Persona pieces
		for (String meta : f.find(p -> p.toLowerCase(Locale.ROOT).endsWith(".meta.json"))) {
			String dir = parent(meta);
			try {
				CosmeticAsset a = importPersona(f.sub(dir), dir.isEmpty() ? sourceName : lastSegment(dir), lang, ids, r.problems);
				if (a != null) {
					r.cosmetics.add(a);
					f.sub(dir).paths().forEach(p -> usedGeometryFiles.add((dir.isEmpty() ? "" : dir + "/") + p));
				}
			} catch (FormatException e) {
				r.problems.add(PackProblem.error(meta, e.getMessage()));
			}
		}

		// 2) Skin packs
		for (String skins : f.find(p -> p.equalsIgnoreCase("skins.json") || p.toLowerCase(Locale.ROOT).endsWith("/skins.json"))) {
			String dir = parent(skins);
			try {
				importSkinPack(f.sub(dir), lang, ids, r);
				f.sub(dir).paths().forEach(p -> usedGeometryFiles.add((dir.isEmpty() ? "" : dir + "/") + p));
			} catch (FormatException e) {
				r.problems.add(PackProblem.error(skins, e.getMessage()));
			}
		}

		// 3) Plain geometry files (Blockbench exports)
		for (String geo : f.find(p -> isGeometryFile(p) && !usedGeometryFiles.contains(p))) {
			try {
				importPlainGeometry(f, geo, ids, r);
			} catch (FormatException e) {
				r.problems.add(PackProblem.error(geo, e.getMessage()));
			}
		}

		if (r.cosmetics.isEmpty() && r.problems.stream().noneMatch(p -> p.severity() == PackProblem.Severity.ERROR)) {
			r.problems.add(PackProblem.error("", Tr.t("btb.t.keine_importierbaren_bedrock_ressourcen_")
					+ Tr.t("btb.t.skin_pack_mit_skins_json_oder_geo_json_m")));
		}
		return r;
	}

	// ------------------------------------------------------------------ protection

	public static boolean isEncrypted(ArchiveFiles f) {
		Result r = new Result();
		return isProtected(f, r);
	}

	private static boolean isProtected(ArchiveFiles f, Result r) {
		boolean encrypted = false;
		for (String p : f.paths()) {
			String l = p.toLowerCase(Locale.ROOT);
			if (l.endsWith("contents.json") || l.endsWith("skins.json") || l.endsWith("manifest.json") || l.endsWith("geometry.json")) {
				if (!looksLikeJson(f.get(p))) {
					encrypted = true;
					break;
				}
			}
		}
		if (encrypted) {
			r.protectedContent = true;
			r.problems.add(PackProblem.error("", Tr.t("btb.t.dieses_pack_ist_verschluesselt_marketpla")
					+ Tr.t("btb.t.kopierschutz_und_keine_kontoberechtigung")));
		}
		return encrypted;
	}

	private static boolean looksLikeJson(byte[] b) {
		if (b == null || b.length == 0) {
			return true;
		}
		int i = 0;
		if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
			i = 3;
		}
		while (i < b.length && Character.isWhitespace(b[i])) {
			i++;
		}
		if (i >= b.length || (b[i] != '{' && b[i] != '[')) {
			return false;
		}
		int binary = 0;
		for (int k = i; k < Math.min(b.length, i + 512); k++) {
			int c = b[k] & 0xFF;
			if (c < 9 || (c > 13 && c < 32)) {
				binary++;
			}
		}
		return binary < 4;
	}

	// ------------------------------------------------------------------ persona

	private static CosmeticAsset importPersona(ArchiveFiles dir, String folderName, Map<String, String> lang, Set<String> ids, List<PackProblem> problems) {
		String metaName = dir.find(p -> p.toLowerCase(Locale.ROOT).endsWith(".meta.json") && !p.contains("/")).stream().findFirst().orElse(null);
		if (metaName == null) {
			return null;
		}
		JsonObject meta = Json.parseObject(dir.get(metaName));
		String pieceType = Json.string(meta, "piece_type", "");
		if (!pieceType.startsWith("persona_") || pieceType.equals("persona_emote") || pieceType.equals("persona_skin")) {
			problems.add(PackProblem.info(folderName, Tr.t("btb.t.persona_typ") + pieceType + Tr.t("btb.t.ist_kein_tragbares_cosmetic_uebersprunge")));
			return null;
		}
		String pieceName = Json.string(meta, "piece_name", folderName);
		String where = Tr.t("btb.t.persona") + pieceName;

		GeoModel model = new GeoModel("geometry.import." + sanitize(pieceName), 64, 64);
		List<BufferedImage> layers = new ArrayList<>();
		List<BufferedImage> tintLayers = new ArrayList<>();
		List<int[]> layerOffsets = new ArrayList<>();
		int atlasW = 0, atlasH = 0;

		// texture_sources: clothing painted on the skin layout → outer-layer boxes
		var texSources = meta.getAsJsonArray("texture_sources");
		BufferedImage overlay = null, overlayTint = null;
		boolean overlayBody = false, overlayFace = false;
		if (texSources != null) {
			overlay = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			overlayTint = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			for (JsonElement e : texSources) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject s = e.getAsJsonObject();
				String texName = Json.string(s, "texture", null);
				BufferedImage tex = texName == null ? null : Images.read(find(dir, texName), texName);
				if (tex == null) {
					problems.add(PackProblem.warning(where, Tr.t("btb.t.textur") + texName + Tr.t("btb.t.fehlt")));
					continue;
				}
				boolean face = Json.bool(s, "use_face_uv", false);
				String tintName = Json.string(s, "tint_map", null);
				BufferedImage tint = tintName == null ? null : Images.read(find(dir, tintName), tintName);
				PersonaLayout.draw(overlay, tex, face);
				if (tint != null) {
					PersonaLayout.draw(overlayTint, tint, face);
				}
				overlayBody |= !face;
				overlayFace |= face;
			}
		}

		// geometry_sources: real 3D geometry with its own texture
		var geoSources = meta.getAsJsonArray("geometry_sources");
		Map<String, GeoModel> geometries = new HashMap<>();
		for (String p : dir.find(BedrockImporter::isGeometryFile)) {
			try {
				for (GeoModel g : GeometryIO.read(dir.get(p)).models()) {
					geometries.put(g.identifier, g);
				}
			} catch (FormatException ex) {
				problems.add(PackProblem.warning(where, p + ": " + ex.getMessage()));
			}
		}
		List<JsonObject> chosen = chooseSources(geoSources);
		List<GeoModel> parts = new ArrayList<>();
		for (JsonObject s : chosen) {
			String gid = Json.string(s, "geometry", null);
			GeoModel g = gid == null ? null : geometries.get(gid);
			if (g == null) {
				problems.add(PackProblem.warning(where, Tr.t("btb.t.geometrie") + gid + Tr.t("btb.t.nicht_gefunden")));
				continue;
			}
			String texName = Json.string(s, "texture", null);
			BufferedImage tex = texName == null ? null : Images.read(find(dir, texName), texName);
			if (tex == null) {
				problems.add(PackProblem.warning(where, Tr.t("btb.t.textur") + texName + Tr.t("btb.t.fehlt_geometrie_uebersprungen")));
				continue;
			}
			if (Json.bool(s, "animated", false)) {
				int frames = Math.max(1, (int) Json.number(s, "frames", 1));
				tex = tex.getSubimage(0, 0, tex.getWidth(), Math.max(1, tex.getHeight() / frames));
				problems.add(PackProblem.info(where, Tr.t("btb.t.animierte_textur_erstes_bild_uebernommen")));
			}
			tex = Images.resize(tex, g.textureWidth, g.textureHeight);
			String tintName = Json.string(s, "tint_map", null);
			BufferedImage tint = tintName == null ? null : Images.resize(Images.read(find(dir, tintName), tintName), g.textureWidth, g.textureHeight);
			layers.add(tex);
			tintLayers.add(tint);
			layerOffsets.add(new int[]{0, atlasH});
			atlasW = Math.max(atlasW, tex.getWidth());
			atlasH += tex.getHeight();
			parts.add(g);
		}
		if (overlay != null && (overlayBody || overlayFace)) {
			layers.add(overlay);
			tintLayers.add(overlayTint);
			layerOffsets.add(new int[]{0, atlasH});
			atlasW = Math.max(atlasW, 64);
			atlasH += 64;
		}
		if (layers.isEmpty()) {
			throw new FormatException(Tr.t("btb.t.persona_teil_enthaelt_keine_verwendbare_"));
		}

		// atlas
		BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
		BufferedImage tintAtlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
		boolean hasTint = false;
		for (int i = 0; i < layers.size(); i++) {
			atlas.getGraphics().drawImage(layers.get(i), 0, layerOffsets.get(i)[1], null);
			if (tintLayers.get(i) != null) {
				tintAtlas.getGraphics().drawImage(tintLayers.get(i), 0, layerOffsets.get(i)[1], null);
				hasTint = true;
			}
		}
		model.textureWidth = atlasW;
		model.textureHeight = atlasH;
		Map<String, GeoBone> byName = new LinkedHashMap<>();
		for (int i = 0; i < parts.size(); i++) {
			int off = layerOffsets.get(i)[1];
			for (GeoBone b : parts.get(i).bones) {
				GeoBone copy = b.copy();
				for (GeoCube c : copy.cubes) {
					shiftUv(c, 0, off);
				}
				merge(byName, copy);
			}
		}
		if (overlay != null && (overlayBody || overlayFace)) {
			int off = layerOffsets.getLast()[1];
			for (GeoBone b : PersonaLayout.overlayBones(overlayBody, overlayFace)) {
				for (GeoCube c : b.cubes) {
					shiftUv(c, 0, off);
				}
				merge(byName, b);
			}
		}
		model.bones.addAll(byName.values());
		if (model.cubeCount() == 0) {
			throw new FormatException(Tr.t("btb.t.persona_teil_ergibt_ein_leeres_modell"));
		}

		CosmeticDefinition def = new CosmeticDefinition();
		def.id = uniqueId(NAMESPACE + ":persona_" + sanitize(pieceName), ids);
		String display = lang.getOrDefault(pieceName, lang.getOrDefault("persona." + pieceName + ".title", prettify(pieceName)));
		def.names.put("de", display);
		def.names.put("en", display);
		def.author = Tr.t("btb.t.bedrock_import");
		def.type = personaType(pieceType);
		def.attachment = def.type.defaultAttachment;
		def.source = Tr.t("btb.t.bedrock_persona") + pieceType + " (" + folderName + ")";
		def.tags.add("bedrock");
		def.tags.add("persona");
		ArchiveFiles files = ArchiveFiles.empty();
		files.put("texture.png", Images.png(atlas));
		if (hasTint) {
			files.put("tint_mask.png", Images.png(tintAtlas));
			def.tintMask = "tint_mask.png";
			def.tint.enabled = true;
			def.tint.defaultColor = personaDefaultColor(meta);
			def.tint.palette.addAll(List.of(0xFF3B2414, 0xFF6B4423, 0xFFA8763E, 0xFFE3C07A, 0xFF1A1A1A, 0xFFB0B0B0, 0xFFB5402E, 0xFF4A6FB5));
		}
		ArchiveFiles out = PackWriter.cosmeticFiles(def, model, null, files);
		return PackReader.readCosmetic(out, NAMESPACE, where, problems);
	}

	private static int personaDefaultColor(JsonObject meta) {
		for (String key : new String[]{"tint_color", "tint_base_color"}) {
			if (meta.has(key) && meta.get(key).isJsonObject()) {
				for (String ch : new String[]{"r_color", "g_color", "b_color", "a_color"}) {
					String v = Json.string(meta.getAsJsonObject(key), ch, null);
					if (v != null) {
						try {
							String h = v.replace("#", "");
							if (h.length() == 8) {
								int argb = (int) Long.parseLong(h, 16);
								return argb | 0xFF000000;
							}
							if (h.length() == 6) {
								return 0xFF000000 | Integer.parseInt(h, 16);
							}
						} catch (NumberFormatException ignored) {
							// next
						}
					}
				}
			}
		}
		return 0xFFFFFFFF;
	}

	private static List<JsonObject> chooseSources(com.google.gson.JsonArray sources) {
		List<JsonObject> preferred = new ArrayList<>(), fallback = new ArrayList<>();
		if (sources == null) {
			return preferred;
		}
		for (JsonElement e : sources) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject s = e.getAsJsonObject();
			String body = Json.string(s, "body_size", null), arm = Json.string(s, "arm_size", null);
			if ((body == null || body.equals("medium")) && (arm == null || arm.equals("wide"))) {
				preferred.add(s);
			}
			fallback.add(s);
		}
		return preferred.isEmpty() ? fallback.subList(0, Math.min(1, fallback.size())) : preferred;
	}

	private static CosmeticType personaType(String pieceType) {
		return switch (pieceType) {
			case "persona_hair" -> CosmeticType.HAIRSTYLE;
			case "persona_facial_hair" -> CosmeticType.BEARD;
			case "persona_eyes", "persona_mouth" -> CosmeticType.FACE_ACCESSORY;
			case "persona_face_accessory" -> CosmeticType.GLASSES;
			case "persona_head", "persona_hood" -> CosmeticType.HAT;
			case "persona_top", "persona_body" -> CosmeticType.SHIRT;
			case "persona_outerwear" -> CosmeticType.JACKET;
			case "persona_bottom", "persona_high_pants" -> CosmeticType.PANTS;
			case "persona_feet" -> CosmeticType.SHOES;
			case "persona_back_item", "persona_capes" -> CosmeticType.BACK_DECORATION;
			case "persona_hand", "persona_left_hand", "persona_right_hand" -> CosmeticType.GLOVES;
			default -> CosmeticType.HEAD_DECORATION;
		};
	}

	// ------------------------------------------------------------------ skin packs

	private static void importSkinPack(ArchiveFiles dir, Map<String, String> lang, Set<String> ids, Result r) {
		JsonObject skins = Json.parseObject(dir.get("skins.json"));
		String packName = Json.string(skins, "localization_name", Json.string(skins, "serialize_name", "skinpack"));
		Map<String, GeoModel> geometries = new HashMap<>();
		for (String p : dir.find(BedrockImporter::isGeometryFile)) {
			try {
				for (GeoModel g : GeometryIO.read(dir.get(p)).models()) {
					geometries.put(g.identifier.toLowerCase(Locale.ROOT), g);
				}
			} catch (FormatException ex) {
				r.problems.add(PackProblem.warning(p, ex.getMessage()));
			}
		}
		var list = skins.getAsJsonArray("skins");
		if (list == null) {
			throw new FormatException(Tr.t("btb.t.skins_json_enthaelt_keine_skins"));
		}
		for (JsonElement e : list) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject s = e.getAsJsonObject();
			String locName = Json.string(s, "localization_name", "skin");
			String display = lang.getOrDefault("skin." + packName + "." + locName, prettify(locName));
			String geoId = Json.string(s, "geometry", "geometry.humanoid.custom");
			GeoModel g = geometries.get(geoId.toLowerCase(Locale.ROOT));
			if (g == null) {
				r.skippedSkins++;
				continue;
			}
			GeoModel extra = extractAccessories(g);
			if (extra == null) {
				r.skippedSkins++;
				continue;
			}
			String texName = Json.string(s, "texture", null);
			byte[] tex = texName == null ? null : find(dir, texName);
			if (tex == null) {
				r.problems.add(PackProblem.warning(display, Tr.t("btb.t.textur") + texName + Tr.t("btb.t.fehlt")));
				continue;
			}
			BufferedImage img = Images.read(tex, texName);
			CosmeticDefinition def = new CosmeticDefinition();
			def.id = uniqueId(NAMESPACE + ":" + sanitize(packName) + "_" + sanitize(locName), ids);
			def.names.put("de", display + " – Accessoire");
			def.names.put("en", display + " – Accessory");
			def.author = Tr.t("btb.t.bedrock_import");
			def.type = guessType(extra);
			def.attachment = def.type.defaultAttachment;
			def.source = Tr.t("btb.t.bedrock_skin_pack") + packName + Tr.t("btb.t.skin") + locName;
			def.tags.add("bedrock");
			def.tags.add("skinpack");
			ArchiveFiles files = ArchiveFiles.empty();
			files.put("texture.png", Images.png(img));
			try {
				ArchiveFiles out = PackWriter.cosmeticFiles(def, extra, null, files);
				r.cosmetics.add(PackReader.readCosmetic(out, NAMESPACE, display, r.problems));
			} catch (FormatException ex) {
				r.problems.add(PackProblem.error(display, ex.getMessage()));
			}
		}
		if (r.skippedSkins > 0) {
			r.problems.add(PackProblem.info(packName, r.skippedSkins + Tr.t("btb.t.skins_ohne_zusatzteile_nur_skin_textur_a")));
		}
	}

	/**
	 * Extracts the accessory parts of a skin geometry:
	 * <ul>
	 *   <li>non-standard bones (horns, tails, wings …) with their children, and</li>
	 *   <li>extra cubes inside standard bones (the usual way skin packs add hoods, horns or bags),
	 *       i.e. every cube that is not one of the base body boxes.</li>
	 * </ul>
	 * Everything is re-parented under bones named like the vanilla part it follows, so the renderer
	 * moves it with that part.
	 */
	public static GeoModel extractAccessories(GeoModel skin) {
		Map<String, GeoBone> byName = new HashMap<>();
		for (GeoBone b : skin.bones) {
			byName.put(b.name.toLowerCase(Locale.ROOT), b);
		}
		Map<String, GeoBone> wrappers = new LinkedHashMap<>();
		List<GeoBone> keep = new ArrayList<>();
		for (GeoBone b : skin.bones) {
			String lower = b.name.toLowerCase(Locale.ROOT).replace("_", "");
			if (isStandard(b.name)) {
				AttachmentPoint.Part part = AttachmentPoint.Part.fromBoneName(b.name);
				if (part == null || lower.equals("cape") || lower.contains("armor")) {
					continue;
				}
				List<GeoCube> extra = new ArrayList<>();
				for (GeoCube c : b.cubes) {
					if (!isBaseCube(part, c)) {
						extra.add(c.copy());
					}
				}
				if (extra.isEmpty()) {
					continue;
				}
				GeoBone w = wrapper(wrappers, part, byName);
				if (b.rotation[0] == 0 && b.rotation[1] == 0 && b.rotation[2] == 0) {
					w.cubes.addAll(extra);
				} else {
					GeoBone rotated = new GeoBone(b.name + "_extra");
					rotated.parent = w.name;
					rotated.pivot = b.pivot.clone();
					rotated.rotation = b.rotation.clone();
					rotated.cubes.addAll(extra);
					keep.add(rotated);
				}
				continue;
			}
			if (b.cubes.isEmpty() && childrenOf(skin, b.name).isEmpty()) {
				continue;
			}
			GeoBone copy = b.copy();
			GeoBone parent = b.parent == null ? null : byName.get(b.parent.toLowerCase(Locale.ROOT));
			if (parent == null || isStandard(parent.name)) {
				String anchor = standardAncestor(b, byName);
				AttachmentPoint.Part part = anchor == null ? AttachmentPoint.Part.BODY : AttachmentPoint.Part.fromBoneName(anchor);
				copy.parent = wrapper(wrappers, part == null ? AttachmentPoint.Part.BODY : part, byName).name;
			}
			keep.add(copy);
		}
		int cubes = keep.stream().mapToInt(x -> x.cubes.size()).sum() + wrappers.values().stream().mapToInt(x -> x.cubes.size()).sum();
		if (cubes == 0) {
			return null;
		}
		GeoModel out = new GeoModel(skin.identifier + ".accessories", skin.textureWidth, skin.textureHeight);
		out.bones.addAll(wrappers.values());
		out.bones.addAll(keep);
		return out;
	}

	private static final Map<AttachmentPoint.Part, String> PART_BONE = Map.of(
			AttachmentPoint.Part.HEAD, "head", AttachmentPoint.Part.BODY, "body", AttachmentPoint.Part.LEFT_ARM, "leftArm",
			AttachmentPoint.Part.RIGHT_ARM, "rightArm", AttachmentPoint.Part.LEFT_LEG, "leftLeg", AttachmentPoint.Part.RIGHT_LEG, "rightLeg");

	private static GeoBone wrapper(Map<String, GeoBone> wrappers, AttachmentPoint.Part part, Map<String, GeoBone> byName) {
		String name = PART_BONE.get(part);
		return wrappers.computeIfAbsent(name, k -> {
			GeoBone w = new GeoBone(name);
			GeoBone src = byName.get(name.toLowerCase(Locale.ROOT));
			w.pivot = src != null ? src.pivot.clone() : new float[]{part.pivotX, part.pivotY, part.pivotZ};
			return w;
		});
	}

	/** The plain body boxes of a skin (any inflate): head 8³, torso 8×12×4, limbs 3–4×12×4. */
	private static boolean isBaseCube(AttachmentPoint.Part part, GeoCube c) {
		float w = c.size[0], h = c.size[1], d = c.size[2];
		return switch (part) {
			case HEAD -> w == 8 && h == 8 && d == 8;
			case BODY -> w == 8 && h == 12 && d == 4;
			default -> h == 12 && (w == 4 || w == 3) && d == 4;
		};
	}

	private static List<GeoBone> childrenOf(GeoModel m, String name) {
		List<GeoBone> out = new ArrayList<>();
		for (GeoBone b : m.bones) {
			if (name.equalsIgnoreCase(b.parent)) {
				out.add(b);
			}
		}
		return out;
	}

	private static String standardAncestor(GeoBone b, Map<String, GeoBone> byName) {
		GeoBone cur = b;
		int guard = 0;
		while (cur != null && guard++ < 64) {
			if (cur != b && AttachmentPoint.Part.fromBoneName(cur.name) != null) {
				return cur.name;
			}
			cur = cur.parent == null ? null : byName.get(cur.parent.toLowerCase(Locale.ROOT));
		}
		return null;
	}

	private static float[] defaultPivot(String part) {
		AttachmentPoint.Part p = AttachmentPoint.Part.fromBoneName(part);
		return p == null ? new float[]{0, 24, 0} : new float[]{p.pivotX, p.pivotY, p.pivotZ};
	}

	private static boolean isStandard(String name) {
		String n = name.toLowerCase(Locale.ROOT).replace("_", "");
		return STANDARD_BONES.contains(n) || n.contains("armor");
	}

	private static CosmeticType guessType(GeoModel m) {
		Set<String> parents = new LinkedHashSet<>();
		for (GeoBone b : m.bones) {
			if (b.parent == null && AttachmentPoint.Part.fromBoneName(b.name) != null) {
				parents.add(b.name.toLowerCase(Locale.ROOT));
			}
		}
		if (parents.size() == 1 && parents.contains("head")) {
			return CosmeticType.HEAD_DECORATION;
		}
		if (parents.size() == 1 && parents.contains("body")) {
			return CosmeticType.BACK_DECORATION;
		}
		if (parents.contains("head")) {
			return CosmeticType.HEAD_DECORATION;
		}
		return CosmeticType.ARMOR_DECORATION;
	}

	// ------------------------------------------------------------------ plain geometry

	private static void importPlainGeometry(ArchiveFiles f, String geoPath, Set<String> ids, Result r) {
		GeometryIO.Result geo = GeometryIO.read(f.get(geoPath));
		geo.warnings().forEach(w -> r.problems.add(PackProblem.warning(geoPath, w)));
		String dir = parent(geoPath);
		String base = lastSegment(geoPath).replaceAll("(?i)\\.geo\\.json$|(?i)\\.geometry\\.json$|(?i)\\.json$", "");
		List<String> pngs = f.find(p -> parent(p).equals(dir) && (p.toLowerCase(Locale.ROOT).endsWith(".png") || p.toLowerCase(Locale.ROOT).endsWith(".tga")));
		Map<String, CosmeticAnimation> anims = new LinkedHashMap<>();
		for (String a : f.find(p -> parent(p).equals(dir) && p.toLowerCase(Locale.ROOT).contains("animation") && p.toLowerCase(Locale.ROOT).endsWith(".json"))) {
			try {
				AnimationIO.Result ar = AnimationIO.read(f.get(a));
				anims.putAll(ar.animations());
				ar.warnings().forEach(w -> r.problems.add(PackProblem.warning(a, w)));
			} catch (FormatException ex) {
				r.problems.add(PackProblem.warning(a, ex.getMessage()));
			}
		}
		for (GeoModel m : geo.models()) {
			String texPath = pickTexture(pngs, base, m.identifier);
			if (texPath == null) {
				r.problems.add(PackProblem.error(geoPath, Tr.t("btb.t.keine_png_textur_zu") + m.identifier + Tr.t("btb.t.gefunden")));
				continue;
			}
			BufferedImage img = Images.read(f.get(texPath), texPath);
			String name = prettify(m.identifier.replaceFirst("^geometry\\.", ""));
			CosmeticDefinition def = new CosmeticDefinition();
			def.id = uniqueId(NAMESPACE + ":" + sanitize(m.identifier.replaceFirst("^geometry\\.", "")), ids);
			def.names.put("de", name);
			def.names.put("en", name);
			def.author = Tr.t("btb.t.import");
			boolean bodyBound = m.bones.stream().anyMatch(b -> AttachmentPoint.Part.fromBoneName(b.name) != null);
			def.type = bodyBound ? CosmeticType.MOVING_DECORATION : CosmeticType.HEAD_DECORATION;
			def.attachment = AttachmentPoint.HEAD;
			def.source = Tr.t("btb.t.geometrie_import") + lastSegment(geoPath);
			def.tags.add("import");
			ArchiveFiles files = ArchiveFiles.empty();
			files.put("texture.png", Images.png(img));
			if (!anims.isEmpty()) {
				def.animations.addAll(anims.keySet());
			}
			ArchiveFiles out = PackWriter.cosmeticFiles(def, m, anims, files);
			r.cosmetics.add(PackReader.readCosmetic(out, NAMESPACE, name, r.problems));
		}
	}

	private static String pickTexture(List<String> pngs, String base, String identifier) {
		if (pngs.isEmpty()) {
			return null;
		}
		String id = identifier.replaceFirst("^geometry\\.", "").toLowerCase(Locale.ROOT);
		for (String p : pngs) {
			String n = lastSegment(p).toLowerCase(Locale.ROOT).replaceAll("\\.(png|tga)$", "");
			if (n.equals(base.toLowerCase(Locale.ROOT)) || n.equals(id)) {
				return p;
			}
		}
		return pngs.getFirst();
	}

	// ------------------------------------------------------------------ helpers

	public static boolean isGeometryFile(String p) {
		String l = p.toLowerCase(Locale.ROOT);
		return l.endsWith(".geo.json") || l.endsWith(".geometry.json") || l.endsWith("geometry.json") || (l.contains("/models/") && l.endsWith(".json"));
	}

	private static void shiftUv(GeoCube c, int du, int dv) {
		if (c.faces == null) {
			c.u += du;
			c.v += dv;
		} else {
			c.faces.replaceAll((k, f) -> new GeoCube.FaceUv(f.u() + du, f.v() + dv, f.w(), f.h()));
		}
	}

	private static void merge(Map<String, GeoBone> byName, GeoBone b) {
		String key = b.name.toLowerCase(Locale.ROOT);
		GeoBone existing = byName.get(key);
		if (existing == null) {
			byName.put(key, b);
		} else {
			existing.cubes.addAll(b.cubes);
		}
	}

	public static byte[] find(ArchiveFiles dir, String name) {
		for (String candidate : new String[]{name, name + ".png", name + ".tga"}) {
			byte[] b = dir.get(candidate);
			if (b != null) {
				return b;
			}
		}
		String base = lastSegment(name);
		for (String p : dir.paths()) {
			String l = lastSegment(p);
			if (l.equalsIgnoreCase(base) || l.equalsIgnoreCase(base + ".png") || l.equalsIgnoreCase(base + ".tga")) {
				return dir.get(p);
			}
		}
		return null;
	}

	public static Map<String, String> readLang(ArchiveFiles f) {
		Map<String, String> out = new HashMap<>();
		List<String> langs = f.find(p -> p.toLowerCase(Locale.ROOT).endsWith(".lang"));
		// prefer the language the game runs in, then English
		String preferred = Tr.german() ? "de_de" : "en_us";
		langs.sort((a, b) -> Boolean.compare(b.toLowerCase(Locale.ROOT).contains(preferred), a.toLowerCase(Locale.ROOT).contains(preferred)));
		for (String p : langs) {
			for (String line : new String(f.get(p), StandardCharsets.UTF_8).split("\\R")) {
				int eq = line.indexOf('=');
				if (eq > 0 && !line.startsWith("#")) {
					String v = line.substring(eq + 1);
					int tab = v.indexOf('\t');
					if (tab >= 0) {
						v = v.substring(0, tab);
					}
					out.putIfAbsent(line.substring(0, eq).trim(), v.trim());
				}
			}
		}
		return out;
	}

	private static String parent(String p) {
		int i = p.lastIndexOf('/');
		return i < 0 ? "" : p.substring(0, i);
	}

	private static String lastSegment(String p) {
		int i = p.lastIndexOf('/');
		return i < 0 ? p : p.substring(i + 1);
	}

	public static String sanitize(String s) {
		String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
		if (out.isEmpty()) {
			out = "cosmetic";
		}
		return out.length() > 80 ? out.substring(0, 80) : out;
	}

	private static String uniqueId(String id, Set<String> ids) {
		String c = id;
		int n = 2;
		while (!ids.add(c)) {
			c = id + "_" + n++;
		}
		return c;
	}

	public static String prettify(String raw) {
		String s = raw.replaceAll("^persona_", "").replace('_', ' ').replace('.', ' ').trim();
		if (s.isEmpty()) {
			return raw;
		}
		StringBuilder b = new StringBuilder();
		for (String w : s.split("\\s+")) {
			if (!w.isEmpty()) {
				b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
			}
		}
		return b.toString().trim();
	}
}
