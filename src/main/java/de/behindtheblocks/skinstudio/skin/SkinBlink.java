package de.behindtheblocks.skinstudio.skin;

import java.awt.image.BufferedImage;

/**
 * Makes a "closed eyes" copy of a skin for blinking. The eyes are found on the face (front of the head,
 * texture 8..15 / 8..15, hat layer 40..47 / 8..15): the rows 2–6 that hold bright, unsaturated pixels (eye
 * whites) on both halves of the face. If a skin has no such whites, row 4 is used, where the eyes of the
 * standard skins sit. Every pixel of those rows that clearly differs from the skin above it becomes lid:
 * the colour from above, the lowest eye row a little darker as lash line.
 */
public final class SkinBlink {
	private static final int FACE_X = 8, FACE_Y = 8, HAT_X = 40;

	private SkinBlink() {
	}

	/**
	 * Top row of the eyes on the face (0–7, Steve and Alex: 4), or -1 if the eyes cannot be told apart from
	 * the skin. Used to move glasses and face parts to the eyes of skins whose eyes sit higher or lower.
	 */
	public static int eyeRow(BufferedImage skin) {
		if (skin.getWidth() < 64 || skin.getHeight() < 32) {
			return -1;
		}
		int s = skin.getWidth() / 64;
		int[][] face = new int[8][8];
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int base = px(skin, s, FACE_X + x, FACE_Y + y);
				int hat = px(skin, s, HAT_X + x, FACE_Y + y);
				face[y][x] = (hat >>> 24) > 200 ? hat : base;
			}
		}
		for (int y = 2; y <= 6; y++) {
			if (hasWhite(face[y], 0, 4) && hasWhite(face[y], 4, 8)) {
				return y;
			}
		}
		int tone = skinTone(face, 2, 3, 4, 5, 6, 7);
		for (int y = 3; y <= 5; y++) {
			if (eyeLike(face[y], tone, 0, 4) && eyeLike(face[y], tone, 4, 8)) {
				return y;
			}
		}
		return -1;
	}

	/** Returns the skin with closed eyes, or null if no eyes could be found. */
	public static BufferedImage close(BufferedImage skin) {
		if (skin.getWidth() < 64 || skin.getHeight() < 32) {
			return null;
		}
		int s = skin.getWidth() / 64;
		int[][] face = new int[8][8];
		boolean[][] fromHat = new boolean[8][8];
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int base = px(skin, s, FACE_X + x, FACE_Y + y);
				int hat = px(skin, s, HAT_X + x, FACE_Y + y);
				boolean useHat = (hat >>> 24) > 200;
				face[y][x] = useHat ? hat : base;
				fromHat[y][x] = useHat;
			}
		}
		// rough skin tone of the whole face (most common colour) to tell eye pixels from skin
		int faceTone = skinTone(face, 2, 3, 4, 5, 6, 7);
		int top = -1, bottom = -1;
		for (int y = 2; y <= 6; y++) {
			if (hasWhite(face[y], 0, 4) && hasWhite(face[y], 4, 8)) {
				if (top < 0) {
					top = y;
				}
				bottom = y;
			} else if (top >= 0) {
				break;
			}
		}
		if (top < 0) {
			// no eye whites (dark or coloured eyes): only a clearly mirrored pair counts as eyes
			for (int y = 3; y <= 5 && top < 0; y++) {
				if (eyeLike(face[y], faceTone, 1, 4) && eyeLike(face[y], faceTone, 4, 7) && mirrored(face[y], faceTone)) {
					top = bottom = y;
				}
			}
		}
		if (top < 0) {
			return null; // not sure where the eyes are: better no blink than a wrong one
		}
		// hair colour (top rows of the face) – hair strands next to the eyes are never closed
		int hairTone = skinTone(face, 0, 1);
		// eyes several pixels tall: an iris row above the whites (same columns, not hair) belongs to the eye
		while (top > 2 && bottom - top < 2 && irisAbove(face[top - 1], face[top], faceTone, hairTone)) {
			top--;
		}
		// eyes several pixels tall: iris or pupil rows below the whites belong to the eye too (at most 3 rows)
		while (bottom < 6 && bottom - top < 2 && eyeLike(face[bottom + 1], faceTone, 0, 4) && eyeLike(face[bottom + 1], faceTone, 4, 8)
				&& sameColumns(face[bottom], face[bottom + 1], faceTone)) {
			bottom++;
		}
		if (top < 1) {
			return null;
		}
		BufferedImage out = new BufferedImage(skin.getWidth(), skin.getHeight(), BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(skin, 0, 0, null);
		// skin tone: the most common colour of the cheeks right below the eyes (above them is often hair)
		int tone = skinTone(face, Math.min(7, bottom + 1), Math.min(7, bottom + 2));
		boolean changed = false;
		int changedPixels = 0;
		for (int x = 1; x < 7; x++) { // the outer columns are usually hair or sideburns
			int above = face[top - 1][x];
			boolean aboveIsSkin = (above >>> 24) >= 200 && dist(above, tone) < 40;
			// the pixel above keeps the shading when it is skin; under hair or eyebrows the plain skin tone is used
			int lid = aboveIsSkin ? above : tone;
			for (int y = top; y <= bottom; y++) {
				int px = face[y][x];
				if (dist(px, tone) < 60) {
					continue; // skin between and beside the eyes
				}
				if (dist(px, hairTone) < 50 && !isWhite(px)) {
					continue; // hair strand hanging next to the eye
				}
				if (!aboveIsSkin && dist(px, above) < 40 && !isWhite(px)) {
					continue; // hair or fringe hanging down beside the eyes
				}
				int c = y == bottom && bottom > top ? shade(lid, 0.8f) : shade(lid, 0.93f);
				set(out, s, (fromHat[y][x] ? HAT_X : FACE_X) + x, FACE_Y + y, c);
				changed = true;
				changedPixels++;
			}
		}
		// eyes are a few pixels; a large change means a mask, visor or glasses – leave such skins alone
		return changed && changedPixels <= 14 ? out : null;
	}

	private static int skinTone(int[][] face, int... rows) {
		java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
		for (int r : rows) {
			for (int x = 0; x < 8; x++) {
				int c = face[r][x];
				if ((c >>> 24) >= 200) {
					count.merge(c | 0xFF000000, 1, Integer::sum);
				}
			}
		}
		int best = 0xFFC69C7A, n = 0;
		for (var e : count.entrySet()) {
			if (e.getValue() > n) {
				n = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/** Every eye-white column of {@code row} has a coloured, non-skin, non-hair pixel directly above it. */
	private static boolean irisAbove(int[] above, int[] row, int tone, int hair) {
		int found = 0;
		for (int x = 1; x < 7; x++) {
			if (!isWhite(row[x])) {
				continue;
			}
			int a = above[x];
			if ((a >>> 24) < 200 || dist(a, tone) < 60 || dist(a, hair) < 50) {
				return false;
			}
			found++;
		}
		return found >= 2;
	}

	/** Left and right half of the row look alike (eyes are mirrored, a fringe or scar usually is not). */
	private static boolean mirrored(int[] row, int tone) {
		return pairMatch(row, tone, true) || pairMatch(row, tone, false);
	}

	/** Mirrored (x ↔ 7 − x) or copied (x ↔ x + 4) halves match on every non-skin pixel. */
	private static boolean pairMatch(int[] row, int tone, boolean mirror) {
		int pairs = 0, match = 0;
		for (int x = 1; x < 4; x++) {
			int l = row[x], r = mirror ? row[7 - x] : row[x + 4];
			boolean le = dist(l, tone) >= 90, re = dist(r, tone) >= 90;
			if (le || re) {
				pairs++;
				if (le && re && dist(l, r) < 60) {
					match++;
				}
			}
		}
		return pairs > 0 && match == pairs;
	}

	/** True if the row has a pixel in [from, to) that clearly is not skin (eye white, iris, pupil). */
	private static boolean eyeLike(int[] row, int tone, int from, int to) {
		for (int x = from; x < to; x++) {
			if ((row[x] >>> 24) > 200 && dist(row[x], tone) >= 90) {
				return true;
			}
		}
		return false;
	}

	/** The non-skin pixels of the lower row sit under non-skin pixels of the upper row (same eye, not the mouth). */
	private static boolean sameColumns(int[] upper, int[] lower, int tone) {
		int below = 0, total = 0;
		for (int x = 0; x < 8; x++) {
			if ((lower[x] >>> 24) > 200 && dist(lower[x], tone) >= 90) {
				total++;
				if (dist(upper[x], tone) >= 60) {
					below++;
				}
			}
		}
		return total > 0 && below * 2 >= total;
	}

	private static boolean hasWhite(int[] row, int from, int to) {
		for (int x = from; x < to; x++) {
			if (isWhite(row[x])) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWhite(int c) {
		int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		return (c >>> 24) > 200 && min >= 170 && max - min <= 45;
	}

	private static int dist(int a, int b) {
		return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
	}

	private static int shade(int c, float f) {
		int r = (int) (((c >> 16) & 0xFF) * f), g = (int) (((c >> 8) & 0xFF) * f), b = (int) ((c & 0xFF) * f);
		return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int px(BufferedImage img, int s, int x, int y) {
		return img.getRGB(x * s, y * s);
	}

	private static void set(BufferedImage img, int s, int x, int y, int c) {
		for (int i = 0; i < s; i++) {
			for (int j = 0; j < s; j++) {
				img.setRGB(x * s + i, y * s + j, c);
			}
		}
	}
}
