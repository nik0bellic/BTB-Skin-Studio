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
				live.add(a.contentHash);
			}
			CosmeticTextures.retain(live);
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			library.reload();
			library.preloadCache();
		});

		ClientCosmeticSync.register();
		SkinStudioCommands.register();
		AutoTest.init();

		openWardrobe = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.btb_skinstudio.wardrobe", InputConstants.KEY_J, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openWardrobe.consumeClick()) {
				if (mc.gui.screen() == null) {
					SkinStudioCommands.openWardrobe(mc);
				}
			}
			CosmeticParticles.tick(mc);
		});
	}
}
