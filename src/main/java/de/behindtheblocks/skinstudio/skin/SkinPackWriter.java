package de.behindtheblocks.skinstudio.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.bedrock.BedrockImporter;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Builds an own skin pack in the Bedrock layout (manifest.json, skins.json, texts/*.lang, textures and,
 * for skins with 3D extras, geometry.json). The same files work as a folder in {@code skin_packs/}, inside a
 * resource pack and – zipped as {@code .mcpack} – in Bedrock.
 */
public final class SkinPackWriter {
	private SkinPackWriter() {
	}

	public static String packId(String name) {
		String id = BedrockImporter.sanitize(name.toLowerCase(Locale.ROOT));
		return id.isBlank() ? "my_skins" : id;
	}

	public static ArchiveFiles write(String name, List<SkinData> skins) {
		String id = packId(name);
		ArchiveFiles f = ArchiveFiles.empty();
		JsonObject manifest = new JsonObject();
		manifest.addProperty("format_version", 1);
		JsonObject header = new JsonObject();
		header.addProperty("name", name);
		header.addProperty("uuid", UUID.nameUUIDFromBytes(("btb-skinpack-" + id).getBytes(StandardCharsets.UTF_8)).toString());
		header.add("version", Json.array(1, 0, 0));
		manifest.add("header", header);
		JsonArray modules = new JsonArray();
		JsonObject module = new JsonObject();
		module.addProperty("type", "skin_pack");
		module.addProperty("uuid", UUID.nameUUIDFromBytes(("btb-skinpack-module-" + id).getBytes(StandardCharsets.UTF_8)).toString());
		module.add("version", Json.array(1, 0, 0));
		modules.add(module);
		manifest.add("modules", modules);
		f.put("manifest.json", Json.PRETTY.toJson(manifest).getBytes(StandardCharsets.UTF_8));

		JsonObject skinsJson = new JsonObject();
		skinsJson.addProperty("serialize_name", id);
		skinsJson.addProperty("localization_name", id);
		JsonArray list = new JsonArray();
		JsonObject geometry = new JsonObject();
		geometry.addProperty("format_version", "1.12.0");
		JsonArray geoList = new JsonArray();
		StringBuilder en = new StringBuilder("skinpack." + id + "=" + name + "\n");
		StringBuilder de = new StringBuilder("skinpack." + id + "=" + name + "\n");
		Set<String> used = new LinkedHashSet<>();
		for (SkinData s : skins) {
			String key = BedrockImporter.sanitize(s.name("en").toLowerCase(Locale.ROOT));
			if (key.isBlank()) {
				key = "skin";
			}
			String unique = key;
			for (int i = 2; !used.add(unique); i++) {
				unique = key + "_" + i;
			}
			JsonObject e = new JsonObject();
			e.addProperty("localization_name", unique);
			String geoId = "geometry.humanoid.custom" + (s.slim ? "Slim" : "");
			if (s.accessories != null && !s.accessories.bones.isEmpty()) {
				geoId = "geometry." + id + "." + unique;
				GeoModel g = s.accessories.copy();
				g.identifier = geoId;
				for (var el : GeometryIO.write(g).getAsJsonArray("minecraft:geometry")) {
					geoList.add(el);
				}
			}
			e.addProperty("geometry", geoId);
			e.addProperty("texture", unique + ".png");
			e.addProperty("type", "free");
			list.add(e);
			f.put(unique + ".png", s.texture);
			en.append("skin.").append(id).append('.').append(unique).append('=').append(s.name("en")).append('\n');
			de.append("skin.").append(id).append('.').append(unique).append('=').append(s.name("de")).append('\n');
		}
		skinsJson.add("skins", list);
		f.put("skins.json", Json.PRETTY.toJson(skinsJson).getBytes(StandardCharsets.UTF_8));
		if (!geoList.isEmpty()) {
			geometry.add("minecraft:geometry", geoList);
			f.put("geometry.json", Json.PRETTY.toJson(geometry).getBytes(StandardCharsets.UTF_8));
		}
		f.put("texts/en_US.lang", en.toString().getBytes(StandardCharsets.UTF_8));
		f.put("texts/de_DE.lang", de.toString().getBytes(StandardCharsets.UTF_8));
		f.put("texts/languages.json", "[\"en_US\", \"de_DE\"]".getBytes(StandardCharsets.UTF_8));
		return f;
	}
}
