package de.behindtheblocks.skinstudio.anim;

import de.behindtheblocks.skinstudio.molang.Molang;

import java.util.Locale;

/**
 * Ready-made animations for the editor. They are ordinary Bedrock animations with Molang
 * expressions, so they are saved into {@code animation.json} and work in Blockbench too.
 */
public final class AnimationPresets {
	public enum Preset {
		BOB("Schweben", "Sanftes Auf und Ab"),
		SPIN("Drehen", "Dreht sich langsam um die eigene Achse"),
		SWAY("Wiegen", "Pendelt leicht hin und her"),
		FLAP("Flattern", "Schlägt wie Flügel (für zwei Bones)"),
		PULSE("Pulsieren", "Wird rhythmisch größer und kleiner"),
		FLICKER("Flackern", "Unruhiges Flackern wie eine Flamme"),
		WAG("Wedeln", "Schnelles seitliches Wackeln");

		public final String label;
		public final String description;

		Preset(String label, String description) {
			this.label = label;
			this.description = description;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private AnimationPresets() {
	}

	/**
	 * @param bone      bone to animate (FLAP: the left wing; {@code secondBone} the right one)
	 * @param speed     cycles per second
	 * @param amplitude pixels (BOB), degrees (SWAY/FLAP/WAG), or relative size (PULSE/FLICKER)
	 */
	public static CosmeticAnimation create(Preset preset, String bone, String secondBone, float speed, float amplitude) {
		CosmeticAnimation a = new CosmeticAnimation("animation.btb." + preset.id() + "." + bone);
		a.loop = CosmeticAnimation.Loop.LOOP;
		String t = "query.anim_time * " + fmt(360f * speed);
		CosmeticAnimation.BoneTrack track = new CosmeticAnimation.BoneTrack();
		switch (preset) {
			case BOB -> track.position = vec("0", "math.sin(" + t + ") * " + fmt(amplitude), "0");
			case SPIN -> track.rotation = vec("0", t, "0");
			case SWAY -> track.rotation = vec("0", "0", "math.sin(" + t + ") * " + fmt(amplitude));
			case WAG -> track.rotation = vec("0", "math.sin(" + t + ") * " + fmt(amplitude), "0");
			case PULSE -> {
				String s = "1 + math.sin(" + t + ") * " + fmt(amplitude);
				track.scale = vec(s, s, s);
			}
			case FLICKER -> {
				String s = "1 + (math.sin(" + t + ") * 0.6 + math.sin(" + t + " * 2.7) * 0.4) * " + fmt(amplitude);
				track.scale = vec("1", s, "1");
			}
			case FLAP -> {
				track.rotation = vec("0", "-15 - math.sin(" + t + ") * " + fmt(amplitude), "0");
				if (secondBone != null) {
					CosmeticAnimation.BoneTrack other = new CosmeticAnimation.BoneTrack();
					other.rotation = vec("0", "15 + math.sin(" + t + ") * " + fmt(amplitude), "0");
					a.bones.put(secondBone, other);
				}
			}
		}
		a.bones.put(bone, track);
		return a;
	}

	/** Recovers the preset from an animation name written by {@link #create}, or null. */
	public static Preset presetOf(String animationName) {
		if (!animationName.startsWith("animation.btb.")) {
			return null;
		}
		String rest = animationName.substring("animation.btb.".length());
		int dot = rest.indexOf('.');
		String id = dot < 0 ? rest : rest.substring(0, dot);
		for (Preset p : Preset.values()) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}

	private static CosmeticAnimation.Channel vec(String x, String y, String z) {
		return CosmeticAnimation.Channel.constant(new Molang.Expr[]{Molang.compile(x), Molang.compile(y), Molang.compile(z)});
	}

	private static String fmt(float v) {
		return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
	}
}
