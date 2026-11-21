package de.behindtheblocks.skinstudio.server;

import de.behindtheblocks.skinstudio.emote.CustomEmote;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Emotes from the editor that players played on this server, kept in the world folder
 * ({@code btb_skinstudio/emotes/<id>.json}) so a BTB Cinematics replay can play them later – even after a restart.
 */
final class CustomEmoteStore {
	private static final int MAX_FILES = 5000;

	private CustomEmoteStore() {
	}

	private static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("btb_skinstudio").resolve("emotes");
	}

	static void save(MinecraftServer server, String id, String json) {
		if (!CustomEmote.isNetworkId(id)) {
			return;
		}
		try {
			Path d = dir(server);
			Path f = d.resolve(id + ".json");
			if (Files.exists(f)) {
				return;
			}
			Files.createDirectories(d);
			try (var s = Files.list(d)) {
				if (s.count() >= MAX_FILES) {
					return;
				}
			}
			Files.writeString(f, json, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// only needed for replays
		}
	}

	/** The JSON of a stored emote (also registers it), or null. */
	static String load(MinecraftServer server, String id) {
		if (!CustomEmote.isNetworkId(id)) {
			return null;
		}
		CustomEmote known = de.behindtheblocks.skinstudio.emote.Emotes.custom(id);
		if (known != null) {
			return known.toJson();
		}
		try {
			Path f = dir(server).resolve(id + ".json");
			if (!Files.isRegularFile(f)) {
				return null;
			}
			String json = Files.readString(f, StandardCharsets.UTF_8);
			CustomEmote e = CustomEmote.parse(json);
			if (!CustomEmote.idOf(e.toJson()).equals(id)) {
				return null;
			}
			de.behindtheblocks.skinstudio.emote.Emotes.registerCustom(id, e);
			return e.toJson();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
