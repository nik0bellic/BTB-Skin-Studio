package de.behindtheblocks.skinstudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.gui.SkinStudioCommands;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.render.CosmeticLayer;
import de.behindtheblocks.skinstudio.client.render.CosmeticParticles;
import de.behindtheblocks.skinstudio.client.render.CosmeticTextures;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/** Client entrypoint: library, render layer, key, sync, particles, commands. */
public final class BtbSkinStudioClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "main"));
	private static KeyMapping openWardrobe;
	private static KeyMapping openEmotes;

	/** Name of the key that opens the emote wheel (for the wardrobe). */
	public static String emoteWheelKey() {
		return openEmotes == null ? "B" : openEmotes.getTranslatedKeyMessage().getString();
	}
	private static String lastLanguage = "";

	@Override
	public void onInitializeClient() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticLayer(avatar));
			}
		});

		CosmeticLibrary library = CosmeticLibrary.get();
		library.addListener(lib -> {
			ActiveCosmetic.clearCache();
			Set<String> live = new HashSet<>();
			for (CosmeticAsset a : lib.all()) {
				for (byte[] img : new byte[][]{a.texture(), a.emissive(), a.preview()}) {
					if (img != null) {
						live.add(CosmeticTextures.fileKey(img));
					}
				}
			}
			CosmeticTextures.retain(live);
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			de.behindtheblocks.skinstudio.util.Tr.setLanguage(CosmeticLibrary.language());
			library.reload();
			library.preloadCache();
		});

		// skins from resource packs (Bedrock Skins layout) follow resource pack changes
		net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
				net.minecraft.resources.Identifier.fromNamespaceAndPath(de.behindtheblocks.skinstudio.BtbSkinStudio.MOD_ID, "skin_packs"),
				(net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> de.behindtheblocks.skinstudio.client.skin.SkinLibrary.get().reload());
		ClientCosmeticSync.register();
		de.behindtheblocks.skinstudio.client.emote.ClientEmotes.register();
		SkinStudioCommands.register();
		AutoTest.init();
		de.behindtheblocks.skinstudio.client.gui.WardrobeScreen.registerMenuButtons();

		openWardrobe = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.btb_skinstudio.wardrobe", InputConstants.KEY_J, CATEGORY));
		openEmotes = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.btb_skinstudio.emotes", InputConstants.KEY_B, CATEGORY));
		de.behindtheblocks.skinstudio.client.emote.EmoteKeys.register(CATEGORY);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openEmotes.consumeClick()) {
				if (mc.gui.screen() == null && mc.player != null) {
					mc.gui.setScreen(new de.behindtheblocks.skinstudio.client.gui.EmoteScreen());
				}
			}
			while (openWardrobe.consumeClick()) {
				if (mc.gui.screen() == null) {
					SkinStudioCommands.openWardrobe(mc);
				}
			}
			CosmeticParticles.tick(mc);
			String lang = CosmeticLibrary.language();
			if (!lang.equals(lastLanguage)) {
				// follow language changes in the options; messages and labels switch immediately
				lastLanguage = lang;
				de.behindtheblocks.skinstudio.util.Tr.setLanguage(lang);
				if (CosmeticLibrary.get().revision() > 0) {
					de.behindtheblocks.skinstudio.client.skin.SkinLibrary.get().reload();
				}
			}
		});
	}
}
