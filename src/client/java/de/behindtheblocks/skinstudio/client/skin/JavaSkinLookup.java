package de.behindtheblocks.skinstudio.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.services.ProfileResult;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Looks up the skin of a Java Edition player by name through Mojang's public profile services (the same
 * services the game uses for player heads) and turns it into an own skin. Only the official texture host
 * is contacted; the downloaded image is validated like any imported PNG.
 */
public final class JavaSkinLookup {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NEVER).build();

	private JavaSkinLookup() {
	}

	public static boolean validName(String name) {
		return name != null && NAME.matcher(name.trim()).matches();
	}

	/** Finds the player, downloads the skin and returns it as an (unsaved) own skin named after the player. */
	public static CompletableFuture<SkinData> fetch(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		return CompletableFuture.supplyAsync(() -> {
			if (!validName(name)) {
				throw new FormatException(Tr.t("btb.err.player_name", name));
			}
			var services = Minecraft.getInstance().services();
			var found = services.profileRepository().findProfileByName(name)
					.orElseThrow(() -> new FormatException(Tr.t("btb.err.player_not_found", name)));
			ProfileResult result = services.sessionService().fetchProfile(found.id(), false);
			if (result == null) {
				throw new FormatException(Tr.t("btb.err.player_not_found", name));
			}
			MinecraftProfileTextures textures = services.sessionService().getTextures(result.profile());
			MinecraftProfileTexture skin = textures.skin();
			if (skin == null || skin.getUrl() == null) {
				throw new FormatException(Tr.t("btb.err.player_no_skin", found.name()));
			}
			URI uri = URI.create(skin.getUrl().replaceFirst("^http://", "https://"));
			if (!"textures.minecraft.net".equals(uri.getHost())) {
				throw new FormatException(Tr.t("btb.err.player_no_skin", found.name()));
			}
			try {
				HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
						HttpResponse.BodyHandlers.ofByteArray());
				if (r.statusCode() != 200) {
					throw new FormatException(Tr.t("btb.err.player_download", r.statusCode()));
				}
				boolean slim = "slim".equals(skin.getMetadata("model"));
				return SkinIO.fromPng(r.body(), found.name().toLowerCase(Locale.ROOT) + ".png", slim);
			} catch (java.io.IOException e) {
				throw new FormatException(Tr.t("btb.err.player_download", e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new FormatException(Tr.t("btb.err.player_download", "interrupted"));
			}
		}, CosmeticLibrary.IO);
	}
}
