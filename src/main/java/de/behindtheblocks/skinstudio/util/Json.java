package de.behindtheblocks.skinstudio.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small, defensive JSON helpers. Every reader reports a readable message instead of a stack trace. */
public final class Json {
	public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private Json() {
	}

	/** Parses lenient JSON (Bedrock files often contain comments and trailing commas). */
	public static JsonElement parse(String text) {
		try {
			String clean = text.startsWith("﻿") ? text.substring(1) : text;
			JsonReader reader = new JsonReader(new StringReader(clean));
			reader.setStrictness(Strictness.LENIENT);
			return JsonParser.parseReader(reader);
		} catch (RuntimeException e) {
			throw new FormatException("Ungültiges JSON: " + rootMessage(e));
		}
	}

	public static JsonElement parse(byte[] bytes) {
		return parse(new String(bytes, StandardCharsets.UTF_8));
	}

	public static JsonObject parseObject(byte[] bytes) {
		JsonElement e = parse(bytes);
		if (!e.isJsonObject()) {
			throw new FormatException("JSON-Objekt erwartet");
		}
		return e.getAsJsonObject();
	}

	private static String rootMessage(Throwable t) {
		while (t.getCause() != null) {
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}

	public static String string(JsonObject o, String key, String def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}

	public static String requireString(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || e.getAsString().isBlank()) {
			throw new FormatException("Feld '" + key + "' fehlt");
		}
		return e.getAsString();
	}

	public static float number(JsonObject o, String key, float def) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) {
			return def;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber()) {
			return p.getAsFloat();
		}
		try {
			return Float.parseFloat(p.getAsString());
		} catch (NumberFormatException ex) {
			throw new FormatException("Feld '" + key + "' ist keine Zahl: " + p.getAsString());
		}
	}

	public static boolean bool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
	}

	/** Reads [x, y, z] (also accepts a single number, applied to all components). */
	public static float[] vec3(JsonElement e, float[] def) {
		if (e == null || e.isJsonNull()) {
			return def;
		}
		if (e.isJsonPrimitive()) {
			float v = e.getAsFloat();
			return new float[]{v, v, v};
		}
		if (!e.isJsonArray()) {
			throw new FormatException("Vektor [x, y, z] erwartet");
		}
		JsonArray a = e.getAsJsonArray();
		float[] out = new float[3];
		for (int i = 0; i < 3; i++) {
			out[i] = i < a.size() ? a.get(i).getAsFloat() : (def != null ? def[i] : 0);
		}
		return out;
	}

	public static float[] vec(JsonObject o, String key, float[] def) {
		try {
			return vec3(o.get(key), def);
		} catch (RuntimeException e) {
			throw new FormatException("Feld '" + key + "': " + e.getMessage());
		}
	}

	public static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		JsonElement e = o.get(key);
		if (e == null) {
			return out;
		}
		if (e.isJsonPrimitive()) {
			out.add(e.getAsString());
		} else if (e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				if (x.isJsonPrimitive()) {
					out.add(x.getAsString());
				}
			}
		}
		return out;
	}

	public static JsonArray array(float... values) {
		JsonArray a = new JsonArray();
		for (float v : values) {
			a.add(round(v));
		}
		return a;
	}

	/** Avoids writing 0.30000001 into files. */
	public static Number round(float v) {
		float r = Math.round(v * 10000f) / 10000f;
		if (r == (int) r) {
			return (int) r;
		}
		return r;
	}

	public static String lower(String s) {
		return s == null ? null : s.toLowerCase(Locale.ROOT);
	}
}
