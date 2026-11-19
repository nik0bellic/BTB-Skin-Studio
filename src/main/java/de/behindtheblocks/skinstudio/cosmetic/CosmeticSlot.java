package de.behindtheblocks.skinstudio.cosmetic;

import java.util.Locale;

/**
 * Body regions a cosmetic occupies. Two cosmetics conflict when they share a slot;
 * equipping the newer one takes the older one off (see {@code Loadout#equip}).
 */
public enum CosmeticSlot {
	HEAD_TOP("Kopf oben"),
	HALO("Über dem Kopf"),
	HORNS("Hörner"),
	HAIR("Frisur"),
	HAIR_ACCESSORY("Haarschmuck"),
	EARS("Ohren"),
	EYES("Augen"),
	EYEBROWS("Augenbrauen"),
	FACE("Gesicht"),
	MOUTH("Mund/Bart"),
	NECK("Hals"),
	BACK("Rücken"),
	SHOULDER_LEFT("Linke Schulter"),
	SHOULDER_RIGHT("Rechte Schulter"),
	TORSO("Oberteil"),
	TORSO_OUTER("Jacke/Weste"),
	CHEST("Brust"),
	WAIST("Hüfte"),
	ARM_LEFT("Linker Arm"),
	ARM_RIGHT("Rechter Arm"),
	WRIST_LEFT("Linkes Handgelenk"),
	WRIST_RIGHT("Rechtes Handgelenk"),
	HANDS("Hände"),
	LEGS("Hose"),
	KNEES("Knie"),
	LEG_LEFT("Linkes Bein"),
	LEG_RIGHT("Rechtes Bein"),
	FEET("Füße"),
	FLOATING("Schwebend"),
	AURA("Effekt"),
	COMPANION("Begleiter");

	public final String label;

	CosmeticSlot(String label) {
		this.label = label;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static CosmeticSlot byId(String id) {
		for (CosmeticSlot s : values()) {
			if (s.id().equalsIgnoreCase(id)) {
				return s;
			}
		}
		return null;
	}
}
