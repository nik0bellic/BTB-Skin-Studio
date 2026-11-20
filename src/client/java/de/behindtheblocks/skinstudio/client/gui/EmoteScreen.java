package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.emote.ClientEmotes;
import de.behindtheblocks.skinstudio.client.emote.EmoteKeys;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Emote wheel: the five places (set in the wardrobe's Emotes tab) around a circle, the own figure next to it
 * showing the hovered emote. Click or keys 1–5 play it.
 */
public class EmoteScreen extends Screen {
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private String hovered;

	public EmoteScreen() {
		super(Component.literal(Tr.t("btb.ui.emotes.wheel")));
		preview.loopEmote = true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void play(int slot) {
		String id = EmoteKeys.slot(slot);
		if (id != null) {
			ClientEmotes.play(id);
		}
		onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, 0x70000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		int radius = Math.min(84, height / 3 - 10);
		int previewW = 120, gap = 40;
		int total = radius * 2 + 120 + gap + previewW;
		int cx = (width - total) / 2 + radius + 60, cy = height / 2;
		// ring
		ui.rounded(cx - radius - 56, cy - radius - 30, radius * 2 + 112, radius * 2 + 60, 24, t.dark ? 0xC81A1D24 : 0xD8FFFFFF);
		String hover = null;
		for (int i = 0; i < 5; i++) {
			double ang = Math.toRadians(-90 + i * 72);
			int sx = cx + (int) Math.round(Math.cos(ang) * radius), sy = cy + (int) Math.round(Math.sin(ang) * radius);
			String id = EmoteKeys.slot(i);
			String name = id == null ? "–" : Tr.t("btb.emote." + id);
			// full name, wrapped onto two lines if needed (never cut off)
			var lines = font.split(Component.literal(name), 96);
			int bw = 4;
			for (var l : lines) {
				bw = Math.max(bw, font.width(l));
			}
			bw += 26;
			int bh = 10 * lines.size() + 10;
			int bx = sx - bw / 2, by = sy - bh / 2;
			boolean hov = ui.hover(bx, by, bw, bh);
			if (hov) {
				hover = id;
			}
			ui.rounded(bx, by, bw, bh, 9, hov ? t.accent : t.surface);
			ui.rounded(bx + 4, by + bh / 2 - 6, 12, 12, 6, hov ? t.onAccent : t.field);
			ui.text(String.valueOf(i + 1), bx + 7, by + bh / 2 - 4, hov ? t.accent : t.text);
			int ly = by + 5;
			for (var l : lines) {
				g.text(font, l, bx + 20, ly, hov ? t.onAccent : t.text, false);
				ly += 10;
			}
			final int slot = i;
			ui.region(bx, by, bw, bh, () -> play(slot));
		}
		// centre: title and hint
		String title = hover != null ? Tr.t("btb.emote." + hover) : Tr.t("btb.ui.emotes.wheel");
		ui.textCentered(title, cx, cy - 8, t.text);
		for (var l : font.split(Component.literal(Tr.t("btb.ui.emotes.hint")), radius + 30)) {
			g.text(font, l, cx - font.width(l) / 2, cy + 6, t.textFaint, false);
			cy += 10;
		}
		cy = height / 2;
		// figure next to the wheel
		if (hover != null && !hover.equals(preview.emote)) {
			preview.playEmote(hover);
		}
		if (hover == null && hovered != null) {
			preview.emote = null;
		}
		hovered = hover;
		int px = cx + radius + 56 + gap - 10;
		ui.rounded(px, cy - radius - 30, previewW, radius * 2 + 60, 16, t.dark ? 0xC81A1D24 : 0xD8FFFFFF);
		preview.draw(g, px, cy - radius - 26, px + previewW, cy + radius + 26, cosmetics());
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private static List<ActiveCosmetic> cosmetics() {
		List<ActiveCosmetic> out = new ArrayList<>(ClientSkins.extrasFor(SkinLibrary.get().find(Wardrobe.get().skin())));
		out.addAll(ClientCosmeticSync.resolve(Wardrobe.get().equipped().entries(), false));
		return out;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k >= 49 && k <= 53) {
			play(k - 49);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	public void autotest(String[] args) {
		if (args[0].equals("play")) {
			play(Integer.parseInt(args[1]));
		} else if (args[0].equals("hover")) {
			preview.playEmote(EmoteKeys.slot(Integer.parseInt(args[1])));
		}
	}
}
