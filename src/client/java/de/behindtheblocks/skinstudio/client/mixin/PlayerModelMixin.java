package de.behindtheblocks.skinstudio.client.mixin;

import de.behindtheblocks.skinstudio.client.render.CosmeticRenderData;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies a playing emote on top of the vanilla pose (blended in and out). Cosmetics follow automatically. */
@Mixin(value = PlayerModel.class, priority = 1500) // after other animation mods (e.g. Not Enough Animations)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void btbSkinStudio$emote(AvatarRenderState state, CallbackInfo ci) {
		try {
			btbSkinStudio$emoteImpl(state, ci);
		} catch (RuntimeException | LinkageError e) {
			de.behindtheblocks.skinstudio.client.Guard.report("emote pose", e);
		}
	}

	private void btbSkinStudio$emoteImpl(AvatarRenderState state, CallbackInfo ci) {
		PlayerModel model = (PlayerModel) (Object) this;
		float[] body = state.getData(CosmeticRenderData.BODY);
		if (body != null) {
			// head size and the thickness of arms and legs (length stays, so feet and hands keep their place)
			model.head.xScale = model.head.yScale = model.head.zScale = body[1];
			model.rightArm.xScale = model.rightArm.zScale = body[2];
			model.leftArm.xScale = model.leftArm.zScale = body[2];
			model.rightLeg.xScale = model.rightLeg.zScale = body[3];
			model.leftLeg.xScale = model.leftLeg.zScale = body[3];
		}
		float[] pose = state.getData(CosmeticRenderData.EMOTE);
		if (pose == null) {
			if (Boolean.TRUE.equals(state.getData(CosmeticRenderData.PREVIEW))) {
				// GUI previews: no idle arm sway – in a small still picture it only looks like jitter
				model.rightArm.xRot = 0;
				model.rightArm.zRot = 0.05f;
				model.leftArm.xRot = 0;
				model.leftArm.zRot = -0.05f;
			}
			return;
		}
		de.behindtheblocks.skinstudio.client.emote.EmotePoser.apply(model, pose);
	}
}
