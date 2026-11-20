package de.behindtheblocks.skinstudio.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Render layer added to every player/mannequin renderer (registered via Fabric's layer callback). */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Render");
	private static boolean loggedError;

	public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		List<ActiveCosmetic> list = state.getData(CosmeticRenderData.COSMETICS);
		if (list == null || list.isEmpty() || state.isInvisible || state.isSpectator) {
			return;
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
		PlayerModel model = getParentModel();
		// skin layers covered by a worn cosmetic (pumpkin, hood …) or by armour: their 3D voxels would poke through
		java.util.Set<String> covered = new java.util.HashSet<>();
		for (ActiveCosmetic c : list) {
			if (!c.asset().id().startsWith("skinpart:")) {
				covered.addAll(c.asset().definition.hideLayers);
			}
		}
		if (!state.headEquipment.isEmpty()) {
			covered.add("hat");
		}
		if (!state.chestEquipment.isEmpty()) {
			covered.add("jacket");
			covered.add("sleeves");
		}
		if (!state.legsEquipment.isEmpty()) {
			covered.add("pants");
		}
		for (ActiveCosmetic c : list) {
			try {
				CosmeticDrawer.submit(poseStack, collector, light, overlay, model, state, c, covered);
			} catch (RuntimeException e) {
				// A broken cosmetic must never take the game down; report once.
				if (!loggedError) {
					loggedError = true;
					LOG.error("Cosmetic {} konnte nicht gezeichnet werden", c.asset().id(), e);
				}
			}
		}
	}
}
