package de.behindtheblocks.skinstudio.client.emote;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.emote.CustomEmote;
import de.behindtheblocks.skinstudio.emote.Emotes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The own emotes from the emote editor, one JSON file each in {@code config/btb_skinstudio/emotes}. Locally an
 * emote is known as {@code u_<file name>} – that id stays the same while it is edited, so it can sit on the
 * emote wheel. On the network its content id ({@code c_…}) is used.
 */
public final class CustomEmoteLibrary {
	private static final List<String> IDS = new ArrayList<>();
	private static boolean loaded;

	private CustomEmoteLibrary() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(BtbSkinStudio.MOD_ID).resolve("emotes");
	}

	public static synchronized void load() {
		for (String id : IDS) {
			Emotes.unregisterCustom(id);
		}
		IDS.clear();
		loaded = true;
		Path d = dir();
		if (!Files.isDirectory(d)) {
			return;
		}
		List<Path> files = new ArrayList<>();
		try (var s = Files.list(d)) {
			s.filter(p -> p.getFileName().toString().matches("[a-z0-9]{1,16}\\.json")).forEach(files::add);
		} catch (IOException e) {
			BtbSkinStudio.LOGGER.warn("Eigene Emotes nicht lesbar: {}", e.toString());
			return;
		}
		files.sort(Comparator.comparing(p -> {
			try {
				return Files.getLastModifiedTime(p).toMillis();
			} catch (IOException e) {
				return 0L;
			}
		}));
		for (Path f : files) {
			String key = f.getFileName().toString().replace(".json", "");
			try {
				CustomEmote e = CustomEmote.parse(Files.readString(f, StandardCharsets.UTF_8));
				String id = "u_" + key;
				Emotes.registerCustom(id, e);
				IDS.add(id);
			} catch (IOException | RuntimeException e) {
				BtbSkinStudio.LOGGER.warn("Eigenes Emote {} nicht lesbar: {}", f.getFileName(), e.toString());
			}
		}
	}

	/** Ids of the own emotes ({@code u_…}), oldest first. */
	public static synchronized List<String> ids() {
		if (!loaded) {
			load();
		}
		return List.copyOf(IDS);
	}

	public static boolean isOwn(String id) {
		return id != null && id.startsWith("u_");
	}

	/** Saves an emote; {@code id} null = a new one. Returns its id. */
	public static synchronized String save(String id, CustomEmote e) throws IOException {
		if (!loaded) {
			load();
		}
		String key = id == null ? newKey() : id.substring(2);
		if (!key.matches("[a-z0-9]{1,16}")) {
			throw new IOException("bad emote id");
		}
		Files.createDirectories(dir());
		Files.writeString(dir().resolve(key + ".json"), e.toJson(), StandardCharsets.UTF_8);
		String full = "u_" + key;
		Emotes.registerCustom(full, e.copy());
		if (!IDS.contains(full)) {
			IDS.add(full);
		}
		return full;
	}

	public static synchronized void delete(String id) {
		if (!isOwn(id)) {
			return;
		}
		try {
			Files.deleteIfExists(dir().resolve(id.substring(2) + ".json"));
		} catch (IOException ignored) {
			// the file is gone from the list anyway
		}
		Emotes.unregisterCustom(id);
		IDS.remove(id);
	}

	private static String newKey() {
		String key;
		do {
			key = Long.toString(System.currentTimeMillis(), 36).toLowerCase(Locale.ROOT) + Integer.toString((int) (Math.random() * 1296), 36);
		} while (Files.exists(dir().resolve(key + ".json")));
		return key.length() > 16 ? key.substring(0, 16) : key;
	}

	/** Display name of any emote: the own name of an editor emote, otherwise the translated one. */
	public static String name(String id) {
		CustomEmote c = Emotes.custom(id);
		if (c != null) {
			return c.name.isBlank() ? de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.emote_editor.unnamed") : c.name;
		}
		return de.behindtheblocks.skinstudio.util.Tr.t("btb.emote." + id);
	}
}
