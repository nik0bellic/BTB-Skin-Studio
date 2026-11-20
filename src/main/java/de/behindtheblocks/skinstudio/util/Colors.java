package de.behindtheblocks.skinstudio.util;

import java.util.Locale;

/** ARGB color helpers. Colors are written as {@code #RRGGBB} or {@code #AARRGGBB}. */
public final class Colors {
	private Colors() {
	}

	public static int parse(String value) {
		String v = value.trim().toLowerCase(Locale.ROOT);
		if (v.startsWith("#")) {
			v = v.substring(1);
		} else if (v.startsWith("0x")) {
			v = v.substring(2);
		}
		try {
			if (v.length() == 6) {
				return 0xFF000000 | Integer.parseUnsignedInt(v, 16);
			}
			if (v.length() == 8) {
				return Integer.parseUnsignedInt(v, 16);
			}
		} catch (NumberFormatException ignored) {
			// fall through
		}
		throw new FormatException(Tr.t("btb.t.ungueltige_farbe") + value + Tr.t("btb.t.erwartet_rrggbb"));
	}

	public static String format(int argb) {
		if ((argb >>> 24) == 0xFF) {
			return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
		}
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	public static int multiply(int a, int b) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24);
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24);
		return ((aa * ba / 255) << 24) | ((ar * br / 255) << 16) | ((ag * bg / 255) << 8) | (ab * bb / 255);
	}

	/** HSV (h 0..360, s and v 0..1) → opaque ARGB. */
	public static int hsv(float h, float s, float v) {
		float c = v * s;
		float hp = (h % 360f + 360f) % 360f / 60f;
		float x = c * (1 - Math.abs(hp % 2 - 1));
		float r = 0, g = 0, b = 0;
		if (hp < 1) { r = c; g = x; } else if (hp < 2) { r = x; g = c; } else if (hp < 3) { g = c; b = x; }
		else if (hp < 4) { g = x; b = c; } else if (hp < 5) { r = x; b = c; } else { r = c; b = x; }
		float m = v - c;
		return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
	}

	/** ARGB → {h, s, v}. */
	public static float[] toHsv(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == r) h = 60 * (((g - b) / d) % 6);
			else if (max == g) h = 60 * ((b - r) / d + 2);
			else h = 60 * ((r - g) / d + 4);
		}
		return new float[]{(h + 360) % 360, max == 0 ? 0 : d / max, max};
	}
}
