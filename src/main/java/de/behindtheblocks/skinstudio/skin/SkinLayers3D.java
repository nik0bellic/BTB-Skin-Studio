package de.behindtheblocks.skinstudio.skin;

import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;
import de.behindtheblocks.skinstudio.geo.GeoModel;

import java.awt.image.BufferedImage;
import java.util.EnumMap;

/**
 * Turns the outer skin layer (hat, jacket, sleeves, pants) into real 3D pixels, similar to Bedrock's
 * layered look: every visible pixel becomes a small cube standing on the body, inner faces between
 * neighbouring pixels are left out. The result is ordinary cosmetic geometry using the skin as texture.
 */
public final class SkinLayers3D {
	private SkinLayers3D() {
	}

	private record Region(String bone, float px, float py, float pz, int u, int v, float x, float y, float z, int w, int h, int d) {
	}

	public static GeoModel build(BufferedImage skin, boolean slim) {
		int s = Math.max(1, skin.getWidth() / 64);
		int aw = slim ? 3 : 4;
		Region[] regions = {
				new Region("head", 0, 24, 0, 32, 0, -4, 24, -4, 8, 8, 8),
				new Region("body", 0, 24, 0, 16, 32, -4, 12, -2, 8, 12, 4),
				new Region("rightArm", -5, 22, 0, 40, 32, slim ? -7 : -8, 12, -2, aw, 12, 4),
				new Region("leftArm", 5, 22, 0, 48, 48, 4, 12, -2, aw, 12, 4),
				new Region("rightLeg", -1.9f, 12, 0, 0, 32, -3.9f, 0, -2, 4, 12, 4),
				new Region("leftLeg", 1.9f, 12, 0, 0, 48, -0.1f, 0, -2, 4, 12, 4),
		};
		GeoModel model = new GeoModel("geometry.btb.skin_layers_3d", 64, 64);
		for (Region r : regions) {
			GeoBone bone = new GeoBone(r.bone);
			bone.pivot = new float[]{r.px, r.py, r.pz};
			for (int face = 0; face < 6; face++) {
				addFace(bone, skin, s, r, face);
			}
			if (!bone.cubes.isEmpty()) {
				model.bones.add(bone);
			}
		}
		return model;
	}

	/** face: 0 top, 1 bottom, 2 right(−x), 3 front(−z), 4 left(+x), 5 back(+z) – in box-UV order. */
	private static void addFace(GeoBone bone, BufferedImage img, int s, Region r, int face) {
		int u = r.u, v = r.v, w = r.w, h = r.h, d = r.d;
		int ru, rv, cols, rows;
		switch (face) {
			case 0 -> { ru = u + d; rv = v; cols = w; rows = d; }
			case 1 -> { ru = u + d + w; rv = v; cols = w; rows = d; }
			case 2 -> { ru = u; rv = v + d; cols = d; rows = h; }
			case 3 -> { ru = u + d; rv = v + d; cols = w; rows = h; }
			case 4 -> { ru = u + d + w; rv = v + d; cols = d; rows = h; }
			default -> { ru = u + 2 * d + w; rv = v + d; cols = w; rows = h; }
		}
		float p = 1f / s;
		int gc = cols * s, gr = rows * s;
		boolean[][] solid = new boolean[gc][gr];
		for (int i = 0; i < gc; i++) {
			for (int j = 0; j < gr; j++) {
				int x = ru * s + i, y = rv * s + j;
				solid[i][j] = x < img.getWidth() && y < img.getHeight() && (img.getRGB(x, y) >>> 24) > 16;
			}
		}
		float t = p; // thickness: one texture pixel
		for (int i = 0; i < gc; i++) {
			for (int j = 0; j < gr; j++) {
				if (!solid[i][j]) {
					continue;
				}
				float[] o = cellOrigin(r, face, i, j, p, t);
				closeEdges(o, r, face, i, j, gc, gr, t);
				GeoCube c = new GeoCube();
				c.origin = new float[]{o[0], o[1], o[2]};
				c.size = new float[]{o[3], o[4], o[5]};
				c.faces = new EnumMap<>(GeoCube.Face.class);
				GeoCube.FaceUv uv = new GeoCube.FaceUv(ru + i * p, rv + j * p, p, p);
				// outward face always; side faces only where the neighbouring pixel is empty
				c.faces.put(outward(face), uv);
				addSide(c, r, face, i, j, i - 1, j, solid, p, t, uv);
				addSide(c, r, face, i, j, i + 1, j, solid, p, t, uv);
				addSide(c, r, face, i, j, i, j - 1, solid, p, t, uv);
				addSide(c, r, face, i, j, i, j + 1, solid, p, t, uv);
				bone.cubes.add(c);
			}
		}
	}

