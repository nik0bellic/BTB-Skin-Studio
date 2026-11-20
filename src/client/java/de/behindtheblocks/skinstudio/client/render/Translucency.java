package de.behindtheblocks.skinstudio.client.render;

import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which quads of a cosmetic use semi-transparent texture pixels (glass, ghosts, mist). Those are drawn in a
 * second, sorted pass after the opaque ones – otherwise a glass face drawn first hides the parts behind it
 * (struts and plants of the terrarium vanished depending on the camera angle). Computed once per asset.
 */
final class Translucency {
	/** Per bone index: true = translucent quad. Null when the cosmetic has no translucent pixels at all. */
	record Mask(boolean[][] perBone) {
	}

	private static final Map<CosmeticAsset, Mask> CACHE = new WeakHashMap<>();
	private static final Mask NONE = new Mask(null);

	private Translucency() {
	}

	static Mask of(CosmeticAsset asset, BakedModel baked) {
		Mask m = CACHE.get(asset);
		if (m == null) {
			m = compute(asset, baked);
			CACHE.put(asset, m);
		}
		return m.perBone() == null ? null : m;
	}

	private static Mask compute(CosmeticAsset asset, BakedModel baked) {
		try {
			byte[] png = asset.texture();
			if (png == null) {
				return NONE;
			}
			BufferedImage img = de.behindtheblocks.skinstudio.bedrock.Images.read(png, "texture.png");
			List<BakedModel.Bone> bones = new ArrayList<>();
			for (BakedModel.Bone b : baked.roots) {
				collect(b, bones);
			}
			int max = 0;
			for (BakedModel.Bone b : bones) {
				max = Math.max(max, b.index + 1);
			}
			boolean[][] out = new boolean[max][];
			boolean any = false;
			for (BakedModel.Bone b : bones) {
				float[] q = b.quads;
				boolean[] t = new boolean[q.length / BakedModel.QUAD_STRIDE];
				for (int i = 0, n = 0; i < q.length; i += BakedModel.QUAD_STRIDE, n++) {
					t[n] = translucent(img, q, i);
					any |= t[n];
				}
				out[b.index] = t;
			}
			return any ? new Mask(out) : NONE;
		} catch (RuntimeException e) {
			return NONE;
		}
	}

	private static void collect(BakedModel.Bone b, List<BakedModel.Bone> out) {
		out.add(b);
		for (BakedModel.Bone c : b.children) {
			collect(c, out);
		}
	}

	/** Any pixel in the quad's UV rectangle with 0 < alpha < 250. */
	private static boolean translucent(BufferedImage img, float[] q, int i) {
		float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
		for (int v = 0; v < 4; v++) {
			int k = i + v * BakedModel.VERTEX_STRIDE;
			u0 = Math.min(u0, q[k + 3]);
			u1 = Math.max(u1, q[k + 3]);
			v0 = Math.min(v0, q[k + 4]);
			v1 = Math.max(v1, q[k + 4]);
		}
		int w = img.getWidth(), h = img.getHeight();
		int x0 = clamp((int) Math.floor(u0 * w + 1e-3), w), x1 = clamp((int) Math.ceil(u1 * w - 1e-3) - 1, w);
		int y0 = clamp((int) Math.floor(v0 * h + 1e-3), h), y1 = clamp((int) Math.ceil(v1 * h - 1e-3) - 1, h);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				int a = img.getRGB(x, y) >>> 24;
				if (a > 0 && a < 250) {
					return true;
				}
			}
		}
		return false;
	}

	private static int clamp(int v, int max) {
		return Math.max(0, Math.min(max - 1, v));
	}
}
