package de.behindtheblocks.skinstudio.client.mixin;

import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the skin chosen in BTB Skin Studio everywhere the game asks for a player's skin
 * (world, first-person hand, player list). Cape and elytra of the original skin are kept.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void btbSkinStudio$skin(CallbackInfoReturnable<PlayerSkin> cir) {
		try {
			btbSkinStudio$skinImpl(cir);
		} catch (RuntimeException | LinkageError e) {
			de.behindtheblocks.skinstudio.client.Guard.report("skin override", e);
		}
	}

	private void btbSkinStudio$skinImpl(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		PlayerSkin original = cir.getReturnValue();
		PlayerSkin replaced = ClientSkins.override(self.getUUID(), original);
		if (replaced != original) {
			cir.setReturnValue(replaced);
		}
	}
}
