package de.behindtheblocks.skinstudio.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes Bedrock geometry JSON.
 * <ul>
 *   <li>format 1.12.0+ ({@code "minecraft:geometry": [ … ]}, box UV and per-face UV)</li>
 *   <li>legacy 1.8.0 / 1.10.0 ({@code "geometry.name": { … }}, incl. {@code name:parent} inheritance)</li>
 * </ul>
 * Poly meshes and locators are skipped with a warning; everything else is kept 1:1.
 */
public final class GeometryIO {
	public static final int MAX_BONES = 256;
	public static final int MAX_CUBES = 4096;

	private GeometryIO() {
	}

	public record Result(List<GeoModel> models, List<String> warnings) {
		public GeoModel first() {
			return models.getFirst();
		}

		public GeoModel find(String identifier) {
			if (identifier == null) {
				return first();
			}
			for (GeoModel m : models) {
				if (identifier.equalsIgnoreCase(m.identifier)) {
					return m;
				}
			}
			return null;
		}
	}

	public static Result read(byte[] bytes) {
		JsonObject root = Json.parseObject(bytes);
		return read(root);
	}

	public static Result read(JsonObject root) {
		List<GeoModel> models = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		if (root.has("minecraft:geometry")) {
			JsonElement g = root.get("minecraft:geometry");
			if (!g.isJsonArray()) {
				throw new FormatException("'minecraft:geometry' muss eine Liste sein");
			}
			for (JsonElement e : g.getAsJsonArray()) {
				if (e.isJsonObject()) {
					models.add(readModern(e.getAsJsonObject(), warnings));
				}
			}
		} else {
			Map<String, JsonObject> legacy = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if (entry.getKey().startsWith("geometry.") && entry.getValue().isJsonObject()) {
					legacy.put(entry.getKey(), entry.getValue().getAsJsonObject());
				}
			}
			Map<String, GeoModel> byName = new LinkedHashMap<>();
			for (Map.Entry<String, JsonObject> entry : legacy.entrySet()) {
				String key = entry.getKey();
				String name = key;
				String parent = null;
				int colon = key.indexOf(':');
				if (colon > 0) {
					name = key.substring(0, colon);
					parent = key.substring(colon + 1);
				}
				GeoModel model = readLegacy(name, entry.getValue(), warnings);
				if (parent != null) {
					GeoModel base = byName.get(parent);
					if (base == null) {
						warnings.add("Basis-Geometrie '" + parent + "' von '" + name + "' nicht in dieser Datei – ohne Vererbung geladen");
					} else {
						model = inherit(base, model);
					}
				}
				byName.put(name, model);
				models.add(model);
			}
		}
		if (models.isEmpty()) {
			throw new FormatException("Keine Geometrie gefunden (weder 'minecraft:geometry' noch 'geometry.*')");
		}
		for (GeoModel m : models) {
			if (m.bones.size() > MAX_BONES || m.cubeCount() > MAX_CUBES) {
				throw new FormatException("Modell '" + m.identifier + "' ist zu groß (" + m.bones.size() + " Bones, " + m.cubeCount() + " Würfel)");
			}
		}
		return new Result(models, warnings);
	}

	private static GeoModel inherit(GeoModel base, GeoModel child) {
		GeoModel out = base.copy();
		out.identifier = child.identifier;
		if (child.textureWidth > 0) {
			out.textureWidth = child.textureWidth;
			out.textureHeight = child.textureHeight;
		}
		for (GeoBone b : child.bones) {
			GeoBone existing = out.bone(b.name);
			if (existing != null) {
				out.bones.set(out.bones.indexOf(existing), b);
			} else {
				out.bones.add(b);
			}
		}
		return out;
	}

	private static GeoModel readModern(JsonObject geo, List<String> warnings) {
		JsonObject desc = geo.has("description") && geo.get("description").isJsonObject() ? geo.getAsJsonObject("description") : new JsonObject();
		GeoModel model = new GeoModel(Json.string(desc, "identifier", "geometry.unnamed"),
				(int) Json.number(desc, "texture_width", 64), (int) Json.number(desc, "texture_height", 64));
		readBones(geo, model, warnings);
		return model;
	}

	private static GeoModel readLegacy(String name, JsonObject geo, List<String> warnings) {
		GeoModel model = new GeoModel(name, (int) Json.number(geo, "texturewidth", 64), (int) Json.number(geo, "textureheight", 64));
		readBones(geo, model, warnings);
		return model;
	}

	private static void readBones(JsonObject geo, GeoModel model, List<String> warnings) {
		JsonElement bones = geo.get("bones");
		if (bones == null || !bones.isJsonArray()) {
			return;
		}
		for (JsonElement be : bones.getAsJsonArray()) {
			if (!be.isJsonObject()) {
				continue;
			}
			JsonObject bo = be.getAsJsonObject();
			GeoBone bone = new GeoBone(Json.string(bo, "name", "bone" + model.bones.size()));
			bone.parent = Json.string(bo, "parent", null);
			bone.pivot = Json.vec(bo, "pivot", new float[3]);
			bone.rotation = Json.vec(bo, "rotation", new float[3]);
			bone.mirror = Json.bool(bo, "mirror", false);
			bone.inflate = Json.number(bo, "inflate", 0);
			if (bo.has("poly_mesh")) {
				warnings.add("Bone '" + bone.name + "': poly_mesh wird nicht unterstützt und ausgelassen");
			}
			JsonElement cubes = bo.get("cubes");
			if (cubes != null && cubes.isJsonArray()) {
				for (JsonElement ce : cubes.getAsJsonArray()) {
					if (ce.isJsonObject()) {
						bone.cubes.add(readCube(ce.getAsJsonObject(), bone, model.identifier));
					}
				}
			}
			model.bones.add(bone);
		}
	}

	private static GeoCube readCube(JsonObject co, GeoBone bone, String modelName) {
		GeoCube c = new GeoCube();
		c.origin = Json.vec(co, "origin", new float[3]);
		c.size = Json.vec(co, "size", new float[3]);
		c.inflate = co.has("inflate") ? Json.number(co, "inflate", 0) : bone.inflate;
		c.mirror = co.has("mirror") ? Json.bool(co, "mirror", false) : bone.mirror;
		if (co.has("rotation")) {
			c.rotation = Json.vec(co, "rotation", new float[3]);
			c.pivot = Json.vec(co, "pivot", new float[]{c.origin[0] + c.size[0] / 2, c.origin[1] + c.size[1] / 2, c.origin[2] + c.size[2] / 2});
		}
		JsonElement uv = co.get("uv");
		if (uv == null) {
			c.u = 0;
			c.v = 0;
		} else if (uv.isJsonArray()) {
			JsonArray a = uv.getAsJsonArray();
			c.u = a.size() > 0 ? a.get(0).getAsFloat() : 0;
			c.v = a.size() > 1 ? a.get(1).getAsFloat() : 0;
		} else if (uv.isJsonObject()) {
			c.faces = new EnumMap<>(GeoCube.Face.class);
			for (GeoCube.Face f : GeoCube.Face.values()) {
				JsonElement fe = uv.getAsJsonObject().get(f.key());
				if (fe == null || !fe.isJsonObject()) {
					continue;
				}
				JsonObject fo = fe.getAsJsonObject();
				float[] st = vec2(fo.get("uv"));
				float[] sz = fo.has("uv_size") ? vec2(fo.get("uv_size")) : defaultFaceSize(f, c.size);
				c.faces.put(f, new GeoCube.FaceUv(st[0], st[1], sz[0], sz[1]));
			}
		} else {
			throw new FormatException("Modell '" + modelName + "', Bone '" + bone.name + "': 'uv' hat ein unbekanntes Format");
		}
		return c;
	}

	private static float[] defaultFaceSize(GeoCube.Face f, float[] size) {
		return switch (f) {
			case NORTH, SOUTH -> new float[]{size[0], size[1]};
			case EAST, WEST -> new float[]{size[2], size[1]};
			case UP, DOWN -> new float[]{size[0], size[2]};
		};
	}

	private static float[] vec2(JsonElement e) {
		if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 2) {
			return new float[2];
		}
		return new float[]{e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()};
	}

	/** Writes format 1.12.0 (readable by Blockbench and Bedrock). */
	public static JsonObject write(GeoModel model) {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.12.0");
		JsonArray list = new JsonArray();
		JsonObject geo = new JsonObject();
		JsonObject desc = new JsonObject();
		desc.addProperty("identifier", model.identifier);
		desc.addProperty("texture_width", model.textureWidth);
		desc.addProperty("texture_height", model.textureHeight);
		geo.add("description", desc);
		JsonArray bones = new JsonArray();
		for (GeoBone b : model.bones) {
			JsonObject bo = new JsonObject();
			bo.addProperty("name", b.name);
			if (b.parent != null) {
				bo.addProperty("parent", b.parent);
			}
			bo.add("pivot", Json.array(b.pivot));
			if (b.rotation[0] != 0 || b.rotation[1] != 0 || b.rotation[2] != 0) {
				bo.add("rotation", Json.array(b.rotation));
			}
			if (b.mirror) {
				bo.addProperty("mirror", true);
			}
			JsonArray cubes = new JsonArray();
			for (GeoCube c : b.cubes) {
				JsonObject co = new JsonObject();
				co.add("origin", Json.array(c.origin));
				co.add("size", Json.array(c.size));
				if (c.inflate != 0) {
					co.addProperty("inflate", Json.round(c.inflate));
				}
				if (c.mirror != b.mirror) {
					co.addProperty("mirror", c.mirror);
				}
				if (c.rotation != null) {
					co.add("pivot", Json.array(c.pivot));
					co.add("rotation", Json.array(c.rotation));
				}
				if (c.faces == null) {
					co.add("uv", Json.array(c.u, c.v));
				} else {
					JsonObject uv = new JsonObject();
					for (Map.Entry<GeoCube.Face, GeoCube.FaceUv> f : c.faces.entrySet()) {
						JsonObject fo = new JsonObject();
						fo.add("uv", Json.array(f.getValue().u(), f.getValue().v()));
						fo.add("uv_size", Json.array(f.getValue().w(), f.getValue().h()));
						uv.add(f.getKey().key(), fo);
					}
					co.add("uv", uv);
				}
				cubes.add(co);
			}
			if (!cubes.isEmpty()) {
				bo.add("cubes", cubes);
			}
			bones.add(bo);
		}
		geo.add("bones", bones);
		list.add(geo);
		root.add("minecraft:geometry", list);
		return root;
	}
}
