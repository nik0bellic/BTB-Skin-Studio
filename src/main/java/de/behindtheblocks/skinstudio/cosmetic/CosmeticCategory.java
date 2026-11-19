package de.behindtheblocks.skinstudio.cosmetic;

import java.util.Locale;

/** Top-level groups shown in the wardrobe navigation. */
public enum CosmeticCategory {
	HEAD("Kopf", "Head"),
	HAIR_FACE("Haare & Gesicht", "Hair & Face"),
	BACK("Rücken", "Back"),
	TORSO("Oberkörper", "Torso"),
	ARMS("Arme & Hände", "Arms & Hands"),
	LEGS("Beine & Füße", "Legs & Feet"),
	SPECIAL("Besonderes", "Special");

	public final String labelDe;
	public final String labelEn;

	CosmeticCategory(String de, String en) {
		this.labelDe = de;
		this.labelEn = en;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static CosmeticCategory byId(String id) {
		for (CosmeticCategory c : values()) {
			if (c.id().equalsIgnoreCase(id)) {
				return c;
			}
		}
		return null;
	}
}
