package de.behindtheblocks.skinstudio.client.render;

import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;

import java.util.HashMap;
import java.util.Map;

/** A cosmetic resolved for rendering: asset, baked geometry, chosen color (0 = default) and personal adjustment. */
public record ActiveCosmetic(CosmeticAsset asset, BakedModel baked, int color, Loadout.Adjust adjust) {
	private static final Map<String, BakedModel> BAKED = new HashMap<>();

	public ActiveCosmetic {
		adjust = adjust == null ? Loadout.Adjust.NONE : adjust;
	}

	/** Bakes once per content hash (render thread only). */
	public static ActiveCosmetic of(CosmeticAsset asset, int color, Loadout.Adjust adjust) {
		BakedModel m = BAKED.computeIfAbsent(asset.contentHash, h -> BakedModel.bake(asset.model));
		return new ActiveCosmetic(asset, m, color, adjust);
	}

	public static ActiveCosmetic of(CosmeticAsset asset, int color) {
		return of(asset, color, Loadout.Adjust.NONE);
	}

	/** Bakes an unsaved model (editor live preview); not cached. */
	public static ActiveCosmetic transient_(CosmeticAsset asset, int color) {
		return new ActiveCosmetic(asset, BakedModel.bake(asset.model), color, Loadout.Adjust.NONE);
	}

	public static void clearCache() {
		BAKED.clear();
	}
}
