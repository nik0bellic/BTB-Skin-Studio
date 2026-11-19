package de.behindtheblocks.skinstudio.geo;

import java.util.EnumMap;
import java.util.Map;

public final class GeoCube {
	public enum Face {
		NORTH, EAST, SOUTH, WEST, UP, DOWN;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Per-face UV rectangle in texture pixels; width/height may be negative (flipped). */
	public record FaceUv(float u, float v, float w, float h) {
	}

	public float[] origin = new float[3];
	public float[] size = new float[3];
	public float inflate;
	public boolean mirror;
	/** Optional per-cube rotation (degrees) around {@link #pivot}. */
	public float[] rotation;
	public float[] pivot;
	/** Box UV offset; used when {@link #faces} is null. */
	public float u;
	public float v;
	/** Per-face UV (Bedrock 1.12+). Missing faces are not drawn. */
	public Map<Face, FaceUv> faces;

	public GeoCube copy() {
		GeoCube c = new GeoCube();
		c.origin = origin.clone();
		c.size = size.clone();
		c.inflate = inflate;
		c.mirror = mirror;
		c.rotation = rotation == null ? null : rotation.clone();
		c.pivot = pivot == null ? null : pivot.clone();
		c.u = u;
		c.v = v;
		c.faces = faces == null ? null : new EnumMap<>(faces);
		return c;
	}

	public static GeoCube box(float x, float y, float z, float w, float h, float d, float u, float v) {
		GeoCube c = new GeoCube();
		c.origin = new float[]{x, y, z};
		c.size = new float[]{w, h, d};
		c.u = u;
		c.v = v;
		return c;
	}
}
