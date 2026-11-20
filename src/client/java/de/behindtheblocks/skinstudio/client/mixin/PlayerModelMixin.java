package de.behindtheblocks.skinstudio.client.mixin;

import de.behindtheblocks.skinstudio.client.render.CosmeticRenderData;
import de.behindtheblocks.skinstudio.emote.Emotes;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies a playing emote on top of the vanilla pose (blended in and out). Cosmetics follow automatically. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void btbSkinStudio$emote(AvatarRenderState state, CallbackInfo ci) {
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
			return;
		}
		float w = pose[Emotes.SIZE];
		apply(model.head, pose, Emotes.HEAD, w);
		apply(model.body, pose, Emotes.BODY, w);
		apply(model.rightArm, pose, Emotes.RIGHT_ARM, w);
		apply(model.leftArm, pose, Emotes.LEFT_ARM, w);
		apply(model.rightLeg, pose, Emotes.RIGHT_LEG, w);
		apply(model.leftLeg, pose, Emotes.LEFT_LEG, w);
	}

	private static void apply(ModelPart part, float[] pose, int i, float w) {
		part.xRot = mix(part.xRot, pose[i], w);
		part.yRot = mix(part.yRot, pose[i + 1], w);
		part.zRot = mix(part.zRot, pose[i + 2], w);
	}

	private static float mix(float vanilla, float deg, float w) {
		return Float.isNaN(deg) ? vanilla : vanilla + ((float) Math.toRadians(deg) - vanilla) * w;
	}
}
