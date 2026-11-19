package de.behindtheblocks.skinstudio.cosmetic;

import java.util.Locale;

/**
 * Where a cosmetic is attached. Each point follows one vanilla player part ({@link #part}) and
 * has an anchor in Bedrock player space (pixels, Y up, feet at 0) that rotation and scale pivot around.
 */
public enum AttachmentPoint {
	HEAD("Kopf", Part.HEAD, 0, 32, 0),
	BODY("Körper", Part.BODY, 0, 18, -2),
	BACK("Rücken", Part.BODY, 0, 18, 2),
	LEFT_ARM("Linker Arm", Part.LEFT_ARM, 6, 12, 0),
	RIGHT_ARM("Rechter Arm", Part.RIGHT_ARM, -6, 12, 0),
	LEFT_LEG("Linkes Bein", Part.LEFT_LEG, 2, 0, 0),
	RIGHT_LEG("Rechtes Bein", Part.RIGHT_LEG, -2, 0, 0),
	LEFT_SHOULDER("Linke Schulter", Part.BODY, 6, 24, 0),
	RIGHT_SHOULDER("Rechte Schulter", Part.BODY, -6, 24, 0);

	/** The vanilla model part a cosmetic follows. Default pivots are given in Bedrock space. */
	public enum Part {
		HEAD(0, 24, 0), BODY(0, 24, 0), LEFT_ARM(5, 22, 0), RIGHT_ARM(-5, 22, 0), LEFT_LEG(1.9f, 12, 0), RIGHT_LEG(-1.9f, 12, 0);

		public final float pivotX, pivotY, pivotZ;

		Part(float x, float y, float z) {
			this.pivotX = x;
			this.pivotY = y;
			this.pivotZ = z;
		}

		/** Maps Bedrock/vanilla bone names (head, rightArm, right_arm, leftLeg …) to a part. */
		public static Part fromBoneName(String bone) {
			String b = bone.toLowerCase(Locale.ROOT).replace("_", "");
			return switch (b) {
				case "head", "hat", "helmet" -> HEAD;
				case "body", "jacket", "torso", "waist" -> BODY;
				case "leftarm", "leftsleeve", "leftitem" -> LEFT_ARM;
				case "rightarm", "rightsleeve", "rightitem" -> RIGHT_ARM;
				case "leftleg", "leftpants", "leftboot" -> LEFT_LEG;
				case "rightleg", "rightpants", "rightboot" -> RIGHT_LEG;
				default -> null;
			};
		}
	}

	public final String label;
	public final Part part;
	public final float anchorX, anchorY, anchorZ;

	AttachmentPoint(String label, Part part, float x, float y, float z) {
		this.label = label;
		this.part = part;
		this.anchorX = x;
		this.anchorY = y;
		this.anchorZ = z;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static AttachmentPoint byId(String id) {
		String n = id.toLowerCase(Locale.ROOT).replace(" ", "_");
		if (n.equals("shoulder")) {
			return LEFT_SHOULDER;
		}
		for (AttachmentPoint p : values()) {
			if (p.id().equals(n)) {
				return p;
			}
		}
		return null;
	}
}
