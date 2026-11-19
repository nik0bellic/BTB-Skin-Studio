package de.behindtheblocks.skinstudio.molang;

/** Mutable per-evaluation inputs. One instance is reused per render thread, never shared. */
public final class MolangContext {
	public float animTime;
	public float lifeTime;
	public float moveSpeed;
	public float walkDistance;
	public float headPitch;
	public float headYaw;
	public boolean sneaking;
	public boolean onGround = true;
	/** variable.* / temp.* values set by assignments. */
	public final java.util.Map<String, Float> variables = new java.util.HashMap<>();
	private long seed = 0x9E3779B97F4A7C15L;

	public float random() {
		seed ^= seed << 13;
		seed ^= seed >>> 7;
		seed ^= seed << 17;
		return (seed >>> 40) / (float) (1L << 24);
	}
}
