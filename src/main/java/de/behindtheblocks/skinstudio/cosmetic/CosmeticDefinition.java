package de.behindtheblocks.skinstudio.cosmetic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Metadata of one cosmetic ({@code cosmetic.json}). Model, texture and animations live in separate
 * files next to it; see {@code docs/FORMAT.md}. Mutable so the editor can work on a copy.
 */
public final class CosmeticDefinition {
	public static final int FORMAT = 1;

	public String id;
	public String version = "1.0.0";
	public final Map<String, String> names = new LinkedHashMap<>();
	public final Map<String, String> descriptions = new LinkedHashMap<>();
	public String author = "";
	public CosmeticType type = CosmeticType.HAT;
	public AttachmentPoint attachment = AttachmentPoint.HEAD;
	public Set<CosmeticSlot> slots = EnumSet.noneOf(CosmeticSlot.class);

	public String model = "model.geo.json";
	/** Geometry identifier inside the model file; null = first. */
	public String geometry;
	public String texture = "texture.png";
	public String emissiveTexture;
	public String tintMask;
	public String animationFile;
	/** Animations to play (looped); empty = all animations in the file. */
	public final List<String> animations = new ArrayList<>();
	public String preview;

	public final Transform transform = new Transform();
	public final Tint tint = new Tint();
	public Glow glow;
	public Particles particles;
	/** Vanilla overlay layers to hide while worn: hat, jacket, sleeves, pants. */
	public final Set<String> hideLayers = new java.util.LinkedHashSet<>();
	public final List<String> tags = new ArrayList<>();
	/** Free-form source note for imported content, e.g. "Bedrock: persona_hat (Ordner xyz)". */
	public String source;

	public static final class Transform {
		/** Pixels in Bedrock space (Y up). */
		public float[] offset = new float[3];
		/** Degrees. */
		public float[] rotation = new float[3];
		public float[] scale = {1, 1, 1};
		/** Rotation/scale pivot relative to the attachment anchor (pixels, Bedrock space). */
		public float[] pivot = new float[3];

		public boolean isIdentity() {
			return offset[0] == 0 && offset[1] == 0 && offset[2] == 0 && rotation[0] == 0 && rotation[1] == 0 && rotation[2] == 0
					&& scale[0] == 1 && scale[1] == 1 && scale[2] == 1 && pivot[0] == 0 && pivot[1] == 0 && pivot[2] == 0;
		}

		public void copyFrom(Transform o) {
			offset = o.offset.clone();
			rotation = o.rotation.clone();
			scale = o.scale.clone();
			pivot = o.pivot.clone();
		}
	}

	public static final class Tint {
		public boolean enabled;
		public int defaultColor = 0xFFFFFFFF;
		public final List<Integer> palette = new ArrayList<>();
	}

	/** Purely visual light halo (no world lighting changes). */
	public static final class Glow {
		public int color = 0xFFFFB24A;
		/** Halo radius in pixels. */
		public float size = 6;
		public float intensity = 0.55f;
		/** Bedrock-space position relative to the attachment anchor. */
		public float[] offset = new float[3];
		public boolean flicker = true;
	}

	public static final class Particles {
		/** Minecraft particle id, e.g. minecraft:flame; only simple particle types. */
		public String type = "minecraft:flame";
		/** Particles per second. */
		public float rate = 2;
		public float[] offset = new float[3];
		public float spread = 1;
	}

	public String name(String lang) {
		return localized(names, lang, id);
	}

	public String description(String lang) {
		return localized(descriptions, lang, "");
	}

	private static String localized(Map<String, String> map, String lang, String fallback) {
		if (map.isEmpty()) {
			return fallback;
		}
		String l = lang == null ? "de" : lang.toLowerCase(Locale.ROOT);
		String v = map.get(l);
		if (v == null && l.length() > 2) {
			v = map.get(l.substring(0, 2));
		}
		if (v == null) {
			v = map.get("en");
		}
		if (v == null) {
			v = map.get("de");
		}
		return v == null ? map.values().iterator().next() : v;
	}

	public CosmeticCategory category() {
		return type.category;
	}

	public Set<CosmeticSlot> effectiveSlots() {
		return slots.isEmpty() ? type.defaultSlots() : slots;
	}

	public String namespace() {
		int i = id.indexOf(':');
		return i < 0 ? "custom" : id.substring(0, i);
	}

	public String path() {
		int i = id.indexOf(':');
		return i < 0 ? id : id.substring(i + 1);
	}

	public CosmeticDefinition copy() {
		return CosmeticJson.read(CosmeticJson.write(this));
	}
}
