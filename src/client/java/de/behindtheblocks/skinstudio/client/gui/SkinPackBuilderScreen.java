package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinPackWriter;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Put together an own skin pack from any loaded skins (own, BTB, Bedrock packs): pick skins, name the pack,
 * keep it as a collection in {@code skin_packs/} and/or export it as a Bedrock {@code .mcpack}.
 */
public class SkinPackBuilderScreen extends Screen {
	private final Screen parent;
	private final Ui ui = new Ui();
	private final Set<String> chosen = new LinkedHashSet<>();
	private EditBox nameBox;
	private int scroll, scrollMax;
	private boolean busy;
	private String status;
	private boolean statusError;

	public SkinPackBuilderScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.ui.packbuilder.title")));
		this.parent = parent;
	}

	@Override
	protected void init() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.fit(this);
		String old = nameBox == null ? Tr.t("btb.ui.packbuilder.default_name") : nameBox.getValue();
		nameBox = new EditBox(font, 0, 0, 100, 12, Component.literal(Tr.t("btb.ui.packbuilder.name")));
		nameBox.setBordered(false);
		nameBox.setMaxLength(40);
		nameBox.setValue(old);
		addRenderableWidget(nameBox);
	}

	private List<SkinData> skins() {
		List<SkinData> out = new ArrayList<>();
		for (SkinData s : SkinLibrary.get().all()) {
			if (s.source != SkinData.Source.VANILLA && s.texture != null) {
				out.add(s);
			}
		}
		return out;
	}

	private List<SkinData> selection() {
		List<SkinData> out = new ArrayList<>();
		for (SkinData s : skins()) {
			if (chosen.contains(s.id)) {
				out.add(s);
			}
		}
		return out;
	}

	private void save(boolean export) {
		List<SkinData> sel = selection();
		String name = nameBox.getValue().trim();
		if (sel.isEmpty() || name.isEmpty()) {
			status = Tr.t("btb.ui.packbuilder.empty");
			statusError = true;
			return;
		}
		busy = true;
		CompletableFuture.supplyAsync(() -> {
			try {
				ArchiveFiles files = SkinPackWriter.write(name, sel);
				String id = SkinPackWriter.packId(name);
				if (export) {
					Path out = CosmeticLibrary.get().store.exports.resolve(id + ".mcpack");
					Files.createDirectories(out.getParent());
					Files.write(out, files.toZip());
					return out;
				}
				Path dir = SkinLibrary.get().skinPacksDir.resolve(id);
				PackStore.deleteRecursively(dir);
				for (String p : files.paths()) {
					Path target = dir.resolve(p);
					Files.createDirectories(target.getParent());
					Files.write(target, files.get(p));
				}
				return dir;
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((path, err) -> {
			busy = false;
			if (err != null) {
				status = Tr.t("btb.ui.error", err.getMessage());
				statusError = true;
				return;
			}
			statusError = false;
			status = Tr.t(export ? "btb.ui.packbuilder.exported" : "btb.ui.packbuilder.saved", name, path.getFileName());
			if (!export) {
				SkinLibrary.get().reload();
			}
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
		ui.title(Tr.t("btb.ui.packbuilder.title"), m + 24, 12, t.text);

		// left: settings and actions
		int panelW = Math.min(200, width / 3);
		ui.glass(m, top, panelW, bottom - top, 10);
		int x = m + 10, w = panelW - 20, y = top + 10;
		for (var line : font.split(Component.literal(Tr.t("btb.ui.packbuilder.info")), w)) {
			g.text(font, line, x, y, t.textMuted, false);
			y += 10;
		}
		y += 6;
		ui.text(Tr.t("btb.ui.packbuilder.name"), x, y, t.textFaint);
		y += 11;
		ui.rounded(x, y, w, 18, 6, t.field);
		nameBox.setX(x + 6);
		nameBox.setY(y + 5);
		nameBox.setWidth(w - 12);
		y += 26;
		ui.text(Tr.t("btb.ui.packbuilder.count", chosen.size()), x, y, t.text);
		y += 16;
		String save = Tr.t("btb.ui.packbuilder.save"), export = Tr.t("btb.ui.packbuilder.export");
		ui.button(x, y, w, 20, save, Icon.SAVE, Ui.Style.PRIMARY, !busy && !chosen.isEmpty(), () -> save(false), null);
		y += 24;
		ui.button(x, y, w, 20, export, Icon.EXPORT, Ui.Style.SECONDARY, !busy && !chosen.isEmpty(), () -> save(true), Tr.t("btb.ui.packbuilder.export.tooltip"));
		y += 24;
		ui.button(x, y, w, 20, Tr.t("btb.ui.packbuilder.open_exports"), Icon.FOLDER, Ui.Style.GHOST, true,
				() -> com.mojang.blaze3d.Blaze3D.openPath(CosmeticLibrary.get().store.exports), null);
		y += 30;
		if (status != null) {
			for (var line : font.split(Component.literal(status), w)) {
				g.text(font, line, x, y, statusError ? t.danger : t.textMuted, false);
				y += 10;
			}
		}

		// right: all skins as selectable cards
		int gx = m + panelW + 6, gw = width - m - gx;
		ui.glass(gx, top, gw, bottom - top, 10);
		List<SkinData> all = skins();
		int cw = 64, ch = 86, cols = Math.max(1, (gw - 16) / (cw + 6));
		int rows = (all.size() + cols - 1) / cols;
		int contentH = rows * (ch + 6);
		scrollMax = Math.max(0, contentH - (bottom - top - 16));
		scroll = Math.max(0, Math.min(scroll, scrollMax));
		g.enableScissor(gx + 2, top + 2, gx + gw - 2, bottom - 2);
		for (int i = 0; i < all.size(); i++) {
			SkinData s = all.get(i);
			int cx = gx + 8 + (i % cols) * (cw + 6), cy = top + 8 + (i / cols) * (ch + 6) - scroll;
			if (cy + ch < top || cy > bottom) {
				continue;
			}
			boolean on = chosen.contains(s.id);
			boolean hov = ui.hover(cx, cy, cw, ch) && ui.mouseY > top && ui.mouseY < bottom;
			ui.rounded(cx, cy, cw, ch, 8, on ? t.surfaceSelected : hov ? t.surfaceHover : t.surface);
			var icon = ClientSkins.icon(s);
			if (icon != null) {
				g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon, cx + 12, cy + 4, 0, 0, 40, 60, 40, 60);
			}
			ui.text(ui.ellipsize(s.name(Tr.german() ? "de" : "en"), cw - 6), cx + 3, cy + ch - 22, t.text);
			ui.text(ui.ellipsize(SkinLibrary.groupOf(s), cw - 6), cx + 3, cy + ch - 12, t.textFaint);
			if (on) {
				ui.rounded(cx + cw - 13, cy + 3, 10, 10, 5, t.accent);
				ui.text("✔", cx + cw - 11, cy + 4, t.onAccent);
			}
			ui.region(cx, cy, cw, ch, () -> {
				if (!chosen.remove(s.id)) {
					chosen.add(s.id);
				}
			});
		}
		g.disableScissor();
		if (all.isEmpty()) {
			ui.text(Tr.t("btb.ui.packbuilder.none"), gx + 10, top + 10, t.textMuted);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (nameBox.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.round(sy * 24)));
		return true;
	}

	public void autotest(String[] args) {
		switch (args[0]) {
			case "pick" -> chosen.add(args[1]);
			case "name" -> nameBox.setValue(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
			case "save" -> save(false);
			case "export" -> save(true);
			default -> {
			}
		}
	}

	@Override
	public void removed() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.restore();
		super.removed();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return ui.drag(event.x()) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		ui.endDrag();
		return super.mouseReleased(event);
	}
}
