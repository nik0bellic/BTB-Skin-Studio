package de.behindtheblocks.skinstudio.client.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Tr;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Looks up the skin of a Bedrock player by gamertag through the public GeyserMC API: gamertag → XUID →
 * the skin GeyserMC stored when the player last joined a Geyser server (uploaded to textures.minecraft.net).
 * Bedrock has no public skin service of its own, so players who never joined a Geyser server are not found.
 */
public final class BedrockSkinLookup {
	private static final Pattern GAMERTAG = Pattern.compile("[A-Za-z0-9 ]{1,16}(#\\d{1,4})?");
	private static final String API = "https://api.geysermc.org/v2/";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NEVER).build();

	private BedrockSkinLookup() {
	}

	public static boolean validGamertag(String tag) {
		return tag != null && GAMERTAG.matcher(tag.trim()).matches();
	}

	public static CompletableFuture<SkinData> fetch(String rawTag) {
		String tag = rawTag == null ? "" : rawTag.trim();
		return CompletableFuture.supplyAsync(() -> {
			if (!validGamertag(tag)) {
				throw new FormatException(Tr.t("btb.err.gamertag", tag));
			}
			JsonObject xuid = getJson(API + "xbox/xuid/" + URLEncoder.encode(tag, StandardCharsets.UTF_8).replace("+", "%20"), tag);
			if (xuid == null || !xuid.has("xuid")) {
				throw new FormatException(Tr.t("btb.err.player_not_found", tag));
			}
			JsonObject skin = getJson(API + "skin/" + xuid.get("xuid").getAsString(), tag);
			if (skin == null || !skin.has("texture_id")) {
				throw new FormatException(Tr.t("btb.err.bedrock_no_skin", tag));
			}
			URI uri = URI.create("https://textures.minecraft.net/texture/" + skin.get("texture_id").getAsString());
			boolean slim = skin.has("is_steve") && !skin.get("is_steve").getAsBoolean();
			try {
				HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
						HttpResponse.BodyHandlers.ofByteArray());
				if (r.statusCode() != 200) {
					throw new FormatException(Tr.t("btb.err.player_download", r.statusCode()));
				}
				String file = tag.replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT) + ".png";
				return SkinIO.fromPng(r.body(), file, slim);
			} catch (java.io.IOException e) {
				throw new FormatException(Tr.t("btb.err.player_download", e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new FormatException(Tr.t("btb.err.player_download", "interrupted"));
			}
		}, CosmeticLibrary.IO);
	}

	/** GET a JSON object; null for "not found" (404 or empty answer). */
	private static JsonObject getJson(String url, String tag) {
		try {
			HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
					.header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 404 || r.statusCode() == 400) {
				return null;
			}
			if (r.statusCode() != 200) {
				throw new FormatException(Tr.t("btb.err.player_download", r.statusCode()));
			}
			var el = JsonParser.parseString(r.body());
			return el.isJsonObject() && el.getAsJsonObject().size() > 0 ? el.getAsJsonObject() : null;
		} catch (java.io.IOException e) {
			throw new FormatException(Tr.t("btb.err.player_download", e.getMessage()));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FormatException(Tr.t("btb.err.player_download", "interrupted"));
		} catch (RuntimeException e) {
			if (e instanceof FormatException f) {
				throw f;
			}
			throw new FormatException(Tr.t("btb.err.player_not_found", tag));
		}
	}
}
