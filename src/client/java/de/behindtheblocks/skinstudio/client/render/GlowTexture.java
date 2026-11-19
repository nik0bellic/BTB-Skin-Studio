package de.behindtheblocks.skinstudio.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Soft radial gradient used for the purely visual light halo of glowing cosmetics (e.g. the torch). */
public final class GlowTexture {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "glow_halo");
	private static boolean created;

	private GlowTexture() {
	}

	public static Identifier id() {
		if (!created) {
			created = true;
			int size = 32;
			NativeImage img = new NativeImage(size, size, false);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float dx = (x + 0.5f) / size * 2 - 1, dy = (y + 0.5f) / size * 2 - 1;
					float d = (float) Math.sqrt(dx * dx + dy * dy);
					float a = Math.max(0, 1 - d);
					a = a * a * (3 - 2 * a) * 0.9f;
					img.setPixel(x, y, (Math.round(a * 255) << 24) | 0xFFFFFF);
				}
			}
			Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "btb glow halo", img));
		}
		return ID;
	}
}
