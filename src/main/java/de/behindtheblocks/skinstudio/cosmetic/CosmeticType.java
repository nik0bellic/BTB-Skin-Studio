package de.behindtheblocks.skinstudio.cosmetic;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;


/** Sub-categories with sensible defaults for attachment point and occupied slots. */
public enum CosmeticType {
	// Kopf
	HAT(CosmeticCategory.HEAD, "Hüte", AttachmentPoint.HEAD, CosmeticSlot.HEAD_TOP),
	BEANIE(CosmeticCategory.HEAD, "Mützen", AttachmentPoint.HEAD, CosmeticSlot.HEAD_TOP),
	CROWN(CosmeticCategory.HEAD, "Kronen", AttachmentPoint.HEAD, CosmeticSlot.HEAD_TOP),
	HELMET(CosmeticCategory.HEAD, "Helme", AttachmentPoint.HEAD, CosmeticSlot.HEAD_TOP, CosmeticSlot.HAIR, CosmeticSlot.HORNS),
	MASK(CosmeticCategory.HEAD, "Masken", AttachmentPoint.HEAD, CosmeticSlot.FACE, CosmeticSlot.MOUTH),
	GLASSES(CosmeticCategory.HEAD, "Brillen", AttachmentPoint.HEAD, CosmeticSlot.EYES),
	HORNS_TYPE(CosmeticCategory.HEAD, "Hörner", AttachmentPoint.HEAD, CosmeticSlot.HORNS),
	HALO_TYPE(CosmeticCategory.HEAD, "Heiligenscheine", AttachmentPoint.HEAD, CosmeticSlot.HALO),
	HAIR_ACCESSORY_TYPE(CosmeticCategory.HEAD, "Haaraccessoires", AttachmentPoint.HEAD, CosmeticSlot.HAIR_ACCESSORY),
	FLOWER(CosmeticCategory.HEAD, "Blumen & Pflanzen", AttachmentPoint.HEAD, CosmeticSlot.HAIR_ACCESSORY),
	HEAD_DECORATION(CosmeticCategory.HEAD, "Deko & Fackeln", AttachmentPoint.HEAD, CosmeticSlot.HEAD_TOP),
	HEADPHONES(CosmeticCategory.HEAD, "Kopfhörer", AttachmentPoint.HEAD, CosmeticSlot.EARS),
	// Haare & Gesicht
	HAIRSTYLE(CosmeticCategory.HAIR_FACE, "Frisuren", AttachmentPoint.HEAD, CosmeticSlot.HAIR),
	BEARD(CosmeticCategory.HAIR_FACE, "Bärte", AttachmentPoint.HEAD, CosmeticSlot.MOUTH),
	MUSTACHE(CosmeticCategory.HAIR_FACE, "Schnurrbärte", AttachmentPoint.HEAD, CosmeticSlot.MOUTH),
	EYEBROWS_TYPE(CosmeticCategory.HAIR_FACE, "Augenbrauen", AttachmentPoint.HEAD, CosmeticSlot.EYEBROWS),
	FACE_ACCESSORY(CosmeticCategory.HAIR_FACE, "Gesichtsaccessoires", AttachmentPoint.HEAD, CosmeticSlot.FACE),
	// Rücken
	BACKPACK(CosmeticCategory.BACK, "Rucksäcke", AttachmentPoint.BACK, CosmeticSlot.BACK),
	WINGS(CosmeticCategory.BACK, "Flügel", AttachmentPoint.BACK, CosmeticSlot.BACK),
	CAPE(CosmeticCategory.BACK, "Umhänge", AttachmentPoint.BACK, CosmeticSlot.BACK),
	SHIELD(CosmeticCategory.BACK, "Schilde", AttachmentPoint.BACK, CosmeticSlot.BACK),
	BACK_DECORATION(CosmeticCategory.BACK, "Deko", AttachmentPoint.BACK, CosmeticSlot.BACK),
	BACK_COMPANION(CosmeticCategory.BACK, "Kleine Begleiter", AttachmentPoint.BACK, CosmeticSlot.BACK),
	// Oberkörper
	SHIRT(CosmeticCategory.TORSO, "Shirts", AttachmentPoint.BODY, CosmeticSlot.TORSO),
	JACKET(CosmeticCategory.TORSO, "Jacken", AttachmentPoint.BODY, CosmeticSlot.TORSO_OUTER),
	VEST(CosmeticCategory.TORSO, "Westen", AttachmentPoint.BODY, CosmeticSlot.TORSO_OUTER),
	ARMOR_DECORATION(CosmeticCategory.TORSO, "Rüstungsdeko", AttachmentPoint.BODY, CosmeticSlot.CHEST),
	SHOULDER_ACCESSORY(CosmeticCategory.TORSO, "Schulteraccessoires", AttachmentPoint.LEFT_SHOULDER, CosmeticSlot.SHOULDER_LEFT),
	BELT(CosmeticCategory.TORSO, "Gürtel & Taschen", AttachmentPoint.BODY, CosmeticSlot.WAIST),
	// Arme & Hände
	GLOVES(CosmeticCategory.ARMS, "Handschuhe", AttachmentPoint.RIGHT_ARM, CosmeticSlot.HANDS),
	BRACELET(CosmeticCategory.ARMS, "Armbänder", AttachmentPoint.RIGHT_ARM, CosmeticSlot.WRIST_RIGHT),
	ARM_PROSTHESIS(CosmeticCategory.ARMS, "Prothesen", AttachmentPoint.RIGHT_ARM, CosmeticSlot.ARM_RIGHT),
	SHOULDER_DECORATION(CosmeticCategory.ARMS, "Schulterdeko", AttachmentPoint.RIGHT_SHOULDER, CosmeticSlot.SHOULDER_RIGHT),
	// Beine & Füße
	PANTS(CosmeticCategory.LEGS, "Hosen", AttachmentPoint.RIGHT_LEG, CosmeticSlot.LEGS),
	SHOES(CosmeticCategory.LEGS, "Schuhe", AttachmentPoint.RIGHT_LEG, CosmeticSlot.FEET),
	BOOTS(CosmeticCategory.LEGS, "Stiefel", AttachmentPoint.RIGHT_LEG, CosmeticSlot.FEET),
	KNEE_PADS(CosmeticCategory.LEGS, "Knieschoner", AttachmentPoint.RIGHT_LEG, CosmeticSlot.KNEES),
	LEG_PROSTHESIS(CosmeticCategory.LEGS, "Beinprothesen", AttachmentPoint.RIGHT_LEG, CosmeticSlot.LEG_RIGHT),
	// Besonderes
	FLOATING(CosmeticCategory.SPECIAL, "Schwebende Objekte", AttachmentPoint.HEAD, CosmeticSlot.FLOATING),
	PARTICLES(CosmeticCategory.SPECIAL, "Partikeleffekte", AttachmentPoint.BODY, CosmeticSlot.AURA),
	ANIMATED(CosmeticCategory.SPECIAL, "Animationen", AttachmentPoint.HEAD, CosmeticSlot.FLOATING),
	COMPANION(CosmeticCategory.SPECIAL, "Begleiter", AttachmentPoint.LEFT_SHOULDER, CosmeticSlot.SHOULDER_LEFT),
	MOVING_DECORATION(CosmeticCategory.SPECIAL, "Bewegliche Deko", AttachmentPoint.BODY, CosmeticSlot.FLOATING);

	public final CosmeticCategory category;
	public final String label;
	public final AttachmentPoint defaultAttachment;
	private final CosmeticSlot[] defaultSlots;

	CosmeticType(CosmeticCategory category, String label, AttachmentPoint attachment, CosmeticSlot... slots) {
		this.category = category;
		this.label = label;
		this.defaultAttachment = attachment;
		this.defaultSlots = slots;
	}

	public Set<CosmeticSlot> defaultSlots() {
		EnumSet<CosmeticSlot> s = EnumSet.noneOf(CosmeticSlot.class);
		s.addAll(java.util.List.of(defaultSlots));
		return s;
	}

	public String id() {
		String n = name().toLowerCase(Locale.ROOT);
		return n.endsWith("_type") ? n.substring(0, n.length() - 5) : n;
	}

	public static CosmeticType byId(String id) {
		String n = id.toLowerCase(Locale.ROOT);
		for (CosmeticType t : values()) {
			if (t.id().equals(n)) {
				return t;
			}
		}
		return null;
	}
}
