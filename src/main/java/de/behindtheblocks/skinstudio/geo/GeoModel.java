package de.behindtheblocks.skinstudio.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * A model in Bedrock geometry form (pixels, Y up, player feet at 0, head pivot at y=24).
 * This is the format Blockbench writes as "Bedrock Model" and the format of Bedrock skin packs,
 * so BTB cosmetics, imported Bedrock geometry and Persona pieces share one representation.
 */
public final class GeoModel {
	public String identifier;
	public int textureWidth;
	public int textureHeight;
	public final List<GeoBone> bones = new ArrayList<>();

	public GeoModel(String identifier, int textureWidth, int textureHeight) {
		this.identifier = identifier;
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
	}

	public GeoBone bone(String name) {
		for (GeoBone b : bones) {
			if (b.name.equalsIgnoreCase(name)) {
				return b;
			}
		}
		return null;
	}

	public int cubeCount() {
		int n = 0;
		for (GeoBone b : bones) {
			n += b.cubes.size();
		}
		return n;
	}

	public GeoModel copy() {
		GeoModel m = new GeoModel(identifier, textureWidth, textureHeight);
		for (GeoBone b : bones) {
			m.bones.add(b.copy());
		}
		return m;
	}

	/** Axis-aligned bounds of all cubes (ignoring rotations), {minX,minY,minZ,maxX,maxY,maxZ}. */
	public float[] bounds() {
		float[] r = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (GeoBone b : bones) {
			for (GeoCube c : b.cubes) {
				for (int i = 0; i < 3; i++) {
					r[i] = Math.min(r[i], c.origin[i] - c.inflate);
					r[i + 3] = Math.max(r[i + 3], c.origin[i] + c.size[i] + c.inflate);
				}
			}
		}
		if (r[0] == Float.MAX_VALUE) {
			return new float[6];
		}
		return r;
	}
}
