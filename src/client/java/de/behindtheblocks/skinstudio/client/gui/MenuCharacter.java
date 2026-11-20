package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.util.Tr;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bedrock-style character next to the main menu and the pause menu: the own figure with skin and all worn
 * cosmetics, the player name above it and a "Wardrobe" button below. It follows the mouse a little.
 * When the window is too narrow (or the option is off) only a small button in the top right corner is shown.
 */
public final class MenuCharacter {
	private MenuCharacter() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
				attach(client, screen, w, h);
			}
		});
	}

	private static void attach(Minecraft mc, Screen screen, int w, int h) {
		String label = Tr.t("btb.ui.wardrobe");
		Tooltip tip = Tooltip.create(Component.literal(Tr.t("btb.ui.wardrobe.tooltip")));
		// free area to the right of the centred vanilla buttons (they are 200–204 px wide)
		int zoneX = w / 2 + 112, zoneW = w - zoneX - 8;
		int boxW = Math.min(130, zoneW), boxH = Math.min(Math.round(h * 0.52f), boxW * 2);
		if (!ClientSettings.get().menuCharacter || boxW < 84 || boxH < 110) {
			int bw = mc.font.width(label) + 20;
			Screens.getWidgets(screen).add(Button.builder(Component.literal(label), b -> mc.gui.setScreen(new WardrobeScreen(screen)))
					.bounds(w - bw - 6, 6, bw, 20).tooltip(tip).build());
			return;
		}
		int x0 = zoneX + (zoneW - boxW) / 2;
		int y0 = Math.max(40, (h - boxH - 26) / 2 + 14);
		int y1 = y0 + boxH;
		int bw = Math.max(boxW - 10, mc.font.width(label) + 20);
		Screens.getWidgets(screen).add(Button.builder(Component.literal(label), b -> mc.gui.setScreen(new WardrobeScreen(screen)))
				.bounds(x0 + (boxW - bw) / 2, y1 + 4, bw, 20).tooltip(tip).build());

		PlayerPreview preview = new PlayerPreview();
		preview.pitch = -4;
		preview.scale = 1.0f;
		preview.lift = 0f;
		boolean[] dragging = {false};
		long[] lastDrag = {0};
		// drag on the figure turns it; afterwards it stays where it was put instead of following the mouse
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
			dragging[0] = e.x() >= x0 && e.x() < x0 + boxW && e.y() >= y0 - 2 && e.y() < y1;
			return true;
		});
		ScreenMouseEvents.afterMouseDrag(screen).register((s, e, dx, dy, consumed) -> {
			if (dragging[0]) {
				preview.drag(dx, dy);
				lastDrag[0] = System.currentTimeMillis();
				return true;
			}
			return consumed;
		});
		ScreenMouseEvents.afterMouseRelease(screen).register((s, e, consumed) -> {
			dragging[0] = false;
			return consumed;
		});
		ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) ->
				draw(mc, g, preview, x0, y0, boxW, y1, mouseX, mouseY, lastDrag[0] == 0));
	}

	private static void draw(Minecraft mc, GuiGraphicsExtractor g, PlayerPreview preview, int x0, int y0, int boxW, int y1, int mouseX, int mouseY, boolean follow) {
		// name tag above the figure
		String name = mc.getUser().getName();
		int cx = x0 + boxW / 2;
		int nw = mc.font.width(name) + 10;
		g.fill(cx - nw / 2, y0 - 16, cx + nw / 2, y0 - 4, 0x80000000);
		g.text(mc.font, name, cx - mc.font.width(name) / 2, y0 - 14, 0xFFFFFFFF, false);
		// soft shadow under the feet
		int sw = boxW / 3;
		for (int i = 0; i < 4; i++) {
			int a = 0x22 - i * 6;
			g.fill(cx - sw / 2 + i * 3, y1 - 6 + i, cx + sw / 2 - i * 3, y1 - 5 + i, a << 24);
		}
		// the figure turns a little towards the mouse, like in Bedrock
		if (follow) {
			float target = Math.max(-45, Math.min(45, (mouseX - cx) * 0.35f));
			preview.yaw += (target - preview.yaw) * 0.12f;
			preview.pitch = Math.max(-20, Math.min(20, (mouseY - (y0 + (y1 - y0) / 4)) * 0.12f));
		}
		try {
			preview.draw(g, x0, y0 - 2, x0 + boxW, y1, cosmetics());
		} catch (RuntimeException e) {
			// a broken cosmetic must never break the menu
		}
	}

	private static List<ActiveCosmetic> cosmetics() {
		List<ActiveCosmetic> out = new ArrayList<>(ClientSkins.extrasFor(SkinLibrary.get().find(Wardrobe.get().skin())));
		out.addAll(ClientCosmeticSync.resolve(Wardrobe.get().equipped().entries(), false));
		return out;
	}
}
