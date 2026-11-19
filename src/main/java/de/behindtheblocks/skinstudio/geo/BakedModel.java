package de.behindtheblocks.skinstudio.geo;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@link GeoModel} converted once into Java model space (Y down, units of 1/16 block),
 * with all quads and UVs precomputed. Rendering only multiplies matrices; no per-frame allocation.
 *
 * <p>Conversion rules (identical to how vanilla bakes the player model):
 * Java position = (x, 24 − y, z); bone rotations keep their sign and are applied in ZYX order
 * like {@code ModelPart#translateAndRotate}.
 */
public final class BakedModel {
	/** Floats per vertex: x, y, z (blocks, relative to bone pivot), u, v (0..1). */
	public static final int VERTEX_STRIDE = 5;
	/** Floats per quad: 4 vertices + normal (3). */
	public static final int QUAD_STRIDE = 4 * VERTEX_STRIDE + 3;

	public final List<Bone> roots = new ArrayList<>();
	public final Map<String, Bone> byName = new HashMap<>();
	public final int quadCount;

	public static final class Bone {
		public final String name;
		public final int index;
		/** Pivot in Java model pixels (absolute). */
		public final float pivotX, pivotY, pivotZ;
		/** Radians. */
		public final float rotX, rotY, rotZ;
		public final float[] quads;
		public final List<Bone> children = new ArrayList<>();
		public Bone parent;

		Bone(String name, int index, float px, float py, float pz, float rx, float ry, float rz, float[] quads) {
			this.name = name;
			this.index = index;
			this.pivotX = px;
			this.pivotY = py;
			this.pivotZ = pz;
			this.rotX = rx;
			this.rotY = ry;
			this.rotZ = rz;
			this.quads = quads;
		}

		public int quadCount() {
			return quads.length / QUAD_STRIDE;
		}
	}

	private BakedModel(int quadCount) {
		this.quadCount = quadCount;
	}

	public static BakedModel bake(GeoModel model) {
		float texW = Math.max(1, model.textureWidth);
		float texH = Math.max(1, model.textureHeight);
		List<Bone> all = new ArrayList<>();
		int total = 0;
		for (int i = 0; i < model.bones.size(); i++) {
			GeoBone gb = model.bones.get(i);
			float px = gb.pivot[0], py = 24f - gb.pivot[1], pz = gb.pivot[2];
			List<float[]> quads = new ArrayList<>();
			for (GeoCube c : gb.cubes) {
				bakeCube(c, px, py, pz, texW, texH, quads);
			}
			float[] flat = new float[quads.size() * QUAD_STRIDE];
			for (int q = 0; q < quads.size(); q++) {
				System.arraycopy(quads.get(q), 0, flat, q * QUAD_STRIDE, QUAD_STRIDE);
			}
			total += quads.size();
			all.add(new Bone(gb.name, i, px, py, pz,
					(float) Math.toRadians(gb.rotation[0]), (float) Math.toRadians(gb.rotation[1]), (float) Math.toRadians(gb.rotation[2]), flat));
		}
		BakedModel baked = new BakedModel(total);
		for (Bone b : all) {
			baked.byName.put(b.name.toLowerCase(Locale.ROOT), b);
		}
		for (int i = 0; i < all.size(); i++) {
			Bone b = all.get(i);
			String parentName = model.bones.get(i).parent;
			Bone parent = parentName == null ? null : baked.byName.get(parentName.toLowerCase(Locale.ROOT));
			if (parent != null && parent != b && !isAncestor(b, parent)) {
				b.parent = parent;
				parent.children.add(b);
			} else {
				baked.roots.add(b);
			}
		}
		return baked;
	}

	private static boolean isAncestor(Bone candidate, Bone of) {
		for (Bone p = of; p != null; p = p.parent) {
			if (p == candidate) {
				return true;
			}
		}
		return false;
	}

	public Bone bone(String name) {
		return byName.get(name.toLowerCase(Locale.ROOT));
	}

	// ----------------------------------------------------------------- cube baking

	private static void bakeCube(GeoCube c, float bonePx, float bonePy, float bonePz, float texW, float texH, List<float[]> out) {
		float w = c.size[0], h = c.size[1], d = c.size[2];
		float minX = c.origin[0];
		float minY = 24f - c.origin[1] - h;
		float minZ = c.origin[2];
		float maxX = minX + w, maxY = minY + h, maxZ = minZ + d;
		float g = c.inflate;
		minX -= g; minY -= g; minZ -= g;
		maxX += g; maxY += g; maxZ += g;
		boolean mirror = c.mirror && c.faces == null;
		if (mirror) {
			float t = maxX;
			maxX = minX;
			minX = t;
		}
		float[] t0 = {minX, minY, minZ}, t1 = {maxX, minY, minZ}, t2 = {maxX, maxY, minZ}, t3 = {minX, maxY, minZ};
		float[] l0 = {minX, minY, maxZ}, l1 = {maxX, minY, maxZ}, l2 = {maxX, maxY, maxZ}, l3 = {minX, maxY, maxZ};

		Quaternionf rot = null;
		float cx = 0, cy = 0, cz = 0;
		if (c.rotation != null && (c.rotation[0] != 0 || c.rotation[1] != 0 || c.rotation[2] != 0)) {
			rot = new Quaternionf().rotationZYX((float) Math.toRadians(c.rotation[2]), (float) Math.toRadians(c.rotation[1]), (float) Math.toRadians(c.rotation[0]));
			cx = c.pivot[0];
			cy = 24f - c.pivot[1];
			cz = c.pivot[2];
		}

		if (c.faces == null) {
			float u = c.u, v = c.v;
			float u0 = u, u1 = u + d, u2 = u + d + w, u22 = u + d + w + w, u3 = u + d + w + d, u4 = u + d + w + d + w;
			float v0 = v, v1 = v + d, v2 = v + d + h;
			emit(out, new float[][]{l1, l0, t0, t1}, u1, v0, u2, v1, texW, texH, mirror, 0, -1, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);  // vanilla DOWN = visual top (Y down)
			emit(out, new float[][]{t2, t3, l3, l2}, u2, v1, u22, v0, texW, texH, mirror, 0, 1, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);  // vanilla UP = visual bottom
			emit(out, new float[][]{t0, l0, l3, t3}, u0, v1, u1, v2, texW, texH, mirror, -1, 0, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);  // WEST
			emit(out, new float[][]{t1, t0, t3, t2}, u1, v1, u2, v2, texW, texH, mirror, 0, 0, -1, rot, cx, cy, cz, bonePx, bonePy, bonePz);  // NORTH
			emit(out, new float[][]{l1, t1, t2, l2}, u2, v1, u3, v2, texW, texH, mirror, 1, 0, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);   // EAST
			emit(out, new float[][]{l0, l1, l2, l3}, u3, v1, u4, v2, texW, texH, mirror, 0, 0, 1, rot, cx, cy, cz, bonePx, bonePy, bonePz);   // SOUTH
		} else {
			for (var e : c.faces.entrySet()) {
				GeoCube.FaceUv f = e.getValue();
				float a = f.u(), b = f.v(), cc = f.u() + f.w(), dd = f.v() + f.h();
				switch (e.getKey()) {
					// Bedrock stores up/down faces flipped on both axes (Blockbench writes negative sizes to compensate).
					// Bedrock "up" is the visual top, which is vanilla's DOWN polygon in Java's Y-down space.
					case UP -> emit(out, new float[][]{l1, l0, t0, t1}, cc, dd, a, b, texW, texH, false, 0, -1, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);
					case DOWN -> emit(out, new float[][]{t2, t3, l3, l2}, cc, b, a, dd, texW, texH, false, 0, 1, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);
					case WEST -> emit(out, new float[][]{t0, l0, l3, t3}, a, b, cc, dd, texW, texH, false, -1, 0, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);
					case NORTH -> emit(out, new float[][]{t1, t0, t3, t2}, a, b, cc, dd, texW, texH, false, 0, 0, -1, rot, cx, cy, cz, bonePx, bonePy, bonePz);
					case EAST -> emit(out, new float[][]{l1, t1, t2, l2}, a, b, cc, dd, texW, texH, false, 1, 0, 0, rot, cx, cy, cz, bonePx, bonePy, bonePz);
					case SOUTH -> emit(out, new float[][]{l0, l1, l2, l3}, a, b, cc, dd, texW, texH, false, 0, 0, 1, rot, cx, cy, cz, bonePx, bonePy, bonePz);
				}
			}
		}
	}

	/** Same vertex/UV assignment as {@code ModelPart.Polygon}. */
	private static void emit(List<float[]> out, float[][] verts, float u0, float v0, float u1, float v1, float texW, float texH,
							 boolean mirror, float nx, float ny, float nz, Quaternionf rot, float cx, float cy, float cz,
							 float px, float py, float pz) {
		if (u0 == u1 && v0 == v1) {
			return;
		}
		float[][] uv = {{u1 / texW, v0 / texH}, {u0 / texW, v0 / texH}, {u0 / texW, v1 / texH}, {u1 / texW, v1 / texH}};
		float[][] vs = verts.clone();
		if (mirror) {
			// vanilla remaps UVs first and then reverses the vertex order: UVs travel with their vertices
			for (int i = 0; i < 2; i++) {
				float[] tmp = vs[i];
				vs[i] = vs[3 - i];
				vs[3 - i] = tmp;
				float[] tu = uv[i];
				uv[i] = uv[3 - i];
				uv[3 - i] = tu;
			}
			nx = -nx;
		}
		float[] q = new float[QUAD_STRIDE];
		Vector3f tmp = new Vector3f();
		for (int i = 0; i < 4; i++) {
			tmp.set(vs[i][0], vs[i][1], vs[i][2]);
			if (rot != null) {
				tmp.sub(cx, cy, cz).rotate(rot).add(cx, cy, cz);
			}
			int o = i * VERTEX_STRIDE;
			q[o] = (tmp.x - px) / 16f;
			q[o + 1] = (tmp.y - py) / 16f;
			q[o + 2] = (tmp.z - pz) / 16f;
			q[o + 3] = uv[i][0];
			q[o + 4] = uv[i][1];
		}
		tmp.set(nx, ny, nz);
		if (rot != null) {
			tmp.rotate(rot);
		}
		q[20] = tmp.x;
		q[21] = tmp.y;
		q[22] = tmp.z;
		out.add(q);
	}
}
