package de.behindtheblocks.skinstudio.emote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in emotes. An emote is a pose over time for head, body, arms and legs (degrees, Java model space);
 * {@link Float#NaN} keeps the game's own animation for that value. Pure data, shared by client and server
 * (the server only checks ids). Raised arms (x below -90) tilt outward with a negative z on the right arm and a
 * positive z on the left arm; a positive z there would push the hands into the head.
 */
public final class Emotes {
	public static final int HEAD = 0, BODY = 3, RIGHT_ARM = 6, LEFT_ARM = 9, RIGHT_LEG = 12, LEFT_LEG = 15;
	/** Whole figure: turn around the feet (degrees) and move (pixels, y down = negative is up). */
	public static final int ROOT = 18, MOVE = 21, SIZE = 24;
	private static final float N = Float.NaN;
	private static final float BLEND = 0.22f;

	@FunctionalInterface
	public interface Pose {
		void at(float t, float[] out);
	}

	/** duration in seconds (one cycle), loop = the cycle repeats a fixed number of times ({@link #length}), item = game item held in the right hand (or null). */
	public record Emote(String id, float duration, boolean loop, Pose pose, String item) {
	}

	private static final Map<String, Emote> ALL = new LinkedHashMap<>();

	static {
		add("wave", 2.8f, false, anim()
				.k(0, RIGHT_ARM, 0, 0, 5).k(0.45f, RIGHT_ARM, -160, 0, -25).k(2.3f, RIGHT_ARM, -160, 0, -25).k(2.8f, RIGHT_ARM, 0, 0, 5)
				.k(0, LEFT_ARM, 0, 0, -3).k(0.45f, LEFT_ARM, 4, 0, -8).k(2.3f, LEFT_ARM, 4, 0, -8).k(2.8f, LEFT_ARM, 0, 0, -3)
				.k(0, BODY, 0, 0, 0).k(0.45f, BODY, 0, 4, 5).k(2.3f, BODY, 0, 4, 5).k(2.8f, BODY, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.45f, HEAD, -6, -12, 7).k(2.3f, HEAD, -6, -12, 7).k(2.8f, HEAD, 0, 0, 0)
				.plus((t, o) -> {
					float env = window(t, 0.45f, 2.3f);
					add(o, RIGHT_ARM, 0, 0, -sin((t - 0.45f) * 540) * 18 * env);
					add(o, HEAD, 0, 0, sin((t - 0.45f) * 270) * 4 * env);
				}));
		// a little jump of joy, again and again
		add("cheer", 1.0f, true, anim()
				.k(0, MOVE, 0, 0, 0).k(0.32f, MOVE, 0, -3.5f, 0).k(0.6f, MOVE, 0, 0, 0).k(1, MOVE, 0, 0, 0)
				.k(0, RIGHT_ARM, -140, 0, -30).k(0.32f, RIGHT_ARM, -176, 0, -18).k(0.6f, RIGHT_ARM, -140, 0, -32).k(1, RIGHT_ARM, -140, 0, -30)
				.k(0, LEFT_ARM, -140, 0, 30).k(0.32f, LEFT_ARM, -176, 0, 18).k(0.6f, LEFT_ARM, -140, 0, 32).k(1, LEFT_ARM, -140, 0, 30)
				.k(0, BODY, 6, 0, 0).k(0.32f, BODY, -6, 0, 0).k(0.6f, BODY, 9, 0, 0).k(1, BODY, 6, 0, 0)
				.k(0, HEAD, -4, 0, 0).k(0.32f, HEAD, -20, 0, 0).k(0.6f, HEAD, 0, 0, 0).k(1, HEAD, -4, 0, 0)
				.k(0, RIGHT_LEG, -3, 0, 2).k(0.32f, RIGHT_LEG, 8, 0, 9).k(0.6f, RIGHT_LEG, -3, 0, 2).k(1, RIGHT_LEG, -3, 0, 2)
				.k(0, LEFT_LEG, -3, 0, -2).k(0.32f, LEFT_LEG, 8, 0, -9).k(0.6f, LEFT_LEG, -3, 0, -2).k(1, LEFT_LEG, -3, 0, -2));
		add("clap", 2.6f, false, anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.3f, RIGHT_ARM, -72, -22, 0).k(2.3f, RIGHT_ARM, -72, -22, 0).k(2.6f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.3f, LEFT_ARM, -72, 22, 0).k(2.3f, LEFT_ARM, -72, 22, 0).k(2.6f, LEFT_ARM, 0, 0, 0)
				.plus((t, o) -> {
					float env = window(t, 0.3f, 2.3f);
					float c = sin(t * 1080) * 16 * env;
					add(o, RIGHT_ARM, 0, -c, 0);
					add(o, LEFT_ARM, 0, c, 0);
					add(o, BODY, Math.abs(sin(t * 540)) * 4 * env, 0, 0);
					add(o, HEAD, -Math.abs(sin(t * 540)) * 6 * env, 0, 0);
				}));
		// "Over there!": look around, then point and lean in
		add("point", 3.2f, false, anim()
				.k(0, HEAD, 0, 0, 0).k(0.45f, HEAD, -6, -35, 0).k(1.0f, HEAD, -6, 32, 0).k(1.35f, HEAD, -2, -4, 0).k(2.8f, HEAD, -2, -4, 0).k(3.2f, HEAD, 0, 0, 0)
				.k(0, RIGHT_ARM, 0, 0, 0).k(1.0f, RIGHT_ARM, 0, 0, 3).k(1.3f, RIGHT_ARM, -104, -6, 0).k(1.45f, RIGHT_ARM, -92, -6, 0).k(2.8f, RIGHT_ARM, -92, -6, 0).k(3.2f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(1.3f, LEFT_ARM, 22, 0, -10).k(2.8f, LEFT_ARM, 22, 0, -10).k(3.2f, LEFT_ARM, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(1.0f, BODY, 0, 0, 0).k(1.35f, BODY, 9, -6, 0).k(2.8f, BODY, 9, -6, 0).k(3.2f, BODY, 0, 0, 0)
				.k(0, RIGHT_LEG, 0, 0, 0).k(1.3f, RIGHT_LEG, -16, 0, 2).k(2.8f, RIGHT_LEG, -16, 0, 2).k(3.2f, RIGHT_LEG, 0, 0, 0)
				.k(0, LEFT_LEG, 0, 0, 0).k(1.3f, LEFT_LEG, 10, 0, -2).k(2.8f, LEFT_LEG, 10, 0, -2).k(3.2f, LEFT_LEG, 0, 0, 0));
		add("facepalm", 3.0f, false, anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.5f, RIGHT_ARM, -128, -6, 0).k(2.5f, RIGHT_ARM, -128, -6, 0).k(3.0f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.5f, LEFT_ARM, 4, 0, -4).k(2.5f, LEFT_ARM, 4, 0, -4).k(3.0f, LEFT_ARM, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.5f, HEAD, 24, 0, 0).k(2.5f, HEAD, 24, 0, 0).k(3.0f, HEAD, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(0.6f, BODY, 10, 0, 0).k(2.5f, BODY, 10, 0, 0).k(3.0f, BODY, 0, 0, 0)
				.plus((t, o) -> add(o, HEAD, 0, sin((t - 0.6f) * 300) * 9 * window(t, 0.6f, 2.4f), 0)));
		// hand at the chin, then the idea comes
		add("think", 3.6f, false, anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.5f, RIGHT_ARM, -90, -45, 0).k(2.5f, RIGHT_ARM, -90, -45, 0).k(2.85f, RIGHT_ARM, -165, 0, -18).k(3.2f, RIGHT_ARM, -165, 0, -18).k(3.6f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.5f, LEFT_ARM, -40, 40, 0).k(2.5f, LEFT_ARM, -40, 40, 0).k(2.85f, LEFT_ARM, 0, 0, -6).k(3.6f, LEFT_ARM, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.5f, HEAD, 12, 0, 8).k(2.5f, HEAD, 12, 0, 8).k(2.85f, HEAD, -14, 0, 0).k(3.2f, HEAD, -14, 0, 0).k(3.6f, HEAD, 0, 0, 0)
				.k(0, ROOT, 0, 0, 0).k(0.5f, ROOT, 0, 0, 2).k(2.5f, ROOT, 0, 0, 2).k(2.85f, ROOT, 0, 0, 0)
				.k(0, MOVE, 0, 0, 0).k(2.5f, MOVE, 0, 0, 0).k(2.75f, MOVE, 0, -1.5f, 0).k(3.0f, MOVE, 0, 0, 0)
				.plus((t, o) -> add(o, HEAD, 0, sin(t * 120) * 10 * window(t, 0.5f, 2.5f), 0)));
		// punching a tree like on day one
		add("woodpunch", 0.8f, true, anim()
				.k(0, RIGHT_ARM, -70, -10, 0).k(0.22f, RIGHT_ARM, -98, -4, 0).k(0.4f, RIGHT_ARM, -70, -10, 0).k(0.8f, RIGHT_ARM, -70, -10, 0)
				.k(0, BODY, 0, 8, 0).k(0.22f, BODY, 4, -14, 0).k(0.4f, BODY, 0, 8, 0).k(0.8f, BODY, 0, 8, 0)
				.k(0, LEFT_ARM, -55, 22, 0).k(0.8f, LEFT_ARM, -55, 22, 0)
				.k(0, HEAD, 10, 0, 0).k(0.8f, HEAD, 10, 0, 0)
				.k(0, RIGHT_LEG, 10, 0, 2).k(0.8f, RIGHT_LEG, 10, 0, 2)
				.k(0, LEFT_LEG, -12, 0, -2).k(0.8f, LEFT_LEG, -12, 0, -2));
		add("mine", 1.2f, true, "minecraft:diamond_pickaxe", anim()
				.k(0, RIGHT_ARM, -165, -8, 0).k(0.32f, RIGHT_ARM, -45, -8, 0).k(0.5f, RIGHT_ARM, -50, -8, 0).k(1.2f, RIGHT_ARM, -165, -8, 0)
				.k(0, BODY, -6, 0, 0).k(0.32f, BODY, 16, 0, 0).k(0.5f, BODY, 14, 0, 0).k(1.2f, BODY, -6, 0, 0)
				.k(0, HEAD, -6, 0, 0).k(0.32f, HEAD, 18, 0, 0).k(1.2f, HEAD, -6, 0, 0)
				.k(0, LEFT_ARM, -40, 25, 0).k(0.32f, LEFT_ARM, -30, 20, 0).k(1.2f, LEFT_ARM, -40, 25, 0)
				.k(0, RIGHT_LEG, 8, 0, 3).k(1.2f, RIGHT_LEG, 8, 0, 3)
				.k(0, LEFT_LEG, -8, 0, -3).k(1.2f, LEFT_LEG, -8, 0, -3));
		// "Diamonds to you!" – a small bow, then the diamond is offered
		add("diamonds", 3.0f, false, "minecraft:diamond", anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.6f, RIGHT_ARM, -82, -12, 0).k(2.4f, RIGHT_ARM, -82, -12, 0).k(3.0f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.6f, LEFT_ARM, 24, 0, -6).k(2.4f, LEFT_ARM, 24, 0, -6).k(3.0f, LEFT_ARM, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(0.6f, BODY, 14, 0, 0).k(1.1f, BODY, 2, 0, 0).k(2.4f, BODY, 2, 0, 0).k(3.0f, BODY, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.6f, HEAD, 16, 0, 0).k(1.1f, HEAD, 4, 0, 6).k(2.4f, HEAD, 4, 0, 6).k(3.0f, HEAD, 0, 0, 0)
				.k(0, RIGHT_LEG, 0, 0, 0).k(0.6f, RIGHT_LEG, -10, 0, 0).k(2.4f, RIGHT_LEG, -10, 0, 0).k(3.0f, RIGHT_LEG, 0, 0, 0)
				.plus((t, o) -> add(o, RIGHT_ARM, sin(t * 360) * 4 * window(t, 1.1f, 2.4f), 0, 0)));
		// floating, turning and flailing like a ghast
		add("ghast_dance", 3.2f, true, anim().plus((t, o) -> {
			float s = sin(t * 225);
			float f = sin(t * 450);
			set(o, MOVE, 0, -2.5f - sin(t * 225) * 1.2f, 0);
			set(o, ROOT, 0, t / 3.2f * 360, 0);
			set(o, RIGHT_ARM, -40 + s * 45, 0, 35 + f * 22);
			set(o, LEFT_ARM, -40 - s * 45, 0, -35 - f * 22);
			set(o, RIGHT_LEG, 18 + s * 20, 0, 6 + f * 4);
			set(o, LEFT_LEG, 18 - s * 20, 0, -6 - f * 4);
			set(o, BODY, 6 + s * 5, 0, f * 4);
			set(o, HEAD, -8 + s * 8, s * 12, 0);
		}));
		// startled by a creeper: a jump back, arms up, looking around nervously
		add("creeper_scare", 2.6f, false, anim()
				.k(0, MOVE, 0, 0, 0).k(0.18f, MOVE, 0, -3, 1.5f).k(0.4f, MOVE, 0, 0, 3).k(2.2f, MOVE, 0, 0, 3).k(2.6f, MOVE, 0, 0, 0)
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.18f, RIGHT_ARM, -155, 0, -32).k(1.9f, RIGHT_ARM, -150, 0, -30).k(2.6f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.18f, LEFT_ARM, -155, 0, 32).k(1.9f, LEFT_ARM, -150, 0, 30).k(2.6f, LEFT_ARM, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(0.18f, BODY, -14, 0, 0).k(0.5f, BODY, -8, 0, 0).k(1.9f, BODY, -6, 0, 0).k(2.6f, BODY, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.18f, HEAD, -18, 0, 0).k(0.7f, HEAD, -8, -30, 0).k(1.2f, HEAD, -8, 30, 0).k(1.7f, HEAD, -4, 0, 0).k(2.6f, HEAD, 0, 0, 0)
				.k(0, RIGHT_LEG, 0, 0, 0).k(0.18f, RIGHT_LEG, 12, 0, 6).k(0.4f, RIGHT_LEG, 4, 0, 3).k(2.2f, RIGHT_LEG, 4, 0, 3).k(2.6f, RIGHT_LEG, 0, 0, 0)
				.k(0, LEFT_LEG, 0, 0, 0).k(0.18f, LEFT_LEG, -10, 0, -6).k(0.4f, LEFT_LEG, -4, 0, -3).k(2.2f, LEFT_LEG, -4, 0, -3).k(2.6f, LEFT_LEG, 0, 0, 0)
				.plus((t, o) -> {
					float shake = sin(t * 2400) * 4 * window(t, 0.2f, 1.9f);
					add(o, RIGHT_ARM, shake, 0, 0);
					add(o, LEFT_ARM, -shake, 0, 0);
				}));
		// a deep, polite bow: one hand at the belly, the other behind the back, one foot back
		add("bow", 2.8f, false, anim()
				.k(0, BODY, 0, 0, 0).k(0.4f, BODY, 0, 0, 0).k(1.0f, BODY, 38, 0, 0).k(1.8f, BODY, 38, 0, 0).k(2.4f, BODY, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(1.0f, HEAD, 12, 0, 0).k(1.8f, HEAD, 12, 0, 0).k(2.4f, HEAD, 0, 0, 0)
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.4f, RIGHT_ARM, -50, -38, 0).k(1.8f, RIGHT_ARM, -50, -38, 0).k(2.4f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.4f, LEFT_ARM, 32, 18, -4).k(1.8f, LEFT_ARM, 32, 18, -4).k(2.4f, LEFT_ARM, 0, 0, 0)
				.k(0, RIGHT_LEG, 0, 0, 0).k(0.4f, RIGHT_LEG, 16, 0, 0).k(1.8f, RIGHT_LEG, 16, 0, 0).k(2.4f, RIGHT_LEG, 0, 0, 0)
				.k(0, LEFT_LEG, 0, 0, 0).k(1.0f, LEFT_LEG, -6, 0, 0).k(1.8f, LEFT_LEG, -6, 0, 0).k(2.4f, LEFT_LEG, 0, 0, 0));
		add("shrug", 2.0f, false, anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.45f, RIGHT_ARM, -28, 0, 45).k(1.4f, RIGHT_ARM, -28, 0, 45).k(2.0f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.45f, LEFT_ARM, -28, 0, -45).k(1.4f, LEFT_ARM, -28, 0, -45).k(2.0f, LEFT_ARM, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.45f, HEAD, 4, 0, 14).k(1.4f, HEAD, 4, 0, 14).k(2.0f, HEAD, 0, 0, 0)
				.k(0, MOVE, 0, 0, 0).k(0.35f, MOVE, 0, -0.8f, 0).k(0.6f, MOVE, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(0.45f, BODY, -4, 0, -3).k(1.4f, BODY, -4, 0, -3).k(2.0f, BODY, 0, 0, 0));
		// a big sip: the bottle comes to the mouth, head and body lean back, a happy shake at the end
		add("potion", 3.4f, false, "minecraft:potion", anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.5f, RIGHT_ARM, -95, -46, 10).k(0.9f, RIGHT_ARM, -107, -46, 10).k(2.2f, RIGHT_ARM, -107, -46, 10).k(2.6f, RIGHT_ARM, -60, -20, 0).k(3.4f, RIGHT_ARM, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.9f, HEAD, -10, 0, 0).k(2.2f, HEAD, -10, 0, 0).k(2.6f, HEAD, 0, 0, 0).k(3.4f, HEAD, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(0.9f, BODY, -10, 0, 0).k(2.2f, BODY, -10, 0, 0).k(2.6f, BODY, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.9f, LEFT_ARM, 0, 0, -10).k(2.6f, LEFT_ARM, 0, 0, -10).k(3.4f, LEFT_ARM, 0, 0, 0)
				.plus((t, o) -> add(o, HEAD, 0, sin((t - 2.6f) * 900) * 10 * window(t, 2.6f, 3.2f), 0)));
		add("cookie", 3.0f, false, "minecraft:cookie", anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.5f, RIGHT_ARM, -94, -49, 0).k(2.5f, RIGHT_ARM, -94, -49, 0).k(3.0f, RIGHT_ARM, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.5f, HEAD, 6, 0, 0).k(2.5f, HEAD, 6, 0, 0).k(3.0f, HEAD, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.5f, LEFT_ARM, -30, 30, 0).k(2.5f, LEFT_ARM, -30, 30, 0).k(3.0f, LEFT_ARM, 0, 0, 0)
				.plus((t, o) -> {
					float env = window(t, 0.5f, 2.5f);
					add(o, RIGHT_ARM, sin(t * 540) * 5 * env, 0, 0);
					add(o, HEAD, sin(t * 720) * 5 * env, 0, 0);
					add(o, BODY, Math.abs(sin(t * 360)) * 3 * env, 0, 0);
				}));
		// the banner swings in big arcs, the whole body goes along
		add("banner", 1.6f, true, "minecraft:red_banner", anim()
				.k(0, RIGHT_ARM, -172, 0, -10).k(0.8f, RIGHT_ARM, -150, 0, -40).k(1.6f, RIGHT_ARM, -172, 0, -10)
				.k(0, BODY, 0, 0, 5).k(0.8f, BODY, 0, 6, -3).k(1.6f, BODY, 0, 0, 5)
				.k(0, HEAD, -12, 0, 0).k(0.8f, HEAD, -6, -10, 0).k(1.6f, HEAD, -12, 0, 0)
				.k(0, LEFT_ARM, 0, 0, -12).k(1.6f, LEFT_ARM, 0, 0, -12)
				.k(0, RIGHT_LEG, 0, 0, 4).k(1.6f, RIGHT_LEG, 0, 0, 4)
				.k(0, LEFT_LEG, 0, 0, -4).k(1.6f, LEFT_LEG, 0, 0, -4));
		// the spyglass is held to the eye (like vanilla) while the head looks around
		add("spyglass", 4.2f, false, "minecraft:spyglass", anim()
				.k(0, HEAD, 0, 0, 0).k(0.5f, HEAD, -6, 0, 0).k(1.3f, HEAD, -10, -20, 0).k(2.3f, HEAD, -4, 28, 0).k(3.1f, HEAD, -8, 0, 0).k(3.7f, HEAD, -8, 0, 0).k(4.2f, HEAD, 0, 0, 0)
				.k(0, BODY, 0, 0, 0).k(0.5f, BODY, 5, 0, 0).k(3.7f, BODY, 5, 0, 0).k(4.2f, BODY, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.5f, LEFT_ARM, 6, 0, -6).k(3.7f, LEFT_ARM, 6, 0, -6).k(4.2f, LEFT_ARM, 0, 0, 0)
				.plus((t, o) -> {
					// like vanilla's spyglass arm (head pitch − 110°), a little closer to the face so the hand stays out of the head
					float lift = window(t, 0.0f, 4.2f, 0.45f);
					set(o, RIGHT_ARM, (o[HEAD] - 110) * lift, (o[HEAD + 1] - 6) * lift, 0);
				}));
		// disco: arms point up and down in turn, hips sway, feet step, a bounce on every beat
		add("dance", 2.0f, true, anim().plus((t, o) -> {
			float beat = Math.abs(sin(t * 360));
			float side = sin(t * 180);
			float a = smooth(Math.max(0, Math.min(1, side * 1.5f + 0.5f)));
			set(o, RIGHT_ARM, lerp(-30, -165, a), lerp(30, 0, a), lerp(10, -28, a));
			set(o, LEFT_ARM, lerp(-165, -30, a), lerp(0, -30, a), lerp(28, -10, a));
			set(o, BODY, beat * 4, side * 14, 0);
			set(o, ROOT, 0, 0, side * 4);
			set(o, MOVE, 0, -beat * 1.2f, 0);
			set(o, HEAD, -6 + beat * 8, -side * 14, side * 6);
			set(o, RIGHT_LEG, -side * 18, 0, 4);
			set(o, LEFT_LEG, side * 18, 0, -4);
		}));
		// bending with laughter: forward, back, holding the belly
		add("laugh", 2.8f, false, anim()
				.k(0, BODY, 0, 0, 0).k(0.4f, BODY, -10, 0, 0).k(0.9f, BODY, 16, 0, 0).k(1.4f, BODY, -6, 0, 0).k(1.9f, BODY, 16, 0, 0).k(2.4f, BODY, 2, 0, 0).k(2.8f, BODY, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.4f, HEAD, -18, 0, 0).k(0.9f, HEAD, 4, 0, 0).k(1.4f, HEAD, -16, 0, 0).k(1.9f, HEAD, 4, 0, 0).k(2.8f, HEAD, 0, 0, 0)
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.4f, RIGHT_ARM, -36, -26, 0).k(2.4f, RIGHT_ARM, -36, -26, 0).k(2.8f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.4f, LEFT_ARM, -10, 0, -14).k(0.9f, LEFT_ARM, -30, 0, -6).k(1.4f, LEFT_ARM, -10, 0, -14).k(1.9f, LEFT_ARM, -30, 0, -6).k(2.8f, LEFT_ARM, 0, 0, 0)
				.plus((t, o) -> add(o, BODY, sin(t * 1800) * 2.5f * window(t, 0.3f, 2.4f), 0, 0)));
		// hands form a heart above the head, swaying side to side
		add("heart", 3.0f, false, anim()
				.k(0, RIGHT_ARM, 0, 0, 0).k(0.45f, RIGHT_ARM, -150, -5, 0).k(2.5f, RIGHT_ARM, -150, -5, 0).k(3.0f, RIGHT_ARM, 0, 0, 0)
				.k(0, LEFT_ARM, 0, 0, 0).k(0.45f, LEFT_ARM, -150, 5, 0).k(2.5f, LEFT_ARM, -150, 5, 0).k(3.0f, LEFT_ARM, 0, 0, 0)
				.k(0, HEAD, 0, 0, 0).k(0.45f, HEAD, -6, 0, 0).k(2.5f, HEAD, -6, 0, 0).k(3.0f, HEAD, 0, 0, 0)
				.plus((t, o) -> {
					float env = window(t, 0.45f, 2.5f);
					float sway = sin((t - 0.45f) * 270) * env;
					add(o, ROOT, 0, 0, sway * 5);
					add(o, HEAD, 0, 0, sway * 6);
					add(o, RIGHT_ARM, Math.abs(sin(t * 540)) * 4 * env, 0, 0);
					add(o, LEFT_ARM, Math.abs(sin(t * 540)) * 4 * env, 0, 0);
				}));
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
		if (id == null) {
			return null;
		}
		Emote e = ALL.get(id);
		return e != null ? e : CUSTOM_EMOTES.get(id);
	}

	public static boolean exists(String id) {
		return id != null && (ALL.containsKey(id) || CUSTOM_EMOTES.containsKey(id));
	}

	public static boolean isBuiltIn(String id) {
		return id != null && ALL.containsKey(id);
	}

	// ------------------------------------------------------------------ emotes from the emote editor

	/** Custom emotes known here: the own ones ({@code u_…}) and those other players played ({@code c_…}). */
	private static final Map<String, Emote> CUSTOM_EMOTES = java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Emote> eldest) {
			boolean drop = size() > 2000 && !eldest.getKey().startsWith("u_");
			if (drop) {
				CUSTOM_DATA.remove(eldest.getKey());
			}
			return drop;
		}
	});
	private static final Map<String, CustomEmote> CUSTOM_DATA = new java.util.concurrent.ConcurrentHashMap<>();

	public static void registerCustom(String id, CustomEmote e) {
		CUSTOM_DATA.put(id, e);
		CUSTOM_EMOTES.put(id, e.toEmote(id));
	}

	public static void unregisterCustom(String id) {
		CUSTOM_DATA.remove(id);
		CUSTOM_EMOTES.remove(id);
	}

	/** The editor data of a custom emote, or null for a built-in one. */
	public static CustomEmote custom(String id) {
		return id == null ? null : CUSTOM_DATA.get(id);
	}

	/**
	 * Pose at {@code t} seconds after start: 18 angles (NaN = keep) plus, at index 18, the blend weight
	 * (0 = not playing, 1 = fully posed). Returns null when the emote is over.
	 */
	public static float[] sample(String id, float t) {
		Emote e = get(id);
		float total = e == null ? 0 : length(e);
		if (e == null || t < 0 || t > total) {
			return null;
		}
		float[] out = new float[SIZE + 1];
		java.util.Arrays.fill(out, N);
		float local = e.loop() ? t % e.duration() : t;
		e.pose().at(local, out);
		float w = Math.min(1, Math.min(t / BLEND, (total - t) / BLEND));
		out[SIZE] = Math.max(0, Math.min(1, w));
		return out;
	}

	/** Every emote has a fixed length: repeating ones play their cycle a few times (about 6 s), then end. */
	public static float length(Emote e) {
		return e.loop() ? e.duration() * cycles(e) : e.duration();
	}

	public static float length(String id) {
		Emote e = get(id);
		return e == null ? 0 : length(e);
	}

	private static int cycles(Emote e) {
		return Math.max(2, Math.round(6f / e.duration()));
	}

	private static Anim anim() {
		return new Anim();
	}

	/**
	 * Keyframe animation: per part a list of keyframes (time in seconds, x/y/z degrees), eased in between,
	 * plus an optional procedural layer on top ({@link #add}). Head, arms and legs are relative to the body,
	 * the body lean turns at the hips and carries head and arms (like Bedrock emotes).
	 */
	private static final class Anim implements Pose {
		private final Map<Integer, List<float[]>> tracks = new LinkedHashMap<>();
		private Pose extra;

		Anim k(float t, int part, float x, float y, float z) {
			tracks.computeIfAbsent(part, p -> new java.util.ArrayList<>()).add(new float[]{t, x, y, z});
			return this;
		}

		Anim plus(Pose p) {
			extra = p;
			return this;
		}

		@Override
		public void at(float t, float[] o) {
			for (var e : tracks.entrySet()) {
				List<float[]> keys = e.getValue();
				float[] a = keys.get(0), b = a;
				for (float[] key : keys) {
					if (key[0] <= t) {
						a = b = key;
					} else {
						b = key;
						break;
					}
				}
				float f = b == a || b[0] <= a[0] ? 0 : smooth((t - a[0]) / (b[0] - a[0]));
				for (int c = 0; c < 3; c++) {
					o[e.getKey() + c] = lerp(a[c + 1], b[c + 1], f);
				}
			}
			if (extra != null) {
				extra.at(t, o);
			}
		}
	}

	/** Adds to a part's pose (an unset value counts as 0). */
	private static void add(float[] o, int part, float x, float y, float z) {
		float[] d = {x, y, z};
		for (int c = 0; c < 3; c++) {
			o[part + c] = (Float.isNaN(o[part + c]) ? 0 : o[part + c]) + d[c];
		}
	}

	/** 1 inside [from, to], easing in and out over 0.25 s at the edges. */
	private static float window(float t, float from, float to) {
		return window(t, from, to, 0.25f);
	}

	private static float window(float t, float from, float to, float edge) {
		float in = Math.max(0, Math.min(1, (t - from) / edge + 1));
		float out = Math.max(0, Math.min(1, (to - t) / edge + 1));
		return smooth(Math.min(in, out));
	}

	private static float smooth(float f) {
		f = Math.max(0, Math.min(1, f));
		return f * f * (3 - 2 * f);
	}

	private static float lerp(float a, float b, float f) {
		return a + (b - a) * f;
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
