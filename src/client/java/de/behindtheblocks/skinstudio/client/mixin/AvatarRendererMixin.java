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

import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticCategory;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
		List<ActiveCosmetic> list = hiddenByArmor(ClientCosmeticSync.activeFor(player.getUUID()), state);
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

	/**
	 * Worn armour wins: cosmetics in the same body area would poke through it, so they are left out while
	 * the armour piece is worn (helmet: head and face, chestplate: torso and arms, leggings: legs,
	 * boots: feet, elytra: wings and cape). Halos float above the helmet and stay.
	 */
	private static List<ActiveCosmetic> hiddenByArmor(List<ActiveCosmetic> list, AvatarRenderState state) {
		if (list.isEmpty() || !ClientSettings.get().hideUnderArmor) {
			return list;
		}
		boolean head = worn(state.headEquipment), chest = worn(state.chestEquipment);
		boolean legs = worn(state.legsEquipment), feet = worn(state.feetEquipment);
		boolean elytra = chest && state.chestEquipment.has(DataComponents.GLIDER);
		if (!head && !chest && !legs && !feet) {
			return list;
		}
		List<ActiveCosmetic> out = new ArrayList<>(list.size());
		for (ActiveCosmetic c : list) {
			CosmeticDefinition d = c.asset().definition;
			CosmeticCategory cat = d.category();
			boolean hide = switch (cat) {
				case HEAD, HAIR_FACE -> head && d.type != CosmeticType.HALO_TYPE;
				case TORSO, ARMS -> chest && !elytra;
				case LEGS -> d.type == CosmeticType.BOOTS ? feet : legs;
				case BACK -> elytra && (d.type == CosmeticType.WINGS || d.type == CosmeticType.CAPE);
				default -> false;
			};
			if (!hide) {
				out.add(c);
			}
		}
		return out;
	}

	private static boolean worn(ItemStack stack) {
		return stack != null && !stack.isEmpty();
	}
}
