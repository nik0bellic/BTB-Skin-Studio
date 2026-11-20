package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.skin.JavaSkinLookup;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** "Find a Java player": type a player name, see the skin in 3D and take it over as an own skin. */
public class PlayerSkinScreen extends Screen {
	private final Screen parent;
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private EditBox nameBox;
	private SkinData found;
	private boolean busy;
	private String status;
	private boolean statusError;
	private boolean dragging;

	public PlayerSkinScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.ui.player.title")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.fit(this);
		String old = nameBox == null ? "" : nameBox.getValue();
		nameBox = new EditBox(font, 0, 0, 100, 12, Component.literal(Tr.t("btb.ui.player.name")));
		nameBox.setBordered(false);
		nameBox.setMaxLength(16);
		nameBox.setHint(Component.literal(Tr.t("btb.ui.player.hint")));
		nameBox.setValue(old);
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
	}

	private void search() {
		String name = nameBox.getValue().trim();
		if (busy || !JavaSkinLookup.validName(name)) {
			status = Tr.t("btb.err.player_name", name);
			statusError = true;
			return;
		}
		busy = true;
		found = null;
		status = Tr.t("btb.ui.player.searching", name);
		statusError = false;
		JavaSkinLookup.fetch(name).whenCompleteAsync((s, err) -> {
			busy = false;
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				status = c.getMessage() == null ? c.toString() : c.getMessage();
				statusError = true;
				return;
			}
			found = s;
			status = Tr.t("btb.ui.player.found", s.name("en"));
		}, Minecraft.getInstance());
	}

	private void take(boolean wear) {
		SkinData s = found;
		if (s == null) {
			return;
		}
		busy = true;
		CompletableFuture.runAsync(() -> {
			try {
				SkinLibrary.get().saveCustom(s);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).thenCompose(v -> SkinLibrary.get().reload()).whenCompleteAsync((v, err) -> {
			busy = false;
			if (err != null) {
				status = Tr.t("btb.ui.error", err.getMessage());
				statusError = true;
				return;
			}
			if (wear) {
				Wardrobe.get().setSkin(s.id);
			}
			status = Tr.t(wear ? "btb.ui.toast.skin_worn" : "btb.ui.player.saved", s.name("en"));
			statusError = false;
		}, Minecraft.getInstance());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(0, 0, width, height, Theme.current().scrim);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		int m = 8, top = 32, bottom = height - m;
		ui.iconButton(m, 7, 18, Icon.BACK, false, this::onClose, Tr.t("btb.ui.back"));
		ui.title(Tr.t("btb.ui.player.title"), m + 24, 12, t.text);

		int panelW = Math.min(260, width / 2);
		ui.glass(m, top, panelW, bottom - top, 10);
		int x = m + 10, w = panelW - 20, y = top + 10;
		for (var line : font.split(Component.literal(Tr.t("btb.ui.player.info")), w)) {
			g.text(font, line, x, y, t.textMuted, false);
			y += 10;
		}
		y += 6;
		ui.rounded(x, y, w, 18, 6, t.field);
		nameBox.setX(x + 6);
		nameBox.setY(y + 5);
		nameBox.setWidth(w - 12);
		y += 24;
		String go = Tr.t("btb.ui.player.search");
		ui.button(x, y, font.width(go) + 30, 18, go, Icon.SEARCH, Ui.Style.PRIMARY, !busy, this::search, null);
		y += 26;
		if (status != null) {
			for (var line : font.split(Component.literal(status), w)) {
				g.text(font, line, x, y, statusError ? t.danger : t.textMuted, false);
				y += 10;
			}
		}
		if (found != null) {
			y += 8;
			String wear = Tr.t("btb.ui.player.wear"), save = Tr.t("btb.ui.player.save");
			int bw1 = font.width(wear) + 30, bw2 = font.width(save) + 30;
			ui.button(x, y, bw1, 18, wear, Icon.HANGER, Ui.Style.PRIMARY, !busy, () -> take(true), null);
			ui.button(x + bw1 + 4, y, bw2, 18, save, Icon.SAVE, Ui.Style.SECONDARY, !busy, () -> take(false), null);
		}
		// legal note: always shown in full, wrapped onto as many lines as needed
		var note = font.split(Component.literal(Tr.t("btb.ui.player.note")), w);
		int ny = bottom - 8 - note.size() * 10;
		for (var line : note) {
			g.text(font, line, x, ny, t.textFaint, false);
			ny += 10;
		}

		int sx = m + panelW + 6, sw = width - m - sx;
		ui.glass(sx, top, sw, bottom - top, 10);
		preview.skinOverride = found;
		preview.draw(g, sx + 4, top + 4, sx + sw - 4, bottom - 8, List.of());
		ui.region(sx + 4, top + 4, sw - 8, bottom - top - 12, () -> dragging = true);
		ui.hint(Tr.t("btb.ui.drag_to_rotate"), sx, sx + sw, bottom - 26);
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == 257 || event.key() == 335) && nameBox.isFocused()) {
			search();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (nameBox.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			preview.drag(dx, dy);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	public void autotest(String[] args) {
		switch (args[0]) {
			case "name" -> nameBox.setValue(args.length > 1 ? args[1] : "");
			case "search" -> search();
			default -> {
			}
		}
	}

	@Override
	public void removed() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.restore();
		super.removed();
	}
}
