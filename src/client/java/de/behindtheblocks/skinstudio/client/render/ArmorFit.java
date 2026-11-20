package de.behindtheblocks.skinstudio.client.render;

import de.behindtheblocks.skinstudio.cosmetic.CosmeticCategory;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps cosmetics and worn armour from fighting each other. Only cosmetics whose geometry actually reaches into
 * the space of a worn armour piece are left out (they would poke through or flicker). Everything with enough
 * distance stays visible, e.g. halos, floating crystals or the fire halo. Back items are moved one pixel
 * outward over a chestplate instead of being hidden, like the vanilla cape. Elytra replace wings and capes.
 */
public final class ArmorFit {
	/** Cube bounds per content hash: {minX, minY, minZ, maxX, maxY, maxZ} in Bedrock player space (px). */
	private static final Map<String, float[][]> BOUNDS = new ConcurrentHashMap<>();
	private static final float PUSH = 1.25f;

	// armour shells in Bedrock player space (body part boxes grown by the armour layer's inflation)
	// helmet: cap, sides, back and forehead band; the face stays open like on the vanilla helmets
	private static final float[][] HELMET = {{-5, 31, -5, 5, 33, 5}, {-5, 23, -5, -4, 33, 5}, {4, 23, -5, 5, 33, 5},
			{-5, 23, 3.5f, 5, 33, 5}, {-5, 30, -5, 5, 33, -4}};
	/** Fits tried before giving up: {scale, lift in px}. */
	private static final float[][] FITS = {{1.12f, 0}, {1.12f, 1.25f}};
	private static final float[][] CHESTPLATE = {{-5, 11, -3, 5, 25, 3}, {-9, 17, -3, -3, 25, 3}, {3, 17, -3, 9, 25, 3}};
	private static final float[][] LEGGINGS = {{-4.5f, 11.5f, -2.5f, 4.5f, 16, 2.5f}, {-4.5f, 2, -2.5f, 4.5f, 12.5f, 2.5f}};
	private static final float[][] BOOTS = {{-5, -1, -3, 5, 5.5f, 3}};

	private ArmorFit() {
	}

	public static List<ActiveCosmetic> apply(List<ActiveCosmetic> list, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		boolean h = worn(head), c = worn(chest), l = worn(legs), f = worn(feet);
		if (list.isEmpty() || (!h && !c && !l && !f)) {
			return list;
		}
		boolean elytra = c && chest.has(DataComponents.GLIDER);
		boolean plate = c && !elytra;
		List<ActiveCosmetic> out = new ArrayList<>(list.size());
		for (ActiveCosmetic a : list) {
			CosmeticDefinition d = a.asset().definition;
			if (d.category() == CosmeticCategory.BACK) {
				if (elytra && (d.type == CosmeticType.WINGS || d.type == CosmeticType.CAPE || d.type == CosmeticType.BACKPACK)) {
					continue;
				}
				if (plate) {
					// straps end up inside the chestplate, the pack itself sits on top of it
					Loadout.Adjust j = a.adjust();
					out.add(new ActiveCosmetic(a.asset(), a.baked(), a.color(), new Loadout.Adjust(j.x(), j.y(), j.z() + PUSH, j.scale())));
					continue;
				}
				out.add(a);
				continue;
			}
			if (plate && d.type == CosmeticType.COMPANION) {
				// shoulder pets sit on the pauldron instead of sinking into it
				Loadout.Adjust j = a.adjust();
				out.add(new ActiveCosmetic(a.asset(), a.baked(), a.color(), new Loadout.Adjust(j.x(), j.y() + PUSH, j.z(), j.scale())));
				continue;
			}
			if (h && (d.category() == CosmeticCategory.HEAD || d.category() == CosmeticCategory.HAIR_FACE)) {
				Loadout.Adjust j = a.adjust();
				switch (d.type) {
					// these hug the head or face and would poke through or vanish into the helmet
					case HAIRSTYLE, MASK, HELMET, EYEBROWS_TYPE -> {
					}
					// face items are worn over the open face of the helmet, a little larger so they lie on top
					case GLASSES, BEARD, MUSTACHE, FACE_ACCESSORY, HEADPHONES ->
							out.add(new ActiveCosmetic(a.asset(), a.baked(), a.color(), new Loadout.Adjust(j.x(), j.y() + 0.4f, j.z(), j.scale() * 1.09f)));
					case HALO_TYPE -> out.add(a);
					// hats, crowns, horns, flowers, decorations: sit on top of the helmet
					default -> out.add(new ActiveCosmetic(a.asset(), a.baked(), a.color(), new Loadout.Adjust(j.x(), j.y() + 1.25f, j.z(), j.scale() * 1.12f)));
				}
				continue;
			}
			float[][] b = bounds(a);
			if (!clash(b, h, plate, l, f)) {
				out.add(a);
				continue;
			}
			// try wearing it a little larger (and higher) over the armour, e.g. glasses over a helmet
			var ap = d.attachment;
			for (float[] fit : FITS) {
				float[][] moved = transform(b, ap.anchorX, ap.anchorY, ap.anchorZ, fit[0], fit[1]);
				if (!clash(moved, h, plate, l, f)) {
					Loadout.Adjust j = a.adjust();
					out.add(new ActiveCosmetic(a.asset(), a.baked(), a.color(), new Loadout.Adjust(j.x(), j.y() + fit[1], j.z(), j.scale() * fit[0])));
					break;
				}
			}
		}
		return out;
	}

