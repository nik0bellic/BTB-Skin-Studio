package de.behindtheblocks.skinstudio.skin;

import java.awt.image.BufferedImage;

/** Front-view figure of a skin (base + outer layer), for the skin list. Output 16×32 px × scale. */
public final class SkinIcon {
	private SkinIcon() {
	}

	public static BufferedImage render(BufferedImage skin, boolean slim, int scale) {
		int s = Math.max(1, skin.getWidth() / 64);
		BufferedImage out = new BufferedImage(16 * scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
		int aw = slim ? 3 : 4;
		// {source u, v, width, height, target x, y} for the front faces (base layer, then outer layer)
		int[][] parts = {
				{8, 8, 8, 8, 4, 0}, {20, 20, 8, 12, 4, 8},
				{44, 20, aw, 12, 4 - aw, 8}, {36, 52, aw, 12, 12, 8},
				{4, 20, 4, 12, 4, 20}, {20, 52, 4, 12, 8, 20},
		};
		int[][] overlay = {
				{40, 8, 8, 8, 4, 0}, {20, 36, 8, 12, 4, 8},
				{44, 36, aw, 12, 4 - aw, 8}, {52, 52, aw, 12, 12, 8},
				{4, 36, 4, 12, 4, 20}, {4, 52, 4, 12, 8, 20},
		};
		for (int[][] layer : new int[][][]{parts, overlay}) {
			for (int[] p : layer) {
				for (int y = 0; y < p[3]; y++) {
					for (int x = 0; x < p[2]; x++) {
						int sx = (p[0] + x) * s, sy = (p[1] + y) * s;
						if (sx >= skin.getWidth() || sy >= skin.getHeight()) {
							continue;
						}
						int c = skin.getRGB(sx, sy);
						if ((c >>> 24) < 16) {
							continue;
						}
						for (int dy = 0; dy < scale; dy++) {
							for (int dx = 0; dx < scale; dx++) {
								int tx = (p[4] + x + (slim && p[4] == 4 - aw ? 0 : 0)) * scale + dx, ty = (p[5] + y) * scale + dy;
								if (tx >= 0 && ty >= 0 && tx < out.getWidth() && ty < out.getHeight()) {
									out.setRGB(tx, ty, c | 0xFF000000);
								}
							}
						}
					}
				}
			}
		}
		return out;
	}
}
