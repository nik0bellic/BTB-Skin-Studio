package de.behindtheblocks.skinstudio.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.util.Json;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The player's personal collection, stored permanently in {@code config/btb_skinstudio/wardrobe.json}:
 * what is equipped, favorites, recently used cosmetics and saved outfits.
 * Writes are atomic (temp file + move) and happen on the IO thread.
 */
public final class Wardrobe {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Wardrobe");
	public static final int MAX_RECENT = 24;

	private Loadout equipped = new Loadout();
	private final Set<String> favorites = new LinkedHashSet<>();
	private final List<String> recent = new ArrayList<>();
	private final Map<String, Loadout> outfits = new LinkedHashMap<>();
	/** Selected skin id; empty = the normal account skin. */
	private String skin = "";
	private final Set<String> favoriteSkins = new LinkedHashSet<>();
	private final List<Runnable> listeners = new ArrayList<>();

	private static Wardrobe instance;

	public static Wardrobe get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(BtbSkinStudio.MOD_ID).resolve("wardrobe.json");
	}

	private static Wardrobe load() {
		Wardrobe w = new Wardrobe();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return w;
		}
		try {
			JsonObject o = Json.parseObject(Files.readAllBytes(f));
			w.equipped = Loadout.fromJson(o.get("equipped"));
			w.favorites.addAll(Json.strings(o, "favorites"));
			w.recent.addAll(Json.strings(o, "recent"));
			w.skin = Json.string(o, "skin", "");
			w.favoriteSkins.addAll(Json.strings(o, "favorite_skins"));
			JsonElement outfits = o.get("outfits");
			if (outfits != null && outfits.isJsonObject()) {
				outfits.getAsJsonObject().entrySet().forEach(e -> w.outfits.put(e.getKey(), Loadout.fromJson(e.getValue())));
			}
			LOG.info("Garderobe geladen: {} ausgerüstet, {} Favoriten, {} Outfits", w.equipped.entries().size(), w.favorites.size(), w.outfits.size());
		} catch (Exception e) {
			LOG.warn("wardrobe.json ist fehlerhaft und wird als wardrobe.broken.json gesichert: {}", e.getMessage());
			try {
				Files.move(f, f.resolveSibling("wardrobe.broken.json"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
				// nothing else we can do
			}
		}
		return w;
	}

	public void save() {
		JsonObject o = new JsonObject();
		o.addProperty("format", 1);
		o.add("equipped", equipped.toJson());
		JsonArray fav = new JsonArray();
		favorites.forEach(fav::add);
		o.add("favorites", fav);
		JsonArray rec = new JsonArray();
		recent.forEach(rec::add);
		o.add("recent", rec);
		JsonObject out = new JsonObject();
		outfits.forEach((k, v) -> out.add(k, v.toJson()));
		o.add("outfits", out);
		o.addProperty("skin", skin);
		JsonArray fs = new JsonArray();
		favoriteSkins.forEach(fs::add);
		o.add("favorite_skins", fs);
		String json = Json.PRETTY.toJson(o);
		Path f = file();
		CosmeticLibrary.IO.execute(() -> {
			try {
				Files.createDirectories(f.getParent());
				Path tmp = f.resolveSibling("wardrobe.json.tmp");
				Files.writeString(tmp, json, StandardCharsets.UTF_8);
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				LOG.error("Garderobe konnte nicht gespeichert werden", e);
			}
		});
	}

	public Loadout equipped() {
		return equipped;
	}

	/** Replaces the equipped loadout, saves and notifies (sync to server). */
	public void setEquipped(Loadout l) {
		equipped = l.copy();
		changed();
	}

	public void markUsed(String id) {
		recent.remove(id);
		recent.addFirst(id);
		while (recent.size() > MAX_RECENT) {
			recent.removeLast();
		}
	}

	public boolean isFavorite(String id) {
		return favorites.contains(id);
	}

	public void toggleFavorite(String id) {
		if (!favorites.remove(id)) {
			favorites.add(id);
		}
		save();
	}

	public Set<String> favorites() {
		return favorites;
	}

	public List<String> recent() {
		return recent;
	}

	public Map<String, Loadout> outfits() {
		return outfits;
	}

	public void saveOutfit(String name, Loadout l) {
		outfits.put(name, l.copy());
		save();
	}

	public void deleteOutfit(String name) {
		outfits.remove(name);
		save();
	}

	public String skin() {
		return skin;
	}

	public void setSkin(String id) {
		skin = id == null ? "" : id;
		changed();
	}

	public Set<String> favoriteSkins() {
		return favoriteSkins;
	}

	public void toggleFavoriteSkin(String id) {
		if (!favoriteSkins.remove(id)) {
			favoriteSkins.add(id);
		}
		save();
	}

	public void addListener(Runnable r) {
		listeners.add(r);
	}

	public void changed() {
		save();
		listeners.forEach(Runnable::run);
	}
}
