package de.behindtheblocks.skinstudio.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import java.util.List;
import java.util.UUID;

/** Extra data attached to {@code AvatarRenderState} via Fabric's render-state data API. */
public final class CosmeticRenderData {
	/** Cosmetics to draw for this avatar (set during extraction, or by a preview screen). */
	public static final RenderStateDataKey<List<ActiveCosmetic>> COSMETICS = RenderStateDataKey.create(() -> "btb_skinstudio:cosmetics");
	/** Pose of a playing emote (see Emotes.sample) or absent. */
	/** Body proportions {height, head, arms, legs} or absent. */
	public static final RenderStateDataKey<float[]> BODY = RenderStateDataKey.create(() -> "btb_skinstudio:body");
	public static final RenderStateDataKey<float[]> EMOTE = RenderStateDataKey.create(() -> "btb_skinstudio:emote");
	/** How many pixels the skin's eyes sit below Steve's (row 4); glasses and face parts follow. */
	public static final RenderStateDataKey<Float> FACE_Y = RenderStateDataKey.create(() -> "btb_skinstudio:face_y");
	public static final RenderStateDataKey<UUID> PLAYER = RenderStateDataKey.create(() -> "btb_skinstudio:player");
	/** Set by preview screens: always draw, also when other players' cosmetics are hidden. */
	public static final RenderStateDataKey<Boolean> PREVIEW = RenderStateDataKey.create(() -> "btb_skinstudio:preview");

	private CosmeticRenderData() {
	}
}
