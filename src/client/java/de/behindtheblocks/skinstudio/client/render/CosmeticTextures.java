package de.behindtheblocks.skinstudio.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * GPU textures for cosmetics, one per (content hash, tint color, layer).
 * PNG decoding and tinting run on the IO thread; only the upload happens on the render thread.
 * Textures are cached (LRU) and released when the cosmetic disappears from the library.
 */
public final class CosmeticTextures {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Textures");
	private static final int MAX_TEXTURES = 384;

	public enum Layer {
		BASE, EMISSIVE, PREVIEW
	}

	private static final Map<String, Identifier> READY = new LinkedHashMap<>(64, 0.75f, true);
	private static final Set<String> PENDING = new HashSet<>();
	private static final Set<String> FAILED = new HashSet<>();

	private CosmeticTextures() {
	}

	/** @return the texture id if uploaded; otherwise starts loading and returns null (skip this frame). */
	public static Identifier get(CosmeticAsset asset, int color, Layer layer) {
		int tint = layer == Layer.BASE && asset.definition.tint.enabled ? (color == 0 ? asset.definition.tint.defaultColor : color) : 0xFFFFFFFF;
		byte[] png = switch (layer) {
			case BASE -> asset.texture();
			case EMISSIVE -> asset.emissive();
			case PREVIEW -> asset.preview();
		};
		if (png == null) {
			return null;
		}
		byte[] mask = layer == Layer.BASE && tint != 0xFFFFFFFF ? asset.tintMask() : null;
		// keyed by image content, so editing metadata (transform, name …) never re-uploads textures
		String key = fileKey(png) + (mask == null ? "" : "m" + fileKey(mask)) + "_" + Integer.toHexString(tint) + "_" + layer.name().toLowerCase(Locale.ROOT);
		Identifier id = READY.get(key);
		if (id != null || FAILED.contains(key) || PENDING.contains(key)) {
			return id;
		}
		PENDING.add(key);
		String label = asset.id() + " (" + layer + ")";
		CompletableFuture.supplyAsync(() -> decode(png, mask, tint), CosmeticLibrary.IO).whenCompleteAsync((image, error) -> {
			PENDING.remove(key);
			if (error != null || image == null) {
				FAILED.add(key);
				LOG.warn("Textur von {} nicht lesbar: {}", label, error == null ? "?" : error.getMessage());
				return;
			}
			Identifier tex = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "cosmetic/" + key);
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> label, image));
			READY.put(key, tex);
			evict();
		}, Minecraft.getInstance());
		return null;
	}

	private static NativeImage decode(byte[] png, byte[] maskPng, int tint) {
		try {
			NativeImage img = NativeImage.read(png);
			if (tint != 0xFFFFFFFF) {
				NativeImage mask = null;
				try {
					if (maskPng != null) {
						mask = NativeImage.read(maskPng);
					}
					int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
					for (int y = 0; y < img.getHeight(); y++) {
						for (int x = 0; x < img.getWidth(); x++) {
							int p = img.getPixel(x, y);
							float strength = 1;
							if (mask != null) {
								int mx = x * mask.getWidth() / img.getWidth(), my = y * mask.getHeight() / img.getHeight();
								strength = (mask.getPixel(mx, my) >>> 24) / 255f;
								if (strength <= 0) {
									continue;
								}
							}
							int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
							int nr = Math.round(r + (r * tr / 255f - r) * strength);
							int ng = Math.round(g + (g * tg / 255f - g) * strength);
							int nb = Math.round(b + (b * tb / 255f - b) * strength);
							img.setPixel(x, y, (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb);
						}
					}
				} finally {
					if (mask != null) {
						mask.close();
					}
				}
			}
			return img;
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static void evict() {
		Iterator<Map.Entry<String, Identifier>> it = READY.entrySet().iterator();
		while (READY.size() > MAX_TEXTURES && it.hasNext()) {
			Identifier id = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(id);
		}
	}

	private static final Map<byte[], String> FILE_KEYS = new java.util.WeakHashMap<>();

	/** Short content key of an image file (identity-cached; the arrays are immutable once loaded). */
	public static String fileKey(byte[] data) {
		return FILE_KEYS.computeIfAbsent(data, d -> Integer.toHexString(java.util.Arrays.hashCode(d)) + Integer.toHexString(d.length));
	}

	/** Releases textures whose image is no longer used by any known cosmetic (render thread, after a reload). */
	public static void retain(Set<String> liveFileKeys) {
		Iterator<Map.Entry<String, Identifier>> it = READY.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Identifier> e = it.next();
			String k = e.getKey();
			int cut = k.indexOf('_');
			int m = k.indexOf('m');
			String file = k.substring(0, m >= 0 && m < cut ? m : cut);
			if (!liveFileKeys.contains(file)) {
				Minecraft.getInstance().getTextureManager().release(e.getValue());
				it.remove();
			}
		}
		FAILED.clear();
	}
}
