package de.behindtheblocks.skinstudio.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;
import de.behindtheblocks.skinstudio.util.Tr;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Online skin pack catalogs in the format used by the "Bedrock Skin Packs" resource pack for the Bedrock Skins
 * mod: an index ({"packs": [{id, name, description, downloadURI, imageUrl}]}) whose packs are Bedrock skin pack
 * zips. Catalogs come from the settings and from resource packs ({@code assets/brand/store_categories.json}).
 * Packs are only downloaded when the player asks for it and are stored in {@code skin_packs/}.
 */
public final class SkinPackCatalog {
	public record Entry(String id, String name, String description, String download, String image) {
	}

	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,80}");
	private static final long MAX_PACK = 32L * 1024 * 1024;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final Map<String, Identifier> ICONS = new ConcurrentHashMap<>();
	private static final Set<String> ICON_PENDING = ConcurrentHashMap.newKeySet();

	private SkinPackCatalog() {
	}

	/** Index URLs: settings first, then every resource pack that declares a skin pack store. */
	public static List<String> catalogUrls() {
		Set<String> out = new LinkedHashSet<>(ClientSettings.get().skinPackCatalogs);
		try {
			var rm = Minecraft.getInstance().getResourceManager();
			for (var res : rm.getResourceStack(Identifier.fromNamespaceAndPath("brand", "store_categories.json"))) {
				try (var in = res.open()) {
					JsonElement root = Json.parse(in.readAllBytes());
					if (root.isJsonArray()) {
						for (JsonElement e : root.getAsJsonArray()) {
							JsonObject o = e.getAsJsonObject();
							if ("skin_packs".equals(Json.string(o, "targetDirectoryName", "")) && Json.string(o, "indexUrl", "").startsWith("https://")) {
								out.add(Json.string(o, "indexUrl", ""));
							}
						}
					}
				} catch (Exception ignored) {
					// a broken store file in some resource pack is skipped
				}
			}
		} catch (RuntimeException ignored) {
			// no resource manager yet
		}
		return new ArrayList<>(out);
	}

	public static CompletableFuture<List<Entry>> load() {
		List<String> urls = catalogUrls();
		return CompletableFuture.supplyAsync(() -> {
			List<Entry> out = new ArrayList<>();
			Set<String> seen = new LinkedHashSet<>();
			List<String> errors = new ArrayList<>();
			for (String url : urls) {
				try {
					JsonObject root = Json.parseObject(get(url, 4L * 1024 * 1024));
					for (JsonElement e : root.getAsJsonArray("packs")) {
						JsonObject o = e.getAsJsonObject();
						String id = Json.string(o, "id", "").toLowerCase(Locale.ROOT);
						String dl = Json.string(o, "downloadURI", "");
						if (!ID.matcher(id).matches() || !dl.startsWith("https://") || !seen.add(id)) {
							continue;
						}
						out.add(new Entry(id, Json.string(o, "name", id), Json.string(o, "description", ""), dl, Json.string(o, "imageUrl", "")));
					}
				} catch (Exception e) {
					errors.add(e.getMessage());
				}
			}
			if (out.isEmpty() && !errors.isEmpty()) {
				throw new FormatException(Tr.t("btb.ui.catalog.offline", errors.getFirst()));
			}
			return out;
		}, CosmeticLibrary.IO);
	}

	public static Path target(Entry e) {
		return SkinLibrary.get().sharedSkinPacksDir.resolve(e.id() + ".zip");
	}

	public static boolean installed(Entry e) {
		return Files.exists(target(e));
	}

	/** Downloads one pack, checks that it is a readable, unencrypted skin pack and stores it (IO thread). */
	public static int downloadNow(Entry e) throws Exception {
		byte[] zip = get(e.download(), MAX_PACK);
		ArchiveFiles files = ArchiveFiles.readZip(zip);
		List<PackProblem> problems = new ArrayList<>();
		int skins = SkinIO.readBedrockPack(files, e.id(), problems).size();
		if (skins == 0) {
			throw new FormatException(problems.isEmpty() ? Tr.t("btb.err.no_skins_json") : problems.getFirst().message());
		}
		Path target = target(e);
		Files.createDirectories(target.getParent());
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		Files.write(tmp, zip);
		Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return skins;
	}

	public static void remove(Entry e) throws Exception {
		Files.deleteIfExists(target(e));
	}

	/** Pack image as GUI texture (loaded in the background, null until ready). */
	public static Identifier icon(Entry e) {
		Identifier id = ICONS.get(e.id());
		if (id != null || e.image().isEmpty() || !ICON_PENDING.add(e.id())) {
			return id;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				return NativeImage.read(get(e.image(), 2L * 1024 * 1024));
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((img, err) -> {
			if (err != null || img == null) {
				return;
			}
			Identifier tex = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "catalog/" + e.id().replaceAll("[^a-z0-9_.-]", "_"));
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "skin pack icon " + e.id(), img));
			ICONS.put(e.id(), tex);
		}, Minecraft.getInstance());
		return null;
	}

	/** github.com/<owner>/<repo>/raw/<ref>/<path> → raw.githubusercontent.com/<owner>/<repo>/<ref>/<path> (no redirect). */
	static String direct(String url) {
		var m = java.util.regex.Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)/raw/(.+)$").matcher(url);
		return m.matches() ? "https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2) + "/" + m.group(3) : url;
	}

	private static byte[] get(String url, long max) throws Exception {
		URI uri = URI.create(direct(url).replace(" ", "%20"));
		if (!"https".equals(uri.getScheme())) {
			throw new FormatException("https only: " + url);
		}
		HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
				.header("User-Agent", "BTB-Skin-Studio/" + BtbSkinStudio.version()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 200) {
			throw new FormatException("HTTP " + r.statusCode() + " – " + uri.getHost());
		}
		if (r.body().length > max) {
			throw new FormatException(Tr.t("btb.ui.catalog.too_big"));
		}
		return r.body();
	}
}
