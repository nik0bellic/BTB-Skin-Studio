package de.behindtheblocks.skinstudio.client.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The capes of the player's own Java account, through Mojang's official profile API (the same calls the
 * Minecraft launcher makes): list the owned capes, show one, or show none. The request is signed with the
 * login of the running game; the token is only sent to api.minecraftservices.com and never stored.
 */
public final class CapeService {
	private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NEVER).build();

	/** One owned cape: id, display name, texture address, currently shown. */
	public record Cape(String id, String alias, String url, boolean active) {
	}

	private CapeService() {
	}

	public static CompletableFuture<List<Cape>> list() {
		return CompletableFuture.supplyAsync(() -> {
			JsonObject o = send(HttpRequest.newBuilder(URI.create(PROFILE)).GET());
			List<Cape> out = new ArrayList<>();
			if (o != null && o.has("capes")) {
				for (var el : o.getAsJsonArray("capes")) {
					JsonObject c = el.getAsJsonObject();
					String url = c.has("url") ? c.get("url").getAsString().replaceFirst("^http://", "https://") : null;
					out.add(new Cape(c.get("id").getAsString(), c.has("alias") ? c.get("alias").getAsString() : "Cape",
							url, c.has("state") && "ACTIVE".equals(c.get("state").getAsString())));
				}
			}
			return out;
		}, CosmeticLibrary.IO);
	}

	/** Shows the given cape on the account (null = no cape). */
	public static CompletableFuture<Void> select(String capeId) {
		return CompletableFuture.runAsync(() -> {
			HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(PROFILE + "/capes/active"));
			if (capeId == null) {
				b.DELETE();
			} else {
				JsonObject body = new JsonObject();
				body.addProperty("capeId", capeId);
				b.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body.toString()));
			}
			send(b);
		}, CosmeticLibrary.IO);
	}

	/** Downloads a cape texture (only from the official texture host). */
	public static CompletableFuture<byte[]> texture(String url) {
		return CompletableFuture.supplyAsync(() -> {
			URI uri = URI.create(url);
			if (!"textures.minecraft.net".equals(uri.getHost())) {
				throw new FormatException("unexpected host");
			}
			try {
				HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
						HttpResponse.BodyHandlers.ofByteArray());
				if (r.statusCode() != 200) {
					throw new FormatException(Tr.t("btb.err.player_download", r.statusCode()));
				}
				return r.body();
			} catch (java.io.IOException e) {
				throw new FormatException(Tr.t("btb.err.player_download", e.getMessage()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new FormatException("interrupted");
			}
		}, CosmeticLibrary.IO);
	}

	private static JsonObject send(HttpRequest.Builder b) {
		String token = Minecraft.getInstance().getUser().getAccessToken();
		if (token == null || token.isBlank() || token.length() < 20) {
			throw new FormatException(Tr.t("btb.err.cape_login"));
		}
		try {
			HttpResponse<String> r = HTTP.send(b.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(15)).build(),
					HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() == 401 || r.statusCode() == 403) {
				throw new FormatException(Tr.t("btb.err.cape_login"));
			}
			if (r.statusCode() / 100 != 2) {
				throw new FormatException(Tr.t("btb.err.cape_failed", r.statusCode()));
			}
			String body = r.body();
			if (body == null || body.isBlank()) {
				return null;
			}
			var el = JsonParser.parseString(body);
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (java.io.IOException e) {
			throw new FormatException(Tr.t("btb.err.cape_failed", e.getMessage()));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new FormatException("interrupted");
		}
	}
}
