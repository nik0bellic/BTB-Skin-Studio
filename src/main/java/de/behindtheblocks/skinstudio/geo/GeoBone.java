package de.behindtheblocks.skinstudio.geo;

import java.util.ArrayList;
import java.util.List;

public final class GeoBone {
	public String name;
	public String parent;
	public float[] pivot = new float[3];
	/** Degrees, Bedrock convention. */
	public float[] rotation = new float[3];
	public boolean mirror;
	public float inflate;
	public final List<GeoCube> cubes = new ArrayList<>();

	public GeoBone(String name) {
		this.name = name;
	}

	public GeoBone copy() {
		GeoBone b = new GeoBone(name);
		b.parent = parent;
		b.pivot = pivot.clone();
		b.rotation = rotation.clone();
		b.mirror = mirror;
		b.inflate = inflate;
		for (GeoCube c : cubes) {
			b.cubes.add(c.copy());
		}
		return b;
	}
}