	private static boolean clash(float[][] b, boolean h, boolean plate, boolean l, boolean f) {
		return (h && hits(b, HELMET)) || (plate && hits(b, CHESTPLATE)) || (l && hits(b, LEGGINGS)) || (f && hits(b, BOOTS));
	}

	private static float[][] transform(float[][] in, float ax, float ay, float az, float scale, float lift) {
		float[][] out = new float[in.length][];
		for (int i = 0; i < in.length; i++) {
			float[] c = in[i];
			out[i] = new float[]{ax + (c[0] - ax) * scale, ay + (c[1] - ay) * scale + lift, az + (c[2] - az) * scale,
					ax + (c[3] - ax) * scale, ay + (c[4] - ay) * scale + lift, az + (c[5] - az) * scale};
		}
		return out;
	}

	private static boolean worn(ItemStack s) {
		return s != null && !s.isEmpty();
	}

	private static boolean hits(float[][] cubes, float[][] shells) {
		for (float[] c : cubes) {
			for (float[] s : shells) {
				if (c[0] < s[3] - 0.05f && c[3] > s[0] + 0.05f && c[1] < s[4] - 0.05f && c[4] > s[1] + 0.05f && c[2] < s[5] - 0.05f && c[5] > s[2] + 0.05f) {
					return true;
				}
			}
		}
		return false;
	}

	/** Static bounds (rest pose, animations ignored) shifted by the cosmetic's offset and the player's adjustment. */
	private static float[][] bounds(ActiveCosmetic a) {
		float[][] base = BOUNDS.computeIfAbsent(a.asset().contentHash, k -> compute(a.asset().model.bones));
		CosmeticDefinition d = a.asset().definition;
		float ox = d.transform.offset[0] + a.adjust().x(), oy = d.transform.offset[1] + a.adjust().y(), oz = d.transform.offset[2] + a.adjust().z();
		if (ox == 0 && oy == 0 && oz == 0) {
			return base;
		}
		float[][] out = new float[base.length][];
		for (int i = 0; i < base.length; i++) {
			float[] c = base[i];
			out[i] = new float[]{c[0] + ox, c[1] + oy, c[2] + oz, c[3] + ox, c[4] + oy, c[5] + oz};
		}
		return out;
	}

	private static float[][] compute(List<GeoBone> bones) {
		List<float[]> out = new ArrayList<>();
		for (GeoBone b : bones) {
			for (GeoCube c : b.cubes) {
				float i = c.inflate;
				float x0 = Math.min(c.origin[0], c.origin[0] + c.size[0]), y0 = Math.min(c.origin[1], c.origin[1] + c.size[1]);
				float z0 = Math.min(c.origin[2], c.origin[2] + c.size[2]);
				float x1 = Math.max(c.origin[0], c.origin[0] + c.size[0]), y1 = Math.max(c.origin[1], c.origin[1] + c.size[1]);
				float z1 = Math.max(c.origin[2], c.origin[2] + c.size[2]);
				out.add(new float[]{x0 - i, y0 - i, z0 - i, x1 + i, y1 + i, z1 + i});
			}
		}
		return out.toArray(new float[0][]);
	}
}
