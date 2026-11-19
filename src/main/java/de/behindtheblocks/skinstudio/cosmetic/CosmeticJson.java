package de.behindtheblocks.skinstudio.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.util.Colors;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;

import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads/writes {@code cosmetic.json}. Validation errors are thrown as {@link FormatException} with German text. */
public final class CosmeticJson {
	/** namespace:path, lowercase, like Minecraft identifiers. */
	public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,48}:[a-z0-9_./-]{1,96}");
	private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_./ -]{1,128}");

	private CosmeticJson() {
	}

	public static boolean isValidId(String id) {
		return id != null && ID.matcher(id).matches();
	}

	public static CosmeticDefinition read(JsonObject o) {
		CosmeticDefinition d = new CosmeticDefinition();
		int format = (int) Json.number(o, "format", 1);
		if (format > CosmeticDefinition.FORMAT) {
			throw new FormatException("Cosmetic-Format " + format + " ist neuer als diese Version von BTB Skin Studio (" + CosmeticDefinition.FORMAT + ") – bitte die Mod aktualisieren");
		}
		d.id = Json.requireString(o, "id");
		if (!isValidId(d.id)) {
			throw new FormatException("Ungültige ID '" + d.id + "' (erwartet namespace:name, nur a-z 0-9 _ . -)");
		}
		d.version = Json.string(o, "version", "1.0.0");
		readLocalized(o.get("name"), d.names);
		readLocalized(o.get("description"), d.descriptions);
		if (d.names.isEmpty()) {
			d.names.put("en", d.path());
		}
		d.author = Json.string(o, "author", "");
		String type = Json.string(o, "type", null);
		if (type != null) {
			d.type = CosmeticType.byId(type);
			if (d.type == null) {
				throw new FormatException("Unbekannter Typ '" + type + "'");
			}
		}
		String att = Json.string(o, "attachment", null);
		d.attachment = att == null ? d.type.defaultAttachment : AttachmentPoint.byId(att);
		if (d.attachment == null) {
			throw new FormatException("Unbekannter Befestigungspunkt '" + att + "'");
		}
		d.slots = EnumSet.noneOf(CosmeticSlot.class);
		for (String s : Json.strings(o, "slots")) {
			CosmeticSlot slot = CosmeticSlot.byId(s);
			if (slot == null) {
				throw new FormatException("Unbekannter Slot '" + s + "'");
			}
			d.slots.add(slot);
		}
		d.model = file(o, "model", "model.geo.json");
		d.geometry = Json.string(o, "geometry", null);
		d.texture = file(o, "texture", "texture.png");
		d.emissiveTexture = file(o, "emissive_texture", null);
		d.tintMask = file(o, "tint_mask", null);
		d.animationFile = file(o, "animation", null);
		d.animations.addAll(Json.strings(o, "animations"));
		d.preview = file(o, "preview", null);

		if (o.has("transform") && o.get("transform").isJsonObject()) {
			JsonObject t = o.getAsJsonObject("transform");
			d.transform.offset = Json.vec(t, "offset", new float[3]);
			d.transform.rotation = Json.vec(t, "rotation", new float[3]);
			d.transform.scale = Json.vec(t, "scale", new float[]{1, 1, 1});
			for (float s : d.transform.scale) {
				if (s <= 0 || s > 16) {
					throw new FormatException("Skalierung muss zwischen 0 und 16 liegen");
				}
			}
		}
		if (o.has("tint") && o.get("tint").isJsonObject()) {
			JsonObject t = o.getAsJsonObject("tint");
			d.tint.enabled = Json.bool(t, "enabled", true);
			d.tint.defaultColor = Colors.parse(Json.string(t, "default", "#FFFFFF"));
			for (String c : Json.strings(t, "palette")) {
				d.tint.palette.add(Colors.parse(c));
			}
		}
		if (o.has("glow") && o.get("glow").isJsonObject()) {
			JsonObject g = o.getAsJsonObject("glow");
			d.glow = new CosmeticDefinition.Glow();
			d.glow.color = Colors.parse(Json.string(g, "color", "#FFB24A"));
			d.glow.size = clamp(Json.number(g, "size", 6), 0.5f, 32);
			d.glow.intensity = clamp(Json.number(g, "intensity", 0.55f), 0, 1);
			d.glow.offset = Json.vec(g, "offset", new float[3]);
			d.glow.flicker = Json.bool(g, "flicker", true);
		}
		if (o.has("particles") && o.get("particles").isJsonObject()) {
			JsonObject p = o.getAsJsonObject("particles");
			d.particles = new CosmeticDefinition.Particles();
			d.particles.type = Json.string(p, "type", "minecraft:flame");
			d.particles.rate = clamp(Json.number(p, "rate", 2), 0, 20);
			d.particles.offset = Json.vec(p, "offset", new float[3]);
			d.particles.spread = clamp(Json.number(p, "spread", 1), 0, 16);
		}
		d.hideLayers.addAll(Json.strings(o, "hide_layers"));
		d.tags.addAll(Json.strings(o, "tags"));
		d.source = Json.string(o, "source", null);
		return d;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String file(JsonObject o, String key, String def) {
		String f = Json.string(o, key, def);
		if (f == null) {
			return null;
		}
		if (!FILE.matcher(f).matches() || f.contains("..") || f.startsWith("/")) {
			throw new FormatException("Ungültiger Dateiname in '" + key + "': " + f);
		}
		return f;
	}

	private static void readLocalized(JsonElement e, Map<String, String> out) {
		if (e == null || e.isJsonNull()) {
			return;
		}
		if (e.isJsonPrimitive()) {
			out.put("en", e.getAsString());
			out.put("de", e.getAsString());
		} else if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
				if (x.getValue().isJsonPrimitive()) {
					out.put(x.getKey().toLowerCase(java.util.Locale.ROOT), x.getValue().getAsString());
				}
			}
		}
	}

	public static JsonObject write(CosmeticDefinition d) {
		JsonObject o = new JsonObject();
		o.addProperty("format", CosmeticDefinition.FORMAT);
		o.addProperty("id", d.id);
		o.addProperty("version", d.version);
		o.add("name", localized(d.names));
		if (!d.descriptions.isEmpty()) {
			o.add("description", localized(d.descriptions));
		}
		if (!d.author.isEmpty()) {
			o.addProperty("author", d.author);
		}
		o.addProperty("type", d.type.id());
		o.addProperty("attachment", d.attachment.id());
		if (!d.slots.isEmpty()) {
			JsonArray a = new JsonArray();
			d.slots.forEach(s -> a.add(s.id()));
			o.add("slots", a);
		}
		o.addProperty("model", d.model);
		if (d.geometry != null) {
			o.addProperty("geometry", d.geometry);
		}
		o.addProperty("texture", d.texture);
		if (d.emissiveTexture != null) {
			o.addProperty("emissive_texture", d.emissiveTexture);
		}
		if (d.tintMask != null) {
			o.addProperty("tint_mask", d.tintMask);
		}
		if (d.animationFile != null) {
			o.addProperty("animation", d.animationFile);
		}
		if (!d.animations.isEmpty()) {
			JsonArray a = new JsonArray();
			d.animations.forEach(a::add);
			o.add("animations", a);
		}
		if (d.preview != null) {
			o.addProperty("preview", d.preview);
		}
		if (!d.transform.isIdentity()) {
			JsonObject t = new JsonObject();
			t.add("offset", Json.array(d.transform.offset));
			t.add("rotation", Json.array(d.transform.rotation));
			t.add("scale", Json.array(d.transform.scale));
			o.add("transform", t);
		}
		if (d.tint.enabled) {
			JsonObject t = new JsonObject();
			t.addProperty("enabled", true);
			t.addProperty("default", Colors.format(d.tint.defaultColor));
			JsonArray p = new JsonArray();
			d.tint.palette.forEach(c -> p.add(Colors.format(c)));
			t.add("palette", p);
			o.add("tint", t);
		}
		if (d.glow != null) {
			JsonObject g = new JsonObject();
			g.addProperty("color", Colors.format(d.glow.color));
			g.addProperty("size", Json.round(d.glow.size));
			g.addProperty("intensity", Json.round(d.glow.intensity));
			g.add("offset", Json.array(d.glow.offset));
			g.addProperty("flicker", d.glow.flicker);
			o.add("glow", g);
		}
		if (d.particles != null) {
			JsonObject p = new JsonObject();
			p.addProperty("type", d.particles.type);
			p.addProperty("rate", Json.round(d.particles.rate));
			p.add("offset", Json.array(d.particles.offset));
			p.addProperty("spread", Json.round(d.particles.spread));
			o.add("particles", p);
		}
		if (!d.hideLayers.isEmpty()) {
			JsonArray a = new JsonArray();
			d.hideLayers.forEach(a::add);
			o.add("hide_layers", a);
		}
		if (!d.tags.isEmpty()) {
			JsonArray a = new JsonArray();
			d.tags.forEach(a::add);
			o.add("tags", a);
		}
		if (d.source != null) {
			o.addProperty("source", d.source);
		}
		return o;
	}

	private static JsonObject localized(Map<String, String> m) {
		JsonObject o = new JsonObject();
		m.forEach(o::addProperty);
		return o;
	}
}
