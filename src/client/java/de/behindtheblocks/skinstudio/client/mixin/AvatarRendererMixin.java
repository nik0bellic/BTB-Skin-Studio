package de.behindtheblocks.skinstudio.client.mixin;

import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.render.CosmeticRenderData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Attaches the player's resolved cosmetics to the render state during extraction, so the
 * {@code CosmeticLayer} can draw them without touching the entity, and hides vanilla overlay
 * layers a worn cosmetic asks to hide (e.g. the hat layer under a helmet).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void btbSkinStudio$extract(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(entity instanceof AbstractClientPlayer player)) {
			state.setData(CosmeticRenderData.COSMETICS, null);
			return;
		}
		List<ActiveCosmetic> list = ClientCosmeticSync.activeFor(player.getUUID());
		state.setData(CosmeticRenderData.PLAYER, player.getUUID());
		state.setData(CosmeticRenderData.COSMETICS, list);
		for (ActiveCosmetic c : list) {
			for (String layer : c.asset().definition.hideLayers) {
				switch (layer) {
					case "hat" -> state.showHat = false;
					case "jacket" -> state.showJacket = false;
					case "sleeves" -> {
						state.showLeftSleeve = false;
						state.showRightSleeve = false;
					}
					case "pants" -> {
						state.showLeftPants = false;
						state.showRightPants = false;
					}
					default -> {
					}
				}
			}
		}
	}
}