	/**
	 * Lets edge voxels grow over the box edge so that corners are closed like in Bedrock / the 3D Skin Layers
	 * mod. Front/back faces cover the vertical edges, front/back/side faces cover the top and bottom edges.
	 * Where two body parts touch (body ↔ arms, legs ↔ legs, body ↔ legs) nothing is extended, so no two voxels
	 * ever share a visible plane.
	 */
	private static void closeEdges(float[] o, Region r, int face, int i, int j, int gc, int gr, float t) {
		if (face == 0 || face == 1) {
			return;
		}
		boolean head = r.bone.equals("head"), body = r.bone.equals("body");
		boolean arm = r.bone.endsWith("Arm"), leg = r.bone.endsWith("Leg");
		boolean right = r.bone.startsWith("right");
		// vertical edges (x direction), only on front and back
		if (face == 3 || face == 5) {
			boolean minSide = face == 3 ? i == 0 : i == gc - 1;      // edge at minX
			boolean maxSide = face == 3 ? i == gc - 1 : i == 0;      // edge at maxX
			boolean allowMin = head || ((arm || leg) && right);
			boolean allowMax = head || ((arm || leg) && !right);
			if (minSide && allowMin) {
				o[0] -= t;
				o[3] += t;
			}
			if (maxSide && allowMax) {
				o[3] += t;
			}
		}
		// top and bottom edges (y direction)
		boolean top = j == 0, bottom = j == gr - 1;
		boolean allowTop = head || arm || body;
		boolean allowBottom = head || arm || leg;
		if (top && allowTop) {
			o[4] += t;
		}
		if (bottom && allowBottom) {
			o[1] -= t;
			o[4] += t;
		}
	}

	private static void addSide(GeoCube c, Region r, int face, int i, int j, int ni, int nj, boolean[][] solid, float p, float t, GeoCube.FaceUv uv) {
		if (ni >= 0 && nj >= 0 && ni < solid.length && nj < solid[0].length && solid[ni][nj]) {
			return;
		}
		float[] a = cellOrigin(r, face, i, j, p, t), b = cellOrigin(r, face, ni, nj, p, t);
		float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		GeoCube.Face f;
		if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
			f = dx > 0 ? GeoCube.Face.EAST : GeoCube.Face.WEST;
		} else if (Math.abs(dy) > Math.abs(dz)) {
			f = dy > 0 ? GeoCube.Face.UP : GeoCube.Face.DOWN;
		} else {
			f = dz > 0 ? GeoCube.Face.SOUTH : GeoCube.Face.NORTH;
		}
		c.faces.put(f, uv);
	}

	private static GeoCube.Face outward(int face) {
		return switch (face) {
			case 0 -> GeoCube.Face.UP;
			case 1 -> GeoCube.Face.DOWN;
			case 2 -> GeoCube.Face.WEST;
			case 3 -> GeoCube.Face.NORTH;
			case 4 -> GeoCube.Face.EAST;
			default -> GeoCube.Face.SOUTH;
		};
	}

	/** Origin and size {x, y, z, sx, sy, sz} of the voxel for grid cell (i, j) of a face (Bedrock space). */
	private static float[] cellOrigin(Region r, int face, int i, int j, float p, float t) {
		float minX = r.x, maxX = r.x + r.w, minY = r.y, maxY = r.y + r.h, minZ = r.z, maxZ = r.z + r.d;
		return switch (face) {
			case 0 -> new float[]{minX + i * p, maxY, maxZ - (j + 1) * p, p, t, p};
			case 1 -> new float[]{minX + i * p, minY - t, maxZ - (j + 1) * p, p, t, p};
			case 2 -> new float[]{minX - t, maxY - (j + 1) * p, maxZ - (i + 1) * p, t, p, p};
			case 3 -> new float[]{minX + i * p, maxY - (j + 1) * p, minZ - t, p, p, t};
			case 4 -> new float[]{maxX, maxY - (j + 1) * p, minZ + i * p, t, p, p};
			default -> new float[]{maxX - (i + 1) * p, maxY - (j + 1) * p, maxZ, p, p, t};
		};
	}
}
