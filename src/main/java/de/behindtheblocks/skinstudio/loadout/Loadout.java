package de.behindtheblocks.skinstudio.loadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticJson;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticSlot;
import de.behindtheblocks.skinstudio.util.Colors;
import de.behindtheblocks.skinstudio.util.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * What a player wears: an ordered list of cosmetic ids with an optional color each.
 * Only ids and colors are stored and synced, never model data.
 */
public final class Loadout {
	public static final int MAX_ENTRIES = 16;

	/**
	 * @param color ARGB tint, 0 = cosmetic default
	 * @param hash  content hash of the cosmetic as the owner has it (for multiplayer transfer), may be empty
	 */
	public record Entry(String id, int color, String hash, Adjust adjust) {
		public Entry {
			hash = hash == null ? "" : hash;
			adjust = adjust == null ? Adjust.NONE : adjust.clamped();
		}

		public Entry(String id, int color, String hash) {
			this(id, color, hash, Adjust.NONE);
		}

		public Entry withColor(int c) {
			return new Entry(id, c, hash, adjust);
		}

		public Entry withAdjust(Adjust a) {
			return new Entry(id, color, hash, a);
		}
	}

	/**
	 * Personal fine-tuning of one worn cosmetic: offset in pixels (Bedrock space, Y up) and uniform scale.
	 * Applied on top of the cosmetic's own transform and synced to other players.
	 */
	public record Adjust(float x, float y, float z, float scale) {
		public static final Adjust NONE = new Adjust(0, 0, 0, 1);
		public static final float MAX_OFFSET = 16;
		public static final float MIN_SCALE = 0.25f;
		public static final float MAX_SCALE = 3;

		public Adjust clamped() {
			float sc = Float.isFinite(scale) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) : 1;
			return new Adjust(c(x), c(y), c(z), sc);
		}

		private static float c(float v) {
			return Float.isFinite(v) ? Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, v)) : 0;
		}

		public boolean isNone() {
			return x == 0 && y == 0 && z == 0 && scale == 1;
		}
	}

	/** What happened when equipping. */
	public record EquipResult(boolean equipped, List<String> removed, String message) {
	}

	private final List<Entry> entries = new ArrayList<>();

	public Loadout() {
	}

	public Loadout(List<Entry> entries) {
		for (Entry e : entries) {
			if (this.entries.size() < MAX_ENTRIES && CosmeticJson.isValidId(e.id()) && find(e.id()) == null) {
				this.entries.add(e);
			}
		}
	}

	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public Entry find(String id) {
		for (Entry e : entries) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	public boolean contains(String id) {
		return find(id) != null;
	}

	/**
	 * Equips a cosmetic. Cosmetics sharing a slot with it are taken off (the newest wins),
	 * so e.g. a helmet replaces a hat but glasses stay.
	 *
	 * @param lookup resolves ids of already equipped cosmetics; unknown ones (not installed) are kept
	 */
	public EquipResult equip(CosmeticDefinition def, int color, String hash, Function<String, CosmeticDefinition> lookup) {
		List<String> removed = new ArrayList<>();
		Set<CosmeticSlot> slots = def.effectiveSlots();
		Entry existing = find(def.id);
		if (existing != null) {
			entries.set(entries.indexOf(existing), new Entry(def.id, color, hash, existing.adjust()));
			return new EquipResult(true, removed, null);
		}
		for (Entry e : new ArrayList<>(entries)) {
			CosmeticDefinition other = lookup.apply(e.id());
			if (other == null) {
				continue;
			}
			Set<CosmeticSlot> overlap = EnumSet.copyOf(slots.isEmpty() ? EnumSet.noneOf(CosmeticSlot.class) : slots);
			overlap.retainAll(other.effectiveSlots());
			if (!overlap.isEmpty()) {
				entries.remove(e);
				removed.add(e.id());
			}
		}
		if (entries.size() >= MAX_ENTRIES) {
			return new EquipResult(false, removed, "Maximal " + MAX_ENTRIES + " Cosmetics gleichzeitig");
		}
		entries.add(new Entry(def.id, color, hash));
		return new EquipResult(true, removed, null);
	}

	public boolean unequip(String id) {
		return entries.removeIf(e -> e.id().equals(id));
	}

	public void setColor(String id, int color) {
		Entry e = find(id);
		if (e != null) {
			entries.set(entries.indexOf(e), e.withColor(color));
		}
	}

	public void setAdjust(String id, Adjust adjust) {
		Entry e = find(id);
		if (e != null) {
			entries.set(entries.indexOf(e), e.withAdjust(adjust));
		}
	}

	public void clear() {
		entries.clear();
	}

	public Loadout copy() {
		return new Loadout(entries);
	}

	public JsonArray toJson() {
		JsonArray a = new JsonArray();
		for (Entry e : entries) {
			JsonObject o = new JsonObject();
			o.addProperty("id", e.id());
			if (e.color() != 0) {
				o.addProperty("color", Colors.format(e.color()));
			}
			if (!e.adjust().isNone()) {
				o.add("offset", Json.array(e.adjust().x(), e.adjust().y(), e.adjust().z()));
				o.addProperty("scale", Json.round(e.adjust().scale()));
			}
			a.add(o);
		}
		return a;
	}

	public static Loadout fromJson(JsonElement el) {
		List<Entry> list = new ArrayList<>();
		if (el != null && el.isJsonArray()) {
			for (JsonElement x : el.getAsJsonArray()) {
				if (x.isJsonPrimitive()) {
					list.add(new Entry(x.getAsString(), 0, ""));
				} else if (x.isJsonObject()) {
					JsonObject o = x.getAsJsonObject();
					int color = 0;
					try {
						String c = Json.string(o, "color", null);
						color = c == null ? 0 : Colors.parse(c);
					} catch (RuntimeException ignored) {
						// invalid color → default
					}
					Adjust adj = Adjust.NONE;
					try {
						float[] off = Json.vec(o, "offset", new float[3]);
						adj = new Adjust(off[0], off[1], off[2], Json.number(o, "scale", 1));
					} catch (RuntimeException ignored) {
						// invalid adjustment → default
					}
					list.add(new Entry(Json.string(o, "id", ""), color, "", adj));
				}
			}
		}
		return new Loadout(list);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Loadout l && l.entries.equals(entries);
	}

	@Override
	public int hashCode() {
		return entries.hashCode();
	}
}
