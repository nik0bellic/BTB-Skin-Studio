package de.behindtheblocks.skinstudio;

import com.google.gson.JsonObject;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Both language files must be strict JSON (Minecraft rejects the whole file otherwise) and complete. */
class LanguageFilesTest {
	private static JsonObject load(String lang) throws Exception {
		try (InputStream in = LanguageFilesTest.class.getResourceAsStream("/assets/btb_skinstudio/lang/" + lang + ".json")) {
			assertNotNull(in, lang + " missing");
			return new GsonBuilder().setStrictness(Strictness.STRICT).create()
					.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		}
	}

	private static int placeholders(String s) {
		Matcher m = Pattern.compile("%s").matcher(s);
		int n = 0;
		while (m.find()) {
			n++;
		}
		return n;
	}

	@Test
	void germanAndEnglishHaveSameKeysAndPlaceholders() throws Exception {
		JsonObject de = load("de_de"), en = load("en_us");
		Set<String> onlyDe = new TreeSet<>(de.keySet()), onlyEn = new TreeSet<>(en.keySet());
		onlyDe.removeAll(en.keySet());
		onlyEn.removeAll(de.keySet());
		assertTrue(onlyDe.isEmpty(), "only in de_de: " + onlyDe);
		assertTrue(onlyEn.isEmpty(), "only in en_us: " + onlyEn);
		for (String k : de.keySet()) {
			String d = de.get(k).getAsString(), e = en.get(k).getAsString();
			boolean fragment = d.startsWith(" ") || e.startsWith(" ");
			assertFalse(!fragment && (d.isBlank() || e.isBlank()), "empty text: " + k);
			assertEquals(placeholders(d), placeholders(e), "placeholder count differs: " + k);
		}
	}
}
