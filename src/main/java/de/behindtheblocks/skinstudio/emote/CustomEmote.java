package de.behindtheblocks.skinstudio.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An emote built in the emote editor: name, length, repeat, an optional item and keyframes per body part
 * (eased in between like the built-in emotes). Stored and sent as a small JSON text; its network id is a hash
 * of that text ({@code c_…}), so the same emote has the same id everywhere and nothing can collide.
 */
public final class CustomEmote {
	/** Body parts in the order of the pose array ({@link Emotes#HEAD} …). */
	public static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root", "move"};
	public static final int MAX_KEYS = 400;
	public static final int MAX_DATA = 16000;
	public static final float MIN_DURATION = 0.3f, MAX_DURATION = 20f;
	private static final Pattern ITEM = Pattern.compile("[a-z0-9_.\\-]{1,32}:[a-z0-9_./\\-]{1,48}");

	/** One keyframe: time in seconds, part index (0–7), x/y/z (degrees; pixels for "move"). */
	public record Key(float t, int part, float x, float y, float z) {
	}

	public String name = "";
	public float duration = 2f;
	public boolean loop;
	/** Game item in the right hand (e.g. "minecraft:diamond") or null. */
	public String item;
	public final List<Key> keys = new ArrayList<>();

	public CustomEmote copy() {
		CustomEmote c = new CustomEmote();
		c.name = name;
		c.duration = duration;
		c.loop = loop;
		c.item = item;
		c.keys.addAll(keys);
		return c;
	}

	/** Pose array index of a part (0–7). */
	public static int offset(int part) {
		return part * 3;
	}

	public static float limit(int part) {
		return part == 7 ? 16 : 180;
	}

	// ------------------------------------------------------------------ editing

	public List<Key> keysOf(int part) {
		List<Key> out = new ArrayList<>();
		for (Key k : keys) {
			if (k.part() == part) {
				out.add(k);
			}
		}
		out.sort(Comparator.comparingDouble(Key::t));
		return out;
	}

	/** The keyframe of {@code part} at time {@code t} (± 0.02 s), or null. */
	public Key keyAt(int part, float t) {
		for (Key k : keys) {
			if (k.part() == part && Math.abs(k.t() - t) < 0.02f) {
				return k;
			}
		}
		return null;
	}

	/** Sets (adds or replaces) the keyframe of {@code part} at {@code t}. */
	public void setKey(int part, float t, float x, float y, float z) {
		Key old = keyAt(part, t);
		if (old != null) {
			keys.remove(old);
			t = old.t();
		} else if (keys.size() >= MAX_KEYS) {
			return;
		}
		float l = limit(part);
		keys.add(new Key(round(Math.max(0, Math.min(duration, t)), 100), part, clamp(x, l), clamp(y, l), clamp(z, l)));
	}

	public void removeKey(int part, float t) {
		Key k = keyAt(part, t);
		if (k != null) {
			keys.remove(k);
		}
	}

	/** Values of a part at {@code t} (eased between its keyframes), or null when the part has no keyframes. */
	public float[] valueAt(int part, float t) {
		List<Key> ks = keysOf(part);
		if (ks.isEmpty()) {
			return null;
		}
		Key a = ks.get(0), b = a;
		for (Key k : ks) {
			if (k.t() <= t) {
				a = b = k;
			} else {
				b = k;
				break;
			}
		}
		float f = b == a || b.t() <= a.t() ? 0 : smooth((t - a.t()) / (b.t() - a.t()));
		return new float[]{a.x() + (b.x() - a.x()) * f, a.y() + (b.y() - a.y()) * f, a.z() + (b.z() - a.z()) * f};
	}

	/** Fills a pose array (see {@link Emotes#sample}) at time {@code t}; parts without keyframes stay NaN. */
	public void poseAt(float t, float[] out) {
		for (int p = 0; p < PARTS.length; p++) {
			float[] v = valueAt(p, t);
			if (v != null) {
				System.arraycopy(v, 0, out, offset(p), 3);
			}
		}
	}

	public Emotes.Emote toEmote(String id) {
		return new Emotes.Emote(id, duration, loop, this::poseAt, item);
	}

	// ------------------------------------------------------------------ text form

	public String toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.addProperty("duration", round(duration, 100));
		o.addProperty("loop", loop);
		if (item != null) {
			o.addProperty("item", item);
		}
		JsonArray arr = new JsonArray();
		List<Key> sorted = new ArrayList<>(keys);
		sorted.sort(Comparator.comparingInt(Key::part).thenComparingDouble(Key::t));
		for (Key k : sorted) {
			JsonArray a = new JsonArray();
			a.add(PARTS[k.part()]);
			a.add(round(k.t(), 100));
			a.add(round(k.x(), 10));
			a.add(round(k.y(), 10));
			a.add(round(k.z(), 10));
			arr.add(a);
		}
		o.add("keys", arr);
		return o.toString();
	}

	/** Reads and checks an emote; anything out of range is rejected or clamped (data from other players). */
	public static CustomEmote parse(String json) {
		if (json == null || json.length() > MAX_DATA) {
			throw new IllegalArgumentException("emote too large");
		}
		JsonElement el = JsonParser.parseString(json);
		if (!el.isJsonObject()) {
			throw new IllegalArgumentException("not an emote");
		}
		JsonObject o = el.getAsJsonObject();
		CustomEmote c = new CustomEmote();
		String name = o.has("name") ? o.get("name").getAsString() : "";
		c.name = name.replaceAll("[\\p{Cntrl}§]", "").strip();
		if (c.name.length() > 32) {
			c.name = c.name.substring(0, 32);
		}
		c.duration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, o.has("duration") ? o.get("duration").getAsFloat() : 2f));
		if (!Float.isFinite(c.duration)) {
			throw new IllegalArgumentException("bad duration");
		}
		c.loop = o.has("loop") && o.get("loop").getAsBoolean();
		if (o.has("item")) {
			String item = o.get("item").getAsString().toLowerCase(Locale.ROOT);
			c.item = ITEM.matcher(item).matches() ? item : null;
		}
		if (o.has("keys")) {
			for (JsonElement ke : o.getAsJsonArray("keys")) {
				if (c.keys.size() >= MAX_KEYS) {
					break;
				}
				JsonArray a = ke.getAsJsonArray();
				int part = java.util.Arrays.asList(PARTS).indexOf(a.get(0).getAsString());
				float t = a.get(1).getAsFloat(), x = a.get(2).getAsFloat(), y = a.get(3).getAsFloat(), z = a.get(4).getAsFloat();
				if (part < 0 || !Float.isFinite(t) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
					continue;
				}
				float l = limit(part);
				c.keys.add(new Key(Math.max(0, Math.min(c.duration, t)), part, clamp(x, l), clamp(y, l), clamp(z, l)));
			}
		}
		return c;
	}

	/** Network id: {@code c_} and the first 12 hex digits of the SHA-1 of the JSON text. */
	public String networkId() {
		return idOf(toJson());
	}

	public static String idOf(String json) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest(json.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder("c_");
			for (int i = 0; i < 6; i++) {
				sb.append(String.format(Locale.ROOT, "%02x", d[i]));
			}
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static boolean isNetworkId(String id) {
		return id != null && id.matches("c_[0-9a-f]{12}");
	}

	private static float clamp(float v, float l) {
		return Math.max(-l, Math.min(l, v));
	}

	private static float round(float v, int f) {
		return Math.round(v * f) / (float) f;
	}

	private static float smooth(float f) {
		f = Math.max(0, Math.min(1, f));
		return f * f * (3 - 2 * f);
	}
}
