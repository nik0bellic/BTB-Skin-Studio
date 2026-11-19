package de.behindtheblocks.skinstudio.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** {@code pack.json} of a {@code .btbcosmeticpack}. */
public final class PackManifest {
	public static final int FORMAT = 1;
	public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,48}");

	public String id;
	public String version = "1.0.0";
	public final Map<String, String> names = new LinkedHashMap<>();
	public final Map<String, String> descriptions = new LinkedHashMap<>();
	public String author = "";
	public String icon;
	/** Free categories/labels of the pack itself (e.g. "Fantasy", "Halloween"). */
	public final List<String> categories = new ArrayList<>();
	/** Optional explicit folder list; empty = every folder below {@code cosmetics/} with a cosmetic.json. */
	public final List<String> cosmetics = new ArrayList<>();

	public static PackManifest read(JsonObject o) {
		PackManifest m = new PackManifest();
		int format = (int) Json.number(o, "format", 1);
		if (format > FORMAT) {
			throw new FormatException("Pack-Format " + format + " ist neuer als diese Mod-Version unterstützt (" + FORMAT + ")");
		}
		m.id = Json.requireString(o, "id");
		if (!ID.matcher(m.id).matches()) {
			throw new FormatException("Ungültige Pack-ID '" + m.id + "' (nur a-z 0-9 _ . -)");
		}
		m.version = Json.string(o, "version", "1.0.0");
		readLocalized(o, "name", m.names);
		readLocalized(o, "description", m.descriptions);
		if (m.names.isEmpty()) {
			m.names.put("en", m.id);
		}
		m.author = Json.string(o, "author", "");
		m.icon = Json.string(o, "icon", null);
		m.categories.addAll(Json.strings(o, "categories"));
		m.cosmetics.addAll(Json.strings(o, "cosmetics"));
		return m;
	}

	private static void readLocalized(JsonObject o, String key, Map<String, String> out) {
		var e = o.get(key);
		if (e == null) {
			return;
		}
		if (e.isJsonPrimitive()) {
			out.put("en", e.getAsString());
			out.put("de", e.getAsString());
		} else if (e.isJsonObject()) {
			e.getAsJsonObject().entrySet().forEach(x -> out.put(x.getKey(), x.getValue().getAsString()));
		}
	}

	public JsonObject write() {
		JsonObject o = new JsonObject();
		o.addProperty("format", FORMAT);
		o.addProperty("id", id);
		o.addProperty("version", version);
		JsonObject n = new JsonObject();
		names.forEach(n::addProperty);
		o.add("name", n);
		if (!descriptions.isEmpty()) {
			JsonObject d = new JsonObject();
			descriptions.forEach(d::addProperty);
			o.add("description", d);
		}
		if (!author.isEmpty()) {
			o.addProperty("author", author);
		}
		if (icon != null) {
			o.addProperty("icon", icon);
		}
		if (!categories.isEmpty()) {
			JsonArray a = new JsonArray();
			categories.forEach(a::add);
			o.add("categories", a);
		}
		return o;
	}

	public String name(String lang) {
		String l = lang == null ? "de" : lang.substring(0, Math.min(2, lang.length()));
		return names.getOrDefault(l, names.getOrDefault("en", names.values().iterator().next()));
	}

	public String description(String lang) {
		if (descriptions.isEmpty()) {
			return "";
		}
		String l = lang == null ? "de" : lang.substring(0, Math.min(2, lang.length()));
		return descriptions.getOrDefault(l, descriptions.getOrDefault("en", descriptions.values().iterator().next()));
	}

	/** Compares dotted versions numerically ("1.10.0" > "1.9.2"). */
	public static int compareVersions(String a, String b) {
		String[] x = a.split("[.-]"), y = b.split("[.-]");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int xi = i < x.length ? parse(x[i]) : 0, yi = i < y.length ? parse(y[i]) : 0;
			if (xi != yi) {
				return Integer.compare(xi, yi);
			}
		}
		return 0;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.replaceAll("\\D.*", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
