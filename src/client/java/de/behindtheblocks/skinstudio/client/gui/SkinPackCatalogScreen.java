package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.client.skin.SkinPackCatalog;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Skin pack catalog: browse the packs of the configured online catalogs (e.g. all classic Bedrock skin packs)
 * and download single packs or all of them into {@code skin_packs/}.
 */
public class SkinPackCatalogScreen extends Screen {
	private final Screen parent;
	private final Ui ui = new Ui();
	private final List<SkinPackCatalog.Entry> entries = new ArrayList<>();
	private final Set<String> busy = new HashSet<>();
	private boolean loading = true;
	private String status;
	private boolean statusError;
	private int scroll, scrollMax;
	private int queued;

	public SkinPackCatalogScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.ui.catalog.title")));
		this.parent = parent;
		reload();
	}

	private void reload() {
		loading = true;
		status = null;
		SkinPackCatalog.load().whenCompleteAsync((list, err) -> {
			loading = false;
			entries.clear();
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				status = c.getMessage();
				statusError = true;
				return;
			}
			entries.addAll(list);
		}, Minecraft.getInstance());
	}

	private void download(SkinPackCatalog.Entry e) {
		if (!busy.add(e.id())) {
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				return SkinPackCatalog.downloadNow(e);
			} catch (Exception ex) {
				throw new RuntimeException(ex.getMessage(), ex);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((n, err) -> {
			busy.remove(e.id());
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				status = Tr.t("btb.ui.catalog.failed", e.name(), c.getMessage());
				statusError = true;
			} else {
				status = Tr.t("btb.ui.catalog.done", e.name(), n);
				statusError = false;
			}
			if (busy.isEmpty()) {
				queued = 0;
				SkinLibrary.get().reload();
			}
		}, Minecraft.getInstance());
	}

	private void downloadAll() {
		for (SkinPackCatalog.Entry e : entries) {
			if (!SkinPackCatalog.installed(e)) {
				queued++;
				download(e);
			}
		}
	}

	private void remove(SkinPackCatalog.Entry e) {
		try {
			SkinPackCatalog.remove(e);
			status = Tr.t("btb.ui.catalog.removed", e.name());
			statusError = false;
			SkinLibrary.get().reload();
		} catch (Exception ex) {
			status = ex.getMessage();
			statusError = true;
		}
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
		ui.title(Tr.t("btb.ui.catalog.title"), m + 24, 12, t.text);
		String all = Tr.t("btb.ui.catalog.all"), again = Tr.t("btb.ui.bedrock.rescan");
		int w1 = font.width(all) + 28, w2 = font.width(again) + 28;
		ui.button(width - m - w1, 7, w1, 18, all, Icon.IMPORT, Ui.Style.PRIMARY, !loading && busy.isEmpty() && !entries.isEmpty(), this::downloadAll, null);
		ui.button(width - m - w1 - 4 - w2, 7, w2, 18, again, Icon.REFRESH, Ui.Style.SECONDARY, !loading, this::reload, null);

		ui.glass(m, top, width - 2 * m, bottom - top, 10);
		int x = m + 10, w = width - 2 * m - 20, y = top + 8;
		for (var line : font.split(Component.literal(Tr.t("btb.ui.catalog.info")), w)) {
			g.text(font, line, x, y, t.textFaint, false);
			y += 10;
		}
		y += 4;
		int listBottom = bottom - 22;
		if (loading) {
			ui.text(Tr.t("btb.ui.catalog.loading"), x, y, t.textMuted);
		}
		int cols = Math.max(1, w / 230), cw = (w - (cols - 1) * 6) / cols, ch = 44;
		int rows = (entries.size() + cols - 1) / cols;
		scrollMax = Math.max(0, rows * (ch + 6) - (listBottom - y));
		scroll = Math.max(0, Math.min(scroll, scrollMax));
		g.enableScissor(m + 2, y, width - m - 2, listBottom);
		for (int i = 0; i < entries.size(); i++) {
			SkinPackCatalog.Entry e = entries.get(i);
			int cx = x + (i % cols) * (cw + 6), cy = y + (i / cols) * (ch + 6) - scroll;
			if (cy + ch < y || cy > listBottom) {
				continue;
			}
			ui.rounded(cx, cy, cw, ch, 8, t.surface);
			var icon = SkinPackCatalog.icon(e);
			if (icon != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, icon, cx + 4, cy + 4, 0, 0, 36, 36, 36, 36);
			} else {
				ui.rounded(cx + 4, cy + 4, 36, 36, 6, t.field);
			}
			boolean installed = SkinPackCatalog.installed(e);
			String label = busy.contains(e.id()) ? Tr.t("btb.ui.catalog.loading_one") : Tr.t(installed ? "btb.ui.catalog.remove" : "btb.ui.catalog.get");
			int bw = font.width(label) + 16;
			int tx = cx + 46, tw = cw - 46 - bw - 10;
			ui.text(ui.ellipsize(e.name(), tw), tx, cy + 6, t.text);
			var desc = font.split(Component.literal(e.description()), tw);
			for (int k = 0; k < Math.min(2, desc.size()); k++) {
				g.text(font, desc.get(k), tx, cy + 18 + k * 10, t.textFaint, false);
			}
			ui.button(cx + cw - bw - 6, cy + 12, bw, 18, label, null, installed ? Ui.Style.SECONDARY : Ui.Style.PRIMARY, !busy.contains(e.id()),
					() -> {
						if (SkinPackCatalog.installed(e)) {
							remove(e);
						} else {
							download(e);
						}
					}, null);
		}
		g.disableScissor();
		String line = status;
		if (!busy.isEmpty()) {
			line = Tr.t("btb.ui.catalog.progress", queued - busy.size(), Math.max(queued, busy.size()));
		}
		if (line != null) {
			ui.text(ui.ellipsize(line, w), x, bottom - 16, statusError && busy.isEmpty() ? t.danger : t.textMuted);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.round(sy * 24)));
		return true;
	}

	public void autotest(String[] args) {
		switch (args[0]) {
			case "get" -> entries.stream().filter(e -> e.id().equals(args[1])).findFirst().ifPresent(this::download);
			case "all" -> downloadAll();
			default -> {
			}
		}
	}

	@Override
	protected void init() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.fit(this);
		super.init();
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
