package de.behindtheblocks.skinstudio.client.render;

import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;

import java.util.HashMap;
import java.util.Map;

/** A cosmetic resolved for rendering: asset, baked geometry and the chosen color (0 = default). */
public record ActiveCosmetic(CosmeticAsset asset, BakedModel baked, int color) {
	private static final Map<String, BakedModel> BAKED = new HashMap<>();

	/** Bakes once per content hash (render thread only). */
	public static ActiveCosmetic of(CosmeticAsset asset, int color) {
		BakedModel m = BAKED.computeIfAbsent(asset.contentHash, h -> BakedModel.bake(asset.model));
		return new ActiveCosmetic(asset, m, color);
	}

	/** Bakes an unsaved model (editor live preview); not cached. */
	public static ActiveCosmetic transient_(CosmeticAsset asset, int color) {
		return new ActiveCosmetic(asset, BakedModel.bake(asset.model), color);
	}

	public static void clearCache() {
		BAKED.clear();
	}
}
