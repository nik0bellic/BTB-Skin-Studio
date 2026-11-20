package de.behindtheblocks.skinstudio.geo;

import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Software renderer for wardrobe icons. Uses the same baked quads and UVs as the in-game renderer, so the
 * icon matches the real cosmetic; a light-grey mannequin part gives context. Plain Java2D, runs off-thread.
 */
public final class PreviewRenderer {
	private PreviewRenderer() {
	}

	/** Projected polygon; zs = per-vertex depth (smaller = nearer), bias pushes the mannequin back. */
	private record Face(float depth, float[] xs, float[] ys, float[] zs, int argb, float bias) {
	}

	public static BufferedImage render(GeoModel model, BufferedImage texture, BufferedImage emissive, AttachmentPoint attachment, int size) {
		BakedModel baked = BakedModel.bake(model);
		boolean back = attachment == AttachmentPoint.BACK;
		float yaw = (float) Math.toRadians(back ? 145 : -35);
		float pitch = (float) Math.toRadians(26);
		List<Face> faces = new ArrayList<>();
		Vector3f light = new Vector3f(-0.4f, -1f, -0.6f).normalize();

		// mannequin context (Java model space, blocks; y down, head pivot at 0)
		for (float[] box : mannequin(attachment)) {
			addBox(faces, box, 0xFFCFD3DA, yaw, pitch, light);
		}
		int mannequinFaces = faces.size();

		Matrix4f root = new Matrix4f();
		for (BakedModel.Bone b : baked.roots) {
			// absolute pivot in Java pixels → blocks, relative to the head pivot (y = 0)
			Matrix4f m = new Matrix4f(root).translate(b.pivotX / 16f, b.pivotY / 16f, b.pivotZ / 16f);
			addBone(faces, b, m, texture, emissive, yaw, pitch, light);
		}
		if (faces.isEmpty()) {
			return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (Face f : faces) {
			for (int i = 0; i < f.xs.length; i++) {
				minX = Math.min(minX, f.xs[i]);
				maxX = Math.max(maxX, f.xs[i]);
				minY = Math.min(minY, f.ys[i]);
				maxY = Math.max(maxY, f.ys[i]);
			}
		}
		int ss = 3;
		int canvas = size * ss;
		float scale = canvas * 0.86f / Math.max(maxX - minX, maxY - minY);
		float ox = canvas / 2f - (minX + maxX) / 2 * scale;
		float oy = canvas / 2f - (minY + maxY) / 2 * scale;
		// z-buffer instead of sorting whole faces: small parts lying on the mannequin are never covered by it
		BufferedImage img = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
		float[] zbuf = new float[canvas * canvas];
		java.util.Arrays.fill(zbuf, Float.MAX_VALUE);
		List<Face> translucent = new ArrayList<>();
		for (int i = 0; i < faces.size(); i++) {
			Face f = faces.get(i);
			float bias = i < mannequinFaces ? 0.004f : 0f;
			if ((f.argb >>> 24) < 250) {
				translucent.add(new Face(f.depth, f.xs, f.ys, f.zs, f.argb, bias));
				continue;
			}
			raster(img, zbuf, f, bias, scale, ox, oy, false);
		}
		translucent.sort((x, y) -> Float.compare(y.depth, x.depth));
		for (Face f : translucent) {
			raster(img, zbuf, f, f.bias, scale, ox, oy, true);
		}
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.drawImage(img, 0, 0, size, size, null);
		g2.dispose();
		return out;
	}

	private static void addBone(List<Face> out, BakedModel.Bone bone, Matrix4f m, BufferedImage tex, BufferedImage emis,
								float yaw, float pitch, Vector3f light) {
		Matrix4f bm = new Matrix4f(m);
		if (bone.rotX != 0 || bone.rotY != 0 || bone.rotZ != 0) {
			bm.rotate(new Quaternionf().rotationZYX(bone.rotZ, bone.rotY, bone.rotX));
		}
		float[] q = bone.quads;
		int tw = tex.getWidth(), th = tex.getHeight();
		Vector3f tmp = new Vector3f();
		for (int i = 0; i < q.length; i += BakedModel.QUAD_STRIDE) {
			float[][] p = new float[4][3];
			float[][] uv = new float[4][2];
			for (int v = 0; v < 4; v++) {
				int k = i + v * BakedModel.VERTEX_STRIDE;
				bm.transformPosition(q[k], q[k + 1], q[k + 2], tmp);
				p[v] = new float[]{tmp.x, tmp.y, tmp.z};
				uv[v] = new float[]{q[k + 3], q[k + 4]};
			}
			Vector3f n = bm.transformDirection(q[i + 20], q[i + 21], q[i + 22], new Vector3f()).normalize();
			float shade = 0.62f + 0.38f * Math.max(0, n.dot(light));
			float du = Math.abs(uv[0][0] - uv[1][0]) * tw, dv = Math.abs(uv[1][1] - uv[2][1]) * th;
			int cols = Math.max(1, Math.min(64, Math.round(du))), rows = Math.max(1, Math.min(64, Math.round(dv)));
			for (int r = 0; r < rows; r++) {
				for (int c = 0; c < cols; c++) {
					float s0 = c / (float) cols, s1 = (c + 1) / (float) cols, t0 = r / (float) rows, t1 = (r + 1) / (float) rows;
					// quad order: 0=(u1,v0) 1=(u0,v0) 2=(u0,v1) 3=(u1,v1); interpolate in (s from vertex 1→0, t from top→bottom)
					float sm = (s0 + s1) / 2, tm = (t0 + t1) / 2;
					float u = lerp(lerp(uv[1][0], uv[0][0], sm), lerp(uv[2][0], uv[3][0], sm), tm);
					float vv = lerp(lerp(uv[1][1], uv[0][1], sm), lerp(uv[2][1], uv[3][1], sm), tm);
					int px = clamp((int) (u * tw), tw), py = clamp((int) (vv * th), th);
					int col = tex.getRGB(px, py);
					boolean glow = false;
					if (emis != null && emis.getWidth() == tw && emis.getHeight() == th) {
						int e = emis.getRGB(px, py);
						if ((e >>> 24) > 40) {
							col = e | 0xFF000000;
							glow = true;
						}
					}
					if ((col >>> 24) < 8) {
						continue;
					}
					float[][] cell = {pt(p, s0, t0), pt(p, s1, t0), pt(p, s1, t1), pt(p, s0, t1)};
					int argb = glow ? col : shadeColor(col, shade);
					project(out, cell, argb, yaw, pitch);
				}
			}
		}
		for (BakedModel.Bone child : bone.children) {
			Matrix4f cm = new Matrix4f(bm).translate((child.pivotX - bone.pivotX) / 16f, (child.pivotY - bone.pivotY) / 16f, (child.pivotZ - bone.pivotZ) / 16f);
			addBone(out, child, cm, tex, emis, yaw, pitch, light);
		}
	}

	/** Bilinear point on the quad: s runs from vertex 1 to vertex 0, t from the top edge (1–0) to the bottom edge (2–3). */
	private static float[] pt(float[][] p, float s, float t) {
		float[] top = new float[3], bot = new float[3], out = new float[3];
		for (int k = 0; k < 3; k++) {
			top[k] = lerp(p[1][k], p[0][k], s);
			bot[k] = lerp(p[2][k], p[3][k], s);
			out[k] = lerp(top[k], bot[k], t);
		}
		return out;
	}

	private static void addBox(List<Face> out, float[] b, int color, float yaw, float pitch, Vector3f light) {
		float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
		float[][][] quads = {
				{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
				{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
				{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}};
		float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
		for (int i = 0; i < 6; i++) {
			float shade = 0.62f + 0.38f * Math.max(0, normals[i][0] * light.x + normals[i][1] * light.y + normals[i][2] * light.z);
			project(out, quads[i], shadeColor(color, shade), yaw, pitch);
		}
	}

	private static void project(List<Face> out, float[][] pts, int argb, float yaw, float pitch) {
		float[] xs = new float[pts.length], ys = new float[pts.length];
		float depth = 0;
		float[] zs = new float[pts.length];
		float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
		for (int i = 0; i < pts.length; i++) {
			// Java model space: y down, front −z. Rotate around the vertical axis, then tilt the camera down.
			float x = pts[i][0], y = -pts[i][1], z = pts[i][2];
			float rx = x * cy - z * sy, rz = x * sy + z * cy;
			float ry = y * cp + rz * sp, rd = rz * cp - y * sp;
			xs[i] = rx;
			ys[i] = -ry;
			zs[i] = rd;
			depth += rd;
		}
		out.add(new Face(depth / pts.length, xs, ys, zs, argb, 0));
	}

	/** Fills a convex quad (two triangles) with depth test; translucent faces blend and do not write depth. */
	private static void raster(BufferedImage img, float[] zbuf, Face f, float bias, float scale, float ox, float oy, boolean blend) {
		int n = img.getWidth();
		int count = f.xs.length;
		float[] x = new float[count], y = new float[count], z = new float[count];
		for (int i = 0; i < count; i++) {
			x[i] = f.xs[i] * scale + ox;
			y[i] = f.ys[i] * scale + oy;
			z[i] = f.zs[i] + bias;
		}
		for (int t = 1; t + 1 < count; t++) {
			tri(img, zbuf, n, x[0], y[0], z[0], x[t], y[t], z[t], x[t + 1], y[t + 1], z[t + 1], f.argb, blend);
		}
	}

	private static void tri(BufferedImage img, float[] zbuf, int n, float x0, float y0, float z0, float x1, float y1, float z1,
							float x2, float y2, float z2, int argb, boolean blend) {
		float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
		if (Math.abs(area) < 1e-9f) {
			return;
		}
		int bx0 = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
		int bx1 = Math.min(n - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
		int by0 = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
		int by1 = Math.min(n - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
		float eps = -1e-4f;
		for (int py = by0; py <= by1; py++) {
			float cyp = py + 0.5f;
			for (int px = bx0; px <= bx1; px++) {
				float cxp = px + 0.5f;
				float w0 = ((x1 - cxp) * (y2 - cyp) - (x2 - cxp) * (y1 - cyp)) / area;
				float w1 = ((x2 - cxp) * (y0 - cyp) - (x0 - cxp) * (y2 - cyp)) / area;
				float w2 = 1 - w0 - w1;
				if (w0 < eps || w1 < eps || w2 < eps) {
					continue;
				}
				float zz = w0 * z0 + w1 * z1 + w2 * z2;
				int idx = py * n + px;
				if (zz >= zbuf[idx]) {
					continue;
				}
				if (blend) {
					img.setRGB(px, py, over(img.getRGB(px, py), argb));
				} else {
					zbuf[idx] = zz;
					img.setRGB(px, py, argb);
				}
			}
		}
	}

	private static int over(int dst, int src) {
		float a = (src >>> 24) / 255f;
		int r = (int) (((src >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
		int g = (int) (((src >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
		int b = (int) ((src & 0xFF) * a + (dst & 0xFF) * (1 - a));
		int da = dst >>> 24;
		int oa = Math.min(255, (int) ((src >>> 24) + da * (1 - a)));
		return oa << 24 | r << 16 | g << 8 | b;
	}

	/** Context boxes (Java model space in blocks, head pivot at y=0). */
	private static List<float[]> mannequin(AttachmentPoint ap) {
		List<float[]> out = new ArrayList<>();
		float[] head = box(-4, -8, -4, 4, 0, 4);
		float[] body = box(-4, 0, -2, 4, 12, 2);
		switch (ap.part) {
			case HEAD -> out.add(head);
			case BODY -> {
				out.add(head);
				out.add(body);
			}
			case LEFT_ARM, RIGHT_ARM -> {
				out.add(box(ap.part == AttachmentPoint.Part.LEFT_ARM ? 4 : -8, 0, -2, ap.part == AttachmentPoint.Part.LEFT_ARM ? 8 : -4, 12, 2));
				out.add(body);
			}
			case LEFT_LEG, RIGHT_LEG -> {
				out.add(box(-4, 12, -2, 0, 24, 2));
				out.add(box(0, 12, -2, 4, 24, 2));
			}
		}
		return out;
	}

	private static float[] box(float x0, float y0, float z0, float x1, float y1, float z1) {
		return new float[]{x0 / 16f, y0 / 16f, z0 / 16f, x1 / 16f, y1 / 16f, z1 / 16f};
	}

	private static int shadeColor(int argb, float f) {
		int a = argb >>> 24, r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
		return a << 24 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int clamp(int v, int max) {
		return Math.max(0, Math.min(max - 1, v));
	}
}
