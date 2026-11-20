package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.emote.ClientEmotes;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.emote.Emotes;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Emote picker: hover shows the emote on the own figure, click plays it in the world. Keys 1–9/0 work too. */
public class EmoteScreen extends Screen {
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private String hovered;

	public EmoteScreen() {
		super(Component.literal(Tr.t("btb.ui.emotes")));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void play(String id) {
		ClientEmotes.play(id);
		onClose();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, 0x66000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		List<Emotes.Emote> all = Emotes.all();
		int cols = 5, bw = 74, bh = 22, gap = 4;
		int rows = (all.size() + cols - 1) / cols;
		int gridW = cols * bw + (cols - 1) * gap, previewW = 110;
		int w = gridW + previewW + 30, h = Math.max(rows * (bh + gap) + 40, 170);
		int x = (width - w) / 2, y = (height - h) / 2;
		ui.glass(x, y, w, h, 12);
		ui.title(Tr.t("btb.ui.emotes"), x + 12, y + 10, t.text);
		ui.text(Tr.t("btb.ui.emotes.hint"), x + 12, y + h - 14, t.textFaint);
		String hover = null;
		for (int i = 0; i < all.size(); i++) {
			Emotes.Emote e = all.get(i);
			int bx = x + 12 + (i % cols) * (bw + gap), by = y + 28 + (i / cols) * (bh + gap);
			String key = i < 10 ? ((i + 1) % 10) + "  " : "";
			if (ui.hover(bx, by, bw, bh)) {
				hover = e.id();
			}
			ui.button(bx, by, bw, bh, key + Tr.t("btb.emote." + e.id()), null, Ui.Style.SECONDARY, true, () -> play(e.id()), null);
		}
		if (hover != null && !hover.equals(hovered)) {
			preview.playEmote(hover);
		} else if (hover != null && preview.emote == null) {
			preview.playEmote(hover);
		}
		hovered = hover;
		int px = x + w - previewW - 8;
		ui.rounded(px, y + 8, previewW, h - 16, 8, t.stage);
		preview.draw(g, px, y + 10, px + previewW, y + h - 10, cosmetics());
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
		if (k >= 48 && k <= 57) {
			int i = k == 48 ? 9 : k - 49;
			List<Emotes.Emote> all = Emotes.all();
			if (i < all.size()) {
				play(all.get(i).id());
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	public void autotest(String[] args) {
		if (args[0].equals("play")) {
			play(args[1]);
		}
	}
}
