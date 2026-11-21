package de.behindtheblocks.skinstudio.client.mixin;

import de.behindtheblocks.skinstudio.client.render.CosmeticRenderData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armour worn during an emote takes the same pose as the player model (the player model itself is posed in {@link PlayerModelMixin}). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void btbSkinStudio$armorEmote(HumanoidRenderState state, CallbackInfo ci) {
		if ((Object) this instanceof PlayerModel) {
			return;
		}
		try {
			float[] pose = state.getData(CosmeticRenderData.EMOTE);
			if (pose != null) {
				de.behindtheblocks.skinstudio.client.emote.EmotePoser.apply((HumanoidModel<?>) (Object) this, pose);
			}
		} catch (RuntimeException | LinkageError e) {
			de.behindtheblocks.skinstudio.client.Guard.report("armour emote pose", e);
		}
	}
}
