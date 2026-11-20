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

import java.util.List;
import java.util.UUID;

/**
 * Attaches the player's resolved cosmetics to the render state during extraction, so the
 * {@code CosmeticLayer} can draw them without touching the entity, and hides vanilla overlay
 * layers a worn cosmetic asks to hide (e.g. the hat layer under a helmet).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void btbSkinStudio$extract(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		try {
			btbSkinStudio$extractImpl(entity, state, partialTicks, ci);
		} catch (RuntimeException | LinkageError e) {
			de.behindtheblocks.skinstudio.client.Guard.report("player render state", e);
		}
	}

	private void btbSkinStudio$extractImpl(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		UUID id;
		boolean puppet = false;
		if (entity instanceof AbstractClientPlayer p) {
			id = p.getUUID();
		} else if (entity instanceof net.minecraft.world.entity.decoration.Mannequin m && (id = profileId(m)) != null) {
			// a mannequin wearing a player's profile (e.g. a BTB Cinematics replay puppet) shows that player's look
			puppet = true;
			var skin = de.behindtheblocks.skinstudio.client.skin.ClientSkins.skinOf(id);
			if (skin != null) {
				state.skin = de.behindtheblocks.skinstudio.client.skin.ClientSkins.toPlayerSkin(skin, state.skin, id.hashCode());
			}
		} else {
			state.setData(CosmeticRenderData.COSMETICS, null);
			return;
		}
		List<ActiveCosmetic> list = hiddenByArmor(ClientCosmeticSync.activeFor(id), state);
		var held = puppet ? de.behindtheblocks.skinstudio.client.emote.ClientEmotes.puppetItem(id) : de.behindtheblocks.skinstudio.client.emote.ClientEmotes.item(id);
		if (held != null) {
			// the emote's item (pickaxe, cookie …) replaces the held item while it plays
			state.rightHandItemStack = held;
			net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForLiving(state.rightHandItemState, held,
					net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
		}
		state.setData(CosmeticRenderData.PLAYER, id);
		state.setData(CosmeticRenderData.FACE_Y, de.behindtheblocks.skinstudio.client.skin.ClientSkins.faceOffset(
				de.behindtheblocks.skinstudio.client.skin.ClientSkins.skinOf(id)));
		state.setData(CosmeticRenderData.EMOTE, puppet ? de.behindtheblocks.skinstudio.client.emote.ClientEmotes.puppetPose(id)
				: de.behindtheblocks.skinstudio.client.emote.ClientEmotes.pose(id));
		var body = ClientCosmeticSync.bodyFor(id);
		state.setData(CosmeticRenderData.BODY, body == null ? null : new float[]{body.height(), body.head(), body.arms(), body.legs()});
		if (body != null) {
			state.scale *= body.height(); // visual only, the hitbox stays the same
		}
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

	/** UUID of the player profile a mannequin wears, or null (default mannequin / no id). */
	private static UUID profileId(net.minecraft.world.entity.decoration.Mannequin m) {
		try {
			var profile = m.getProfile();
			UUID id = profile == null ? null : profile.partialProfile().id();
			return id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) ? null : id;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static List<ActiveCosmetic> hiddenByArmor(List<ActiveCosmetic> list, AvatarRenderState state) {
		if (!ClientSettings.get().hideUnderArmor) {
			return list;
		}
		return de.behindtheblocks.skinstudio.client.render.ArmorFit.apply(list, state.headEquipment, state.chestEquipment, state.legsEquipment, state.feetEquipment);
	}
}
