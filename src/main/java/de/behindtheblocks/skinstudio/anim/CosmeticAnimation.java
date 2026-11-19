package de.behindtheblocks.skinstudio.anim;

import de.behindtheblocks.skinstudio.molang.Molang;
import de.behindtheblocks.skinstudio.molang.MolangContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One Bedrock-style animation: per-bone rotation/position/scale channels (constant, Molang or keyframes). */
public final class CosmeticAnimation {
	public enum Loop {
		ONCE, LOOP, HOLD
	}

	public enum Lerp {
		LINEAR, CATMULLROM, STEP
	}

	public final String name;
	public Loop loop = Loop.LOOP;
	/** Seconds; 0 = derived from the last keyframe (or endless for pure Molang). */
	public float length;
	public final Map<String, BoneTrack> bones = new LinkedHashMap<>();

	public CosmeticAnimation(String name) {
		this.name = name;
	}

	public static final class BoneTrack {
		public Channel rotation;
		public Channel position;
		public Channel scale;
	}

	public record Keyframe(float time, Molang.Expr[] pre, Molang.Expr[] post, Lerp lerp) {
	}

	/** Either a single (possibly Molang) vector or a list of keyframes. */
	public static final class Channel {
		public final Molang.Expr[] constant;
		public final List<Keyframe> keys;

		private Channel(Molang.Expr[] constant, List<Keyframe> keys) {
			this.constant = constant;
			this.keys = keys;
		}

		public static Channel constant(Molang.Expr[] v) {
			return new Channel(v, null);
		}

		public static Channel keyframes(List<Keyframe> keys) {
			List<Keyframe> sorted = new ArrayList<>(keys);
			sorted.sort((a, b) -> Float.compare(a.time(), b.time()));
			return new Channel(null, sorted);
		}

		public float lastTime() {
			return keys == null || keys.isEmpty() ? 0 : keys.getLast().time();
		}

		public void sample(float t, MolangContext ctx, float[] out) {
			if (constant != null) {
				for (int i = 0; i < 3; i++) {
					out[i] = constant[i].eval(ctx);
				}
				return;
			}
			if (keys.isEmpty()) {
				out[0] = out[1] = out[2] = 0;
				return;
			}
			int n = keys.size();
			if (t <= keys.getFirst().time()) {
				eval(keys.getFirst().pre(), ctx, out);
				return;
			}
			if (t >= keys.getLast().time()) {
				eval(keys.getLast().post(), ctx, out);
				return;
			}
			int i = 0;
			while (i < n - 2 && keys.get(i + 1).time() <= t) {
				i++;
			}
			Keyframe a = keys.get(i), b = keys.get(i + 1);
			float span = b.time() - a.time();
			float f = span <= 0 ? 1 : (t - a.time()) / span;
			float[] va = new float[3], vb = new float[3];
			eval(a.post(), ctx, va);
			eval(b.pre(), ctx, vb);
			if (a.lerp() == Lerp.STEP) {
				System.arraycopy(va, 0, out, 0, 3);
				return;
			}
			if (a.lerp() == Lerp.CATMULLROM || b.lerp() == Lerp.CATMULLROM) {
				float[] p0 = new float[3], p3 = new float[3];
				eval(i > 0 ? keys.get(i - 1).post() : a.post(), ctx, p0);
				eval(i + 2 < n ? keys.get(i + 2).pre() : b.pre(), ctx, p3);
				for (int k = 0; k < 3; k++) {
					out[k] = catmull(p0[k], va[k], vb[k], p3[k], f);
				}
				return;
			}
			for (int k = 0; k < 3; k++) {
				out[k] = va[k] + (vb[k] - va[k]) * f;
			}
		}

		private static void eval(Molang.Expr[] e, MolangContext ctx, float[] out) {
			for (int i = 0; i < 3; i++) {
				out[i] = e[i].eval(ctx);
			}
		}

		private static float catmull(float p0, float p1, float p2, float p3, float t) {
			float t2 = t * t, t3 = t2 * t;
			return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
		}
	}

	public float effectiveLength() {
		if (length > 0) {
			return length;
		}
		float max = 0;
		for (BoneTrack t : bones.values()) {
			for (Channel c : new Channel[]{t.rotation, t.position, t.scale}) {
				if (c != null) {
					max = Math.max(max, c.lastTime());
				}
			}
		}
		return max;
	}

	/** Local animation time for a running clock. */
	public float localTime(float seconds) {
		float len = effectiveLength();
		if (len <= 0) {
			return seconds;
		}
		return switch (loop) {
			case LOOP -> seconds % len;
			case HOLD, ONCE -> Math.min(seconds, len);
		};
	}
}
