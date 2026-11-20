package de.behindtheblocks.skinstudio.client.gui.ui;

import com.mojang.blaze3d.platform.NativeImage;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Ring textures for the emote wheel, drawn once: the full ring with five segments and one highlight per
 * segment. Anti-aliased edges, thin gaps between the segments.
 */
public final class WheelTexture {
	public static final int SIZE = 256;
	private static Identifier ring;
	private static final Identifier[] HIGHLIGHT = new Identifier[5];
	private static boolean dark;

	private WheelTexture() {
	}

	public static Identifier ring(Theme t) {
		ensure(t);
		return ring;
	}

	public static Identifier highlight(int segment, Theme t) {
		ensure(t);
		return HIGHLIGHT[segment];
	}

	/** Segment under a point relative to the centre (radius in the same unit as outer/inner), or -1. */
	public static int segmentAt(double dx, double dy, double inner, double outer) {
		double d = Math.sqrt(dx * dx + dy * dy);
		if (d < inner || d > outer) {
			return -1;
		}
		double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 36; // segment 0 centred at the top
		ang = ((ang % 360) + 360) % 360;
		return (int) (ang / 72) % 5;
	}

	private static void ensure(Theme t) {
		if (ring != null && dark == t.dark) {
			return;
		}
		dark = t.dark;
		int base = t.dark ? 0xE0222630 : 0xEEF4F5F8;
		int edge = t.dark ? 0x40FFFFFF : 0x30000000;
		ring = register("emote_wheel_ring", draw(-1, base, edge));
		for (int i = 0; i < 5; i++) {
			HIGHLIGHT[i] = register("emote_wheel_hl_" + i, draw(i, t.accent | 0xFF000000, 0));
		}
	}

	private static NativeImage draw(int only, int fill, int edge) {
		NativeImage img = new NativeImage(SIZE, SIZE, true);
		double c = SIZE / 2.0, outer = SIZE / 2.0 - 2, inner = SIZE * 0.2;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double dx = x + 0.5 - c, dy = y + 0.5 - c;
				double d = Math.sqrt(dx * dx + dy * dy);
				// coverage with a one-pixel soft edge
				double cov = Math.min(Math.max(0, Math.min(1, outer - d + 0.5)), Math.max(0, Math.min(1, d - inner + 0.5)));
				if (cov <= 0) {
					img.setPixel(x, y, 0);
					continue;
				}
				double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 36;
				ang = ((ang % 360) + 360) % 360;
				int seg = (int) (ang / 72) % 5;
				double toGap = Math.min(ang % 72, 72 - ang % 72) * Math.PI / 180 * d; // distance to the separator in pixels
				double gap = Math.max(0, Math.min(1, toGap - 1.5));
				if (only >= 0 && seg != only) {
					img.setPixel(x, y, 0);
					continue;
				}
				int color = fill;
				boolean rim = edge != 0 && (outer - d < 1.5 || d - inner < 1.5);
				if (rim) {
					color = blend(fill, edge);
				}
				int a = (int) (((color >>> 24) & 0xFF) * cov * gap);
				img.setPixel(x, y, (a << 24) | (color & 0xFFFFFF));
			}
		}
		return img;
	}

	private static int blend(int a, int b) {
		float ba = ((b >>> 24) & 0xFF) / 255f;
		int r = (int) (((a >> 16) & 0xFF) * (1 - ba) + ((b >> 16) & 0xFF) * ba);
		int g = (int) (((a >> 8) & 0xFF) * (1 - ba) + ((b >> 8) & 0xFF) * ba);
		int bl = (int) ((a & 0xFF) * (1 - ba) + (b & 0xFF) * ba);
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}

	private static Identifier register(String name, NativeImage img) {
		Identifier id = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "gui/" + name + (dark ? "_dark" : "_light"));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> name, img));
		return id;
	}
}
