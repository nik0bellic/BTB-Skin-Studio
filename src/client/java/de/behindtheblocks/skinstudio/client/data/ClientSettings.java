package de.behindtheblocks.skinstudio.client.data;

import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.util.Json;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client options ({@code config/btb_skinstudio/settings.json}). */
public final class ClientSettings {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Settings");

	public enum Theme {
		DARK, LIGHT
	}

	public boolean showOtherPlayers = true;
	public boolean particles = true;
	public boolean glowEffects = true;
	public boolean animations = true;
	/** Allow sending own (custom) cosmetics to other players on request. */
	public boolean shareOwnCosmetics = true;
	/** Download cosmetics of other players that are not installed locally. */
	public boolean downloadOthers = true;
	/** Render the outer skin layer as real 3D pixels. */
	public boolean skinLayers3d = false;
	/** Load the skin packs that come with an installed Bedrock edition (read-only, never copied). */
	public boolean bedrockBuiltinPacks = true;
	/** Show the own character next to the main menu and the pause menu. */
	public boolean menuCharacter = true;
	/** Leave out cosmetics that would poke through worn armour. */
	public boolean hideUnderArmor = true;
	/** Online skin pack catalogs (index files in the Bedrock Skin Packs format). */
	public java.util.List<String> skinPackCatalogs = new java.util.ArrayList<>(DEFAULT_CATALOGS);
	public static final java.util.List<String> DEFAULT_CATALOGS = java.util.List.of(
			"https://raw.githubusercontent.com/BrandonItaly/Skin-Packs/refs/heads/main/skin_packs.json");
	public Theme theme = Theme.DARK;

	private static ClientSettings instance;

	public static ClientSettings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(BtbSkinStudio.MOD_ID).resolve("settings.json");
	}

	private static ClientSettings load() {
		ClientSettings s = new ClientSettings();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return s;
		}
		try {
			JsonObject o = Json.parseObject(Files.readAllBytes(f));
			s.showOtherPlayers = Json.bool(o, "show_other_players", true);
			s.particles = Json.bool(o, "particles", true);
			s.glowEffects = Json.bool(o, "glow_effects", true);
			s.animations = Json.bool(o, "animations", true);
			s.shareOwnCosmetics = Json.bool(o, "share_own_cosmetics", true);
			s.downloadOthers = Json.bool(o, "download_others", true);
			s.skinLayers3d = Json.bool(o, "skin_layers_3d", false);
			s.bedrockBuiltinPacks = Json.bool(o, "bedrock_builtin_packs", true);
			s.menuCharacter = Json.bool(o, "menu_character", true);
			s.hideUnderArmor = Json.bool(o, "hide_under_armor", true);
			if (o.has("skin_pack_catalogs")) {
				s.skinPackCatalogs = new java.util.ArrayList<>(Json.strings(o, "skin_pack_catalogs"));
			}
			s.theme = "light".equals(Json.string(o, "theme", "dark")) ? Theme.LIGHT : Theme.DARK;
		} catch (Exception e) {
			LOG.warn("settings.json ist fehlerhaft, Standardwerte werden verwendet: {}", e.getMessage());
		}
		return s;
	}

	public void save() {
		JsonObject o = new JsonObject();
		o.addProperty("show_other_players", showOtherPlayers);
		o.addProperty("particles", particles);
		o.addProperty("glow_effects", glowEffects);
		o.addProperty("animations", animations);
		o.addProperty("share_own_cosmetics", shareOwnCosmetics);
		o.addProperty("download_others", downloadOthers);
		o.addProperty("skin_layers_3d", skinLayers3d);
		o.addProperty("bedrock_builtin_packs", bedrockBuiltinPacks);
		o.addProperty("menu_character", menuCharacter);
		o.addProperty("hide_under_armor", hideUnderArmor);
		com.google.gson.JsonArray cat = new com.google.gson.JsonArray();
		skinPackCatalogs.forEach(cat::add);
		o.add("skin_pack_catalogs", cat);
		o.addProperty("theme", theme == Theme.LIGHT ? "light" : "dark");
		String json = Json.PRETTY.toJson(o);
		Path f = file();
		de.behindtheblocks.skinstudio.client.library.CosmeticLibrary.IO.execute(() -> {
			try {
				Files.createDirectories(f.getParent());
				Files.writeString(f, json, StandardCharsets.UTF_8);
			} catch (Exception e) {
				LOG.warn("settings.json konnte nicht gespeichert werden: {}", e.getMessage());
			}
		});
	}
}
