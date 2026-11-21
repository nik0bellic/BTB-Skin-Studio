package de.behindtheblocks.skinstudio.client.emote;

import de.behindtheblocks.skinstudio.emote.Emotes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Puts an emote pose onto a humanoid model (player model and the armour models worn on it).
 * Head, arms and legs are set relative to the body like vanilla does. The body lean turns at the hips and
 * carries head and arms along; the whole-figure turn and move turns at the feet and carries every part.
 * Vanilla models have no such hierarchy (head and arms do not follow a bent body), which is why poses that
 * bend the body used to tear the figure apart.
 */
public final class EmotePoser {
	private static final Vector3f HIPS = new Vector3f(0, 12, 0);
	private static final Vector3f FEET = new Vector3f(0, 24, 0);

	private EmotePoser() {
	}

	public static void apply(HumanoidModel<?> model, float[] pose) {
		float w = pose[Emotes.SIZE];
		set(model.head, pose, Emotes.HEAD, w);
		set(model.rightArm, pose, Emotes.RIGHT_ARM, w);
		set(model.leftArm, pose, Emotes.LEFT_ARM, w);
		set(model.rightLeg, pose, Emotes.RIGHT_LEG, w);
		set(model.leftLeg, pose, Emotes.LEFT_LEG, w);

		Matrix3f lean = rotation(pose, Emotes.BODY, w);
		if (lean != null) {
			for (ModelPart p : new ModelPart[]{model.body, model.head, model.rightArm, model.leftArm}) {
				carry(p, lean, HIPS);
			}
		}
		Matrix3f turn = rotation(pose, Emotes.ROOT, w);
		float mx = value(pose, Emotes.MOVE) * w, my = value(pose, Emotes.MOVE + 1) * w, mz = value(pose, Emotes.MOVE + 2) * w;
		ModelPart[] all = {model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		for (ModelPart p : all) {
			if (turn != null) {
				carry(p, turn, FEET);
			}
			p.x += mx;
			p.y += my;
			p.z += mz;
		}
	}

	private static void set(ModelPart part, float[] pose, int i, float w) {
		part.xRot = mix(part.xRot, pose[i], w);
		part.yRot = mix(part.yRot, pose[i + 1], w);
		part.zRot = mix(part.zRot, pose[i + 2], w);
	}

	private static float mix(float vanilla, float deg, float w) {
		return Float.isNaN(deg) ? vanilla : vanilla + ((float) Math.toRadians(deg) - vanilla) * w;
	}

	private static float value(float[] pose, int i) {
		return Float.isNaN(pose[i]) ? 0 : pose[i];
	}

	/** Rotation of a pose group (degrees, weighted), or null when it does not turn at all. */
	private static Matrix3f rotation(float[] pose, int i, float w) {
		float x = value(pose, i) * w, y = value(pose, i + 1) * w, z = value(pose, i + 2) * w;
		if (Math.abs(x) < 0.01f && Math.abs(y) < 0.01f && Math.abs(z) < 0.01f) {
			return null;
		}
		return new Matrix3f().rotationZYX((float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x));
	}

	/** Turns a part around {@code pivot} by {@code r}: its position moves along, its rotation is composed. */
	private static void carry(ModelPart part, Matrix3f r, Vector3f pivot) {
		Vector3f p = new Vector3f(part.x, part.y, part.z).sub(pivot);
		r.transform(p).add(pivot);
		part.x = p.x;
		part.y = p.y;
		part.z = p.z;
		Matrix3f m = new Matrix3f(r).mul(new Matrix3f().rotationZYX(part.zRot, part.yRot, part.xRot));
		// back to the Z·Y·X angles ModelPart uses
		float sy = Math.max(-1, Math.min(1, -m.m02()));
		part.yRot = (float) Math.asin(sy);
		if (Math.abs(sy) < 0.9999f) {
			part.xRot = (float) Math.atan2(m.m12(), m.m22());
			part.zRot = (float) Math.atan2(m.m01(), m.m00());
		} else {
			part.xRot = (float) Math.atan2(-m.m21(), m.m11());
			part.zRot = 0;
		}
	}
}
