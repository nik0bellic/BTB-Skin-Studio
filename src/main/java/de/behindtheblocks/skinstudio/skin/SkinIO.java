package de.behindtheblocks.skinstudio.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.bedrock.BedrockImporter;
import de.behindtheblocks.skinstudio.bedrock.Images;
import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.Png;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;
import de.behindtheblocks.skinstudio.util.Tr;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Skin files: {@code .btbskin} archives (skin.json + skin.png + optional accessories.geo.json),
 * plain PNG skins and Bedrock skin packs (skins.json + geometry.json, as used by the Bedrock Skins mod).
 */
public final class SkinIO {
	public static final String SKIN_JSON = "skin.json";
	public static final String SKIN_PNG = "skin.png";
	public static final String ACCESSORIES = "accessories.geo.json";
	public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,48}:[a-z0-9_./-]{1,128}");

	private SkinIO() {
	}

	// ------------------------------------------------------------------ .btbskin

	public static ArchiveFiles write(String id, Map<String, String> names, boolean slim, byte[] png, GeoModel accessories) {
		JsonObject o = new JsonObject();
		o.addProperty("format", 1);
		o.addProperty("id", id);
		JsonObject n = new JsonObject();
		names.forEach(n::addProperty);
		o.add("name", n);
		o.addProperty("model", slim ? "slim" : "wide");
		o.addProperty("texture", SKIN_PNG);
		ArchiveFiles f = ArchiveFiles.empty();
		f.put(SKIN_PNG, png);
		if (accessories != null && accessories.cubeCount() > 0) {
			o.addProperty("accessories", ACCESSORIES);
			f.put(ACCESSORIES, Json.PRETTY.toJson(GeometryIO.write(accessories)).getBytes(StandardCharsets.UTF_8));
		}
		f.put(SKIN_JSON, Json.PRETTY.toJson(o).getBytes(StandardCharsets.UTF_8));
		return f;
	}

	public static boolean isSkinArchive(ArchiveFiles raw) {
		return raw.unwrapSingleFolder().has(SKIN_JSON);
	}

	public static SkinData read(ArchiveFiles raw, SkinData.Source source, String group) {
		ArchiveFiles f = raw.unwrapSingleFolder();
		byte[] json = f.get(SKIN_JSON);
		if (json == null) {
			throw new FormatException(Tr.t("btb.err.skin_json_missing"));
		}
		JsonObject o = Json.parseObject(json);
		String id = Json.requireString(o, "id");
		if (!ID.matcher(id).matches()) {
			throw new FormatException(Tr.t("btb.err.bad_id", id));
		}
		Map<String, String> names = new LinkedHashMap<>();
		JsonElement n = o.get("name");
		if (n != null && n.isJsonObject()) {
			n.getAsJsonObject().entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
		} else if (n != null && n.isJsonPrimitive()) {
			names.put("en", n.getAsString());
		}
		byte[] png = f.get(Json.string(o, "texture", SKIN_PNG));
		if (png == null) {
			throw new FormatException(Tr.t("btb.err.file_missing", SKIN_PNG));
		}
		validateTexture(png, SKIN_PNG);
		GeoModel acc = null;
		String accName = Json.string(o, "accessories", null);
		if (accName != null && f.has(accName)) {
			acc = GeometryIO.read(f.get(accName)).first();
		}
		return new SkinData(id, names, "slim".equals(Json.string(o, "model", "wide")), png, acc, source, group, f);
	}

	/** Accepts 64×64, 64×32 (legacy, converted), and HD multiples (128×128 …). */
	public static Png.Info validateTexture(byte[] png, String name) {
		Png.Info info = Png.info(png, name);
		boolean square = info.width() == info.height();
		boolean legacy = info.width() == info.height() * 2;
		if ((!square && !legacy) || info.width() % 64 != 0 || info.width() > 512) {
			throw new FormatException(Tr.t("btb.err.skin_size", info.width() + "×" + info.height()));
		}
		return info;
	}

	// ------------------------------------------------------------------ PNG skins

	/** Creates a custom skin from a PNG; 64×32 legacy skins are converted, the arm model is detected. */
	public static SkinData fromPng(byte[] png, String fileName, Boolean forceSlim) {
		validateTexture(png, fileName);
		BufferedImage img = Images.read(png, fileName);
		if (img.getWidth() == img.getHeight() * 2) {
			img = convertLegacy(img);
			png = Images.png(img);
		}
		boolean slim = forceSlim != null ? forceSlim : detectSlim(img);
		String base = fileName.replaceAll("(?i)\\.png$", "");
		String id = "skin:" + BedrockImporter.sanitize(base);
		Map<String, String> names = new LinkedHashMap<>();
		names.put("en", base);
		return new SkinData(id, names, slim, png, null, SkinData.Source.CUSTOM, "", write(id, names, slim, png, null));
	}

	/** Slim skins leave the outer arm column (x 54–55, y 20–31 at 64 px) transparent. */
	/** Draws {@code top} (an outfit with transparent parts) over {@code base}; both 64×64. */
	public static byte[] overlay(byte[] base, byte[] top) {
		BufferedImage b = Images.read(base, "base.png"), t = Images.read(top, "outfit.png");
		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		try {
			g.drawImage(b, 0, 0, 64, 64, null);
			// the outfit replaces the base's outer layers on body, arms and legs (jacket, sleeves, pants);
			// the head with hair and hat layer stays the base's own
			g.setComposite(java.awt.AlphaComposite.Clear);
			for (int[] r : new int[][]{{16, 32, 24, 16}, {40, 32, 16, 16}, {48, 48, 16, 16}, {0, 32, 16, 16}, {0, 48, 16, 16}}) {
				g.fillRect(r[0], r[1], r[2], r[3]);
			}
			g.setComposite(java.awt.AlphaComposite.SrcOver);
			g.drawImage(t, 0, 0, 64, 64, null);
		} finally {
			g.dispose();
		}
		return Images.png(out);
	}

	/** Same skin with another texture (used for outfits composed onto a default skin). */
	public static SkinData withTexture(SkinData s, byte[] png, boolean slim) {
		return new SkinData(s.id, s.names, slim, png, s.accessories, s.source, s.group, write(s.id, s.names, slim, png, s.accessories));
	}

	public static boolean detectSlim(BufferedImage img) {
		int s = img.getWidth() / 64;
		for (int y = 20 * s; y < 32 * s; y++) {
			for (int x = 54 * s; x < 56 * s; x++) {
				if ((img.getRGB(x, y) >>> 24) != 0) {
					return false;
				}
			}
		}
		return true;
	}

	/** 64×32 → 64×64 like vanilla: the left arm/leg are mirrored copies of the right ones. */
	static BufferedImage convertLegacy(BufferedImage src) {
		int s = src.getWidth() / 64;
		BufferedImage out = new BufferedImage(64 * s, 64 * s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		copyMirrored(out, 4, 16, 4, 4, 20, 48, s);
		copyMirrored(out, 8, 16, 4, 4, 24, 48, s);
		copyMirrored(out, 0, 20, 4, 12, 24, 52, s);
		copyMirrored(out, 4, 20, 4, 12, 20, 52, s);
		copyMirrored(out, 8, 20, 4, 12, 16, 52, s);
		copyMirrored(out, 12, 20, 4, 12, 28, 52, s);
		copyMirrored(out, 44, 16, 4, 4, 36, 48, s);
		copyMirrored(out, 48, 16, 4, 4, 40, 48, s);
		copyMirrored(out, 40, 20, 4, 12, 40, 52, s);
		copyMirrored(out, 44, 20, 4, 12, 36, 52, s);
		copyMirrored(out, 48, 20, 4, 12, 32, 52, s);
		copyMirrored(out, 52, 20, 4, 12, 44, 52, s);
		return out;
	}

	private static void copyMirrored(BufferedImage img, int sx, int sy, int w, int h, int tx, int ty, int s) {
		for (int y = 0; y < h * s; y++) {
			for (int x = 0; x < w * s; x++) {
				img.setRGB(tx * s + (w * s - 1 - x), ty * s + y, img.getRGB(sx * s + x, sy * s + y));
			}
		}
	}

	// ------------------------------------------------------------------ Bedrock skin packs

	/** Reads a Bedrock skin pack folder/archive into skins; accessories come from the custom geometry. */
	public static List<SkinData> readBedrockPack(ArchiveFiles raw, String fallbackName, List<PackProblem> problems) {
		List<SkinData> out = new ArrayList<>();
		ArchiveFiles f = raw.unwrapSingleFolder();
		if (BedrockImporter.isEncrypted(f)) {
			problems.add(PackProblem.error(fallbackName, Tr.t("btb.err.encrypted")));
			return out;
		}
		byte[] skinsJson = f.get("skins.json");
		if (skinsJson == null) {
			problems.add(PackProblem.error(fallbackName, Tr.t("btb.err.no_skins_json")));
			return out;
		}
		JsonObject skins = Json.parseObject(skinsJson);
		String packName = Json.string(skins, "localization_name", Json.string(skins, "serialize_name", fallbackName));
		Map<String, String> lang = BedrockImporter.readLang(f);
		String packDisplay = lang.getOrDefault("skinpack." + packName, BedrockImporter.prettify(packName));
		Map<String, GeoModel> geometries = new HashMap<>();
		for (String p : f.find(BedrockImporter::isGeometryFile)) {
			try {
				for (GeoModel g : GeometryIO.read(f.get(p)).models()) {
					geometries.put(g.identifier.toLowerCase(Locale.ROOT), g);
				}
			} catch (FormatException e) {
				problems.add(PackProblem.warning(p, e.getMessage()));
			}
		}
		var list = skins.getAsJsonArray("skins");
		if (list == null) {
			problems.add(PackProblem.error(fallbackName, Tr.t("btb.err.no_skins_json")));
			return out;
		}
		String packId = BedrockImporter.sanitize(packName);
		for (JsonElement e : list) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject s = e.getAsJsonObject();
			String loc = Json.string(s, "localization_name", "skin");
			String display = lang.getOrDefault("skin." + packName + "." + loc, BedrockImporter.prettify(loc));
			String texName = Json.string(s, "texture", null);
			byte[] png = texName == null ? null : BedrockImporter.find(f, texName);
			if (png == null) {
				problems.add(PackProblem.warning(display, Tr.t("btb.err.file_missing", String.valueOf(texName))));
				continue;
			}
			try {
				validateTexture(png, texName);
				String geoId = Json.string(s, "geometry", "geometry.humanoid.custom").toLowerCase(Locale.ROOT);
				GeoModel g = geometries.get(geoId);
				boolean slim = geoId.contains("slim") || (g != null && hasSlimArms(g));
				BufferedImage img = Images.read(png, texName);
				if (img.getWidth() == img.getHeight() * 2) {
					png = Images.png(convertLegacy(img));
				}
				GeoModel acc = g == null ? null : BedrockImporter.extractAccessories(g);
				String id = "bedrock:" + packId + "/" + BedrockImporter.sanitize(loc);
				Map<String, String> names = new LinkedHashMap<>();
				names.put("en", display);
				out.add(new SkinData(id, names, slim, png, acc, SkinData.Source.BEDROCK_PACK, packDisplay, write(id, names, slim, png, acc)));
			} catch (FormatException ex) {
				problems.add(PackProblem.warning(display, ex.getMessage()));
			}
		}
		return out;
	}

	private static boolean hasSlimArms(GeoModel g) {
		for (GeoBone b : g.bones) {
			if (b.name.equalsIgnoreCase("rightArm") || b.name.equalsIgnoreCase("leftArm")) {
				for (GeoCube c : b.cubes) {
					if (c.size[0] == 3 && c.size[1] == 12) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
