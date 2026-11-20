package de.behindtheblocks.skinstudio.emote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in emotes. An emote is a pose over time for head, body, arms and legs (degrees, Java model space);
 * {@link Float#NaN} keeps the game's own animation for that value. Pure data, shared by client and server
 * (the server only checks ids).
 */
public final class Emotes {
	public static final int HEAD = 0, BODY = 3, RIGHT_ARM = 6, LEFT_ARM = 9, RIGHT_LEG = 12, LEFT_LEG = 15, SIZE = 18;
	private static final float N = Float.NaN;
	private static final float BLEND = 0.22f;

	@FunctionalInterface
	public interface Pose {
		void at(float t, float[] out);
	}

	/** duration in seconds, loop = repeats until the player moves, item = game item held in the right hand (or null). */
	public record Emote(String id, float duration, boolean loop, Pose pose, String item) {
	}

	private static final Map<String, Emote> ALL = new LinkedHashMap<>();

	static {
		add("wave", 2.6f, false, (t, o) -> {
			set(o, RIGHT_ARM, -165, 0, 15 + sin(t * 720) * 22);
			set(o, HEAD, N, -10 + sin(t * 180) * 4, N);
		});
		add("cheer", 2.0f, true, (t, o) -> {
			float bob = sin(t * 540) * 12;
			set(o, RIGHT_ARM, -170 + bob, 0, 18);
			set(o, LEFT_ARM, -170 - bob, 0, -18);
			set(o, HEAD, -15, N, N);
		});
		add("clap", 2.4f, false, (t, o) -> {
			float c = 22 + sin(t * 1080) * 16;
			set(o, RIGHT_ARM, -70, -c, 0);
			set(o, LEFT_ARM, -70, c, 0);
		});
		// "Over there!"
		add("point", 2.2f, false, (t, o) -> {
			set(o, RIGHT_ARM, -92, -6, 0);
			set(o, LEFT_ARM, -10, 0, -8);
			set(o, HEAD, 0, -6, 0);
		});
		add("facepalm", 2.6f, false, (t, o) -> {
			set(o, RIGHT_ARM, -128, -38, 0);
			set(o, HEAD, 22 + sin(t * 360) * 3, sin(t * 240) * 8, 0);
		});
		add("think", 3.0f, false, (t, o) -> {
			set(o, RIGHT_ARM, -96, -42, 0);
			set(o, LEFT_ARM, -38, 52, 0);
			set(o, HEAD, 12, sin(t * 120) * 10, 0);
		});
		// punching a tree like on day one
		add("woodpunch", 2.4f, true, (t, o) -> {
			float p = Math.abs(sin(t * 540));
			set(o, RIGHT_ARM, -60 - 40 * p, -10, 0);
			set(o, HEAD, 10, 0, 0);
		});
		add("mine", 2.4f, true, "minecraft:diamond_pickaxe", (t, o) -> {
			float p = Math.abs(sin(t * 360));
			set(o, RIGHT_ARM, -40 - 100 * p, -8, 0);
			set(o, HEAD, 14, 0, 0);
		});
		// "Diamonds to you!"
		add("diamonds", 2.6f, false, "minecraft:diamond", (t, o) -> {
			set(o, RIGHT_ARM, -78, -12, 0);
			set(o, HEAD, 8, 0, sin(t * 240) * 4);
		});
		// floating and flailing like a ghast
		add("ghast_dance", 3.2f, true, (t, o) -> {
			float s = sin(t * 300);
			set(o, RIGHT_ARM, -30 + s * 40, 0, 30 + sin(t * 600) * 20);
			set(o, LEFT_ARM, -30 - s * 40, 0, -30 - sin(t * 600) * 20);
			set(o, RIGHT_LEG, 20 + s * 18, 0, 6);
			set(o, LEFT_LEG, 20 - s * 18, 0, -6);
			set(o, HEAD, -6 + s * 6, s * 10, 0);
		});
		// startled by a creeper: arms up, shaking
		add("creeper_scare", 2.0f, false, (t, o) -> {
			float shake = sin(t * 2400) * 4;
			set(o, RIGHT_ARM, -150 + shake, 0, 30);
			set(o, LEFT_ARM, -150 - shake, 0, -30);
			set(o, HEAD, -18, shake * 2, 0);
		});
		add("bow", 2.2f, false, (t, o) -> {
			float d = bump(t, 2.2f);
			set(o, HEAD, 45 * d, 0, 0);
			set(o, RIGHT_ARM, -18 * d, 0, 4);
			set(o, LEFT_ARM, -18 * d, 0, -4);
		});
		add("shrug", 1.8f, false, (t, o) -> {
			float d = bump(t, 1.8f);
			set(o, RIGHT_ARM, -25 * d, 0, 42 * d);
			set(o, LEFT_ARM, -25 * d, 0, -42 * d);
			set(o, HEAD, N, 0, 12 * d);
		});
		add("potion", 3.0f, false, "minecraft:potion", (t, o) -> {
			float sip = Math.max(0, sin(t * 120));
			set(o, RIGHT_ARM, -95 - 25 * sip, -32, 0);
			set(o, HEAD, -12 * sip, 0, 0);
		});
		add("cookie", 2.8f, false, "minecraft:cookie", (t, o) -> {
			set(o, RIGHT_ARM, -100 + sin(t * 540) * 6, -34, 0);
			set(o, HEAD, 6 + sin(t * 720) * 5, 0, 0);
		});
		add("banner", 3.0f, true, "minecraft:red_banner", (t, o) -> {
			set(o, RIGHT_ARM, -172, 0, 8 + sin(t * 360) * 14);
			set(o, HEAD, -10, N, N);
		});
		add("spyglass", 3.0f, false, "minecraft:spyglass", (t, o) -> {
			set(o, RIGHT_ARM, -100, -40, 0);
			set(o, LEFT_ARM, -96, 34, 0);
			set(o, HEAD, -4, sin(t * 90) * 30, 0);
		});
		add("dance", 4.0f, true, (t, o) -> {
			float s = sin(t * 360);
			set(o, RIGHT_ARM, -90 - s * 70, 0, 20 + Math.abs(s) * 20);
			set(o, LEFT_ARM, -90 + s * 70, 0, -20 - Math.abs(s) * 20);
			set(o, RIGHT_LEG, s * 25, 0, 4);
			set(o, LEFT_LEG, -s * 25, 0, -4);
			set(o, HEAD, sin(t * 720) * 8, s * 18, 0);
			set(o, BODY, 0, s * 12, 0);
		});
		add("laugh", 2.4f, false, (t, o) -> {
			set(o, HEAD, -8 + sin(t * 1440) * 7, sin(t * 360) * 6, 0);
			set(o, RIGHT_ARM, -28, -22, 0);
			set(o, LEFT_ARM, -28, 22, 0);
		});
		add("heart", 2.6f, false, (t, o) -> {
			set(o, RIGHT_ARM, -150, -22, 0);
			set(o, LEFT_ARM, -150, 22, 0);
			set(o, HEAD, -6, 0, sin(t * 360) * 6);
		});
	}

	private Emotes() {
	}

	private static void add(String id, float duration, boolean loop, Pose pose) {
		add(id, duration, loop, null, pose);
	}

	private static void add(String id, float duration, boolean loop, String prop, Pose pose) {
		ALL.put(id, new Emote(id, duration, loop, pose, prop));
	}

	/** Default wheel: five emotes. */
	public static final List<String> DEFAULT_WHEEL = List.of("wave", "cheer", "point", "diamonds", "dance");

	public static List<Emote> all() {
		return List.copyOf(ALL.values());
	}

	public static Emote get(String id) {
		return id == null ? null : ALL.get(id);
	}

	public static boolean exists(String id) {
		return ALL.containsKey(id);
	}

	/**
	 * Pose at {@code t} seconds after start: 18 angles (NaN = keep) plus, at index 18, the blend weight
	 * (0 = not playing, 1 = fully posed). Returns null when the emote is over.
	 */
	public static float[] sample(String id, float t) {
		Emote e = get(id);
		if (e == null || t < 0 || (!e.loop() && t > e.duration())) {
			return null;
		}
		float[] out = new float[SIZE + 1];
		java.util.Arrays.fill(out, N);
		float local = e.loop() ? t % e.duration() : t;
		e.pose().at(local, out);
		float w = Math.min(1, t / BLEND);
		if (!e.loop()) {
			w = Math.min(w, (e.duration() - t) / BLEND);
		}
		out[SIZE] = Math.max(0, Math.min(1, w));
		return out;
	}

	private static void set(float[] o, int part, float x, float y, float z) {
		o[part] = x;
		o[part + 1] = y;
		o[part + 2] = z;
	}

	private static float sin(float deg) {
		return (float) Math.sin(Math.toRadians(deg));
	}

	/** 0 → 1 → 0 over the duration (smooth). */
	private static float bump(float t, float d) {
		float x = Math.max(0, Math.min(1, t / d));
		return (float) Math.sin(Math.PI * x);
	}
}
