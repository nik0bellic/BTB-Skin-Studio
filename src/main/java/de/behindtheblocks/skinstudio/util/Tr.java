package de.behindtheblocks.skinstudio.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translations for UI texts and user-facing messages (German and English).
 * Reads the mod's own {@code assets/btb_skinstudio/lang/*.json} from the classpath, so it also works for
 * messages produced off the render thread, in tests and on the server. The client sets the language
 * from the game options; everything else defaults to English.
 */
public final class Tr {
	private static final Map<String, Map<String, String>> CACHE = new HashMap<>();
	private static volatile String language = "en_us";

	private Tr() {
	}

	public static void setLanguage(String gameLanguage) {
		String l = gameLanguage == null ? "en_us" : gameLanguage.toLowerCase(Locale.ROOT);
		language = l.startsWith("de") ? "de_de" : "en_us";
	}

	public static String language() {
		return language;
	}

	public static boolean german() {
		return language.equals("de_de");
	}

	/** Translates {@code key}; {@code %s} placeholders are filled with {@code args} in order. */
	public static String t(String key, Object... args) {
		String v = map(language).get(key);
		if (v == null) {
			v = map("en_us").get(key);
		}
		if (v == null) {
			v = key;
		}
		if (args.length == 0) {
			return v;
		}
		StringBuilder b = new StringBuilder();
		int ai = 0;
		for (int i = 0; i < v.length(); i++) {
			char c = v.charAt(i);
			if (c == '%' && i + 1 < v.length() && v.charAt(i + 1) == 's') {
				b.append(ai < args.length ? args[ai++] : "");
				i++;
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	private static Map<String, String> map(String lang) {
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(lang, Tr::load);
		}
	}

	private static Map<String, String> load(String lang) {
		Map<String, String> out = new HashMap<>();
		try (InputStream in = Tr.class.getResourceAsStream("/assets/btb_skinstudio/lang/" + lang + ".json")) {
			if (in != null) {
				JsonObject o = Json.parseObject(in.readAllBytes());
				for (Map.Entry<String, JsonElement> e : o.entrySet()) {
					out.put(e.getKey(), e.getValue().getAsString());
				}
			}
		} catch (Exception ignored) {
			// missing translations fall back to the key
		}
		return out;
	}
}
