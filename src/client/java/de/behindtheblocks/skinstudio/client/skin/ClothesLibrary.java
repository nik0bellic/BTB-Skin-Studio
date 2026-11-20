package de.behindtheblocks.skinstudio.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.skin.Clothing;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.Colors;
import de.behindtheblocks.skinstudio.util.Json;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The built-in clothing set (own designs, see tools/generate_clothes.py) and the composition of the character:
 * base skin + worn pieces → one skin texture.
 */
public final class ClothesLibrary {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Clothes");
	public static final String CHARACTER_ID = "skin:my_character";

	public record Item(String id, Clothing.Slot slot, Map<String, String> names, int[] colors, int order,
					   byte[] wide, byte[] wideFixed, byte[] slim, byte[] slimFixed) {
		public String name(String lang) {
			String l = lang == null ? "en" : lang.toLowerCase(java.util.Locale.ROOT);
			l = l.length() > 2 ? l.substring(0, 2) : l;
			return names.getOrDefault(l, names.getOrDefault("en", id));
		}
	}

	private static Map<String, Item> items;

	private ClothesLibrary() {
	}

	public static synchronized Map<String, Item> items() {
		if (items == null) {
			items = load();
		}
		return items;
	}

	public static List<Item> forSlot(Clothing.Slot slot) {
		List<Item> out = new ArrayList<>();
		for (Item i : items().values()) {
			if (i.slot() == slot) {
				out.add(i);
			}
		}
		return out;
	}

	private static Map<String, Item> load() {
		Map<String, Item> out = new LinkedHashMap<>();
		Path root = FabricLoader.getInstance().getModContainer(BtbSkinStudio.MOD_ID)
				.flatMap(c -> c.findPath("assets/" + BtbSkinStudio.MOD_ID + "/builtin/btb_clothes")).orElse(null);
		if (root == null) {
			return out;
		}
		try {
			JsonObject o = Json.parseObject(Files.readAllBytes(root.resolve("clothes.json")));
			for (JsonElement e : o.getAsJsonArray("items")) {
				JsonObject it = e.getAsJsonObject();
				String id = Json.string(it, "id", "");
				Path d = root.resolve(id);
				Map<String, String> names = new LinkedHashMap<>();
				it.getAsJsonObject("name").entrySet().forEach(n -> names.put(n.getKey(), n.getValue().getAsString()));
				List<String> cs = Json.strings(it, "colors");
				int[] colors = cs.stream().mapToInt(Colors::parse).toArray();
				out.put(id, new Item(id, Clothing.Slot.valueOf(Json.string(it, "slot", "top").toUpperCase(Locale.ROOT)), names, colors,
						(int) Json.number(it, "order", 10), Files.readAllBytes(d.resolve("wide.png")), Files.readAllBytes(d.resolve("wide_fixed.png")),
						Files.readAllBytes(d.resolve("slim.png")), Files.readAllBytes(d.resolve("slim_fixed.png"))));
			}
		} catch (Exception ex) {
			LOG.error("Kleidung nicht lesbar", ex);
		}
		return out;
	}

	/** The base skin the clothes are put on (falls back to Steve). */
	public static SkinData base(String id) {
		SkinData s = SkinLibrary.get().find(id);
		if (s == null || ClientSkins.iconSource(s) == null || CHARACTER_ID.equals(s.id)) {
			s = SkinLibrary.get().find("vanilla:steve_wide");
		}
		return s;
	}

	/** Base skin with the given clothes as an (unsaved) own skin; null if no base texture is available. */
	public static SkinData compose(SkinData base, Map<String, Wardrobe.Cloth> worn) {
		byte[] png = base == null ? null : ClientSkins.iconSource(base);
		if (png == null) {
			return null;
		}
		List<Clothing.Layer> layers = new ArrayList<>();
		// with an own body the arm model follows the body choice, otherwise the base skin's
		boolean slim = base.slim;
		var body = worn.get(Clothing.Slot.SKIN.name());
		if (body != null && items().containsKey(body.id())) {
			slim = body.id().endsWith("_slim");
		}
		for (var e : worn.entrySet()) {
			Item it = items().get(e.getValue().id());
			if (it == null) {
				continue;
			}
			layers.add(new Clothing.Layer(slim ? it.slim() : it.wide(), slim ? it.slimFixed() : it.wideFixed(), e.getValue().color(), it.order(), it.slot()));
		}
		Map<String, String> names = new LinkedHashMap<>();
		names.put("de", "Mein Charakter");
		names.put("en", "My character");
		byte[] composed = layers.isEmpty() ? png : Clothing.compose(png, layers);
		var acc = body != null ? null : base.accessories;
		return new SkinData(CHARACTER_ID, names, slim, composed, acc, SkinData.Source.CUSTOM, "",
				SkinIO.write(CHARACTER_ID, names, slim, composed, acc));
	}
}
