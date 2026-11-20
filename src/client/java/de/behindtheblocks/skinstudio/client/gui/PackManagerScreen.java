package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.render.CosmeticTextures;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.pack.PackManifest;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.pack.PackWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Pack management: installed packs with version, author and problems; import (file dialog,
 * drag &amp; drop, import folder), export, uninstall and reload.
 */
public class PackManagerScreen extends Screen {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Packs");
	private final Screen parent;
	private final Ui ui = new Ui();
	private String selectedPack;
	private String confirmUninstall;
	private String status;
	private boolean statusError;
	private int scroll, scrollMax;
	private int detailScroll, detailMax;

	public PackManagerScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.t.packs")));
		this.parent = parent;
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
		CosmeticLibrary lib = CosmeticLibrary.get();
		int m = 8, top = 30, bottom = height - m;
		ui.iconButton(m, 6, 18, Icon.BACK, false, this::onClose, Tr.t("btb.t.zurueck"));
		ui.title(Tr.t("btb.t.cosmetic_packs"), m + 24, 11, t.text);
		int bx = width - m;
		String[] labels = {Tr.t("btb.t.datei_importieren"), Tr.t("btb.t.import_ordner"), Tr.t("btb.t.neu_laden")};
		Icon[] icons = {Icon.IMPORT, Icon.FOLDER, Icon.REFRESH};
		Runnable[] actions = {this::pickFiles, this::scanImportFolder, () -> lib.reload().thenRun(() -> status(Tr.t("btb.t.neu_geladen"), false))};
		String[] tips = {Tr.t("btb.t.btbcosmeticpack_btbcosmetic_mcpack_zip_j"),
				Tr.t("btb.t.dateien_aus") + lib.store.importDir + Tr.t("btb.t.importieren_oeffnet_den_ordner_wenn_er_l"), Tr.t("btb.t.alle_packs_neu_einlesen")};
		for (int i = labels.length - 1; i >= 0; i--) {
			int w = font.width(labels[i]) + 26;
			bx -= w;
			ui.button(bx, 6, w, 18, labels[i], icons[i], i == 0 ? Ui.Style.PRIMARY : Ui.Style.SECONDARY, true, actions[i], tips[i]);
			bx -= 4;
		}

		int listW = Math.max(150, width * 2 / 5);
		ui.glass(m, top, listW, bottom - top, 8);
		List<LoadedPack> packs = lib.packs();
		if (selectedPack == null && !packs.isEmpty()) {
			selectedPack = key(packs.getFirst());
		}
		g.enableScissor(m, top + 4, m + listW, bottom - 4);
		int y = top + 6 - scroll;
		for (LoadedPack p : packs) {
			boolean sel = key(p).equals(selectedPack);
			boolean hov = ui.hover(m + 4, y, listW - 8, 34);
			if (sel || hov) {
				ui.rounded(m + 4, y, listW - 8, 34, 7, sel ? t.surfaceSelected : t.surfaceHover);
			}
			ui.rounded(m + 9, y + 5, 24, 24, 5, t.stage);
			Identifier icon = packIcon(p);
			if (icon != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, icon, m + 9, y + 5, 0, 0, 24, 24, 128, 128, 128, 128);
			} else {
				Icon.BOX.draw(g, m + 17, y + 13, t.textMuted);
			}
			String name = p.manifest == null ? (p.source == null ? "?" : p.source.getFileName().toString()) : p.manifest.name(CosmeticLibrary.language());
			ui.text(ui.ellipsize(name, listW - 50), m + 39, y + 6, t.text);
			String origin = switch (p.origin) {
				case BUILTIN -> Tr.t("btb.t.eingebaut");
				case INSTALLED -> Tr.t("btb.t.installiert");
				case CUSTOM -> Tr.t("btb.t.eigene");
				case PREVIEW -> Tr.t("btb.t.vorschau");
			};
			String sub = origin + (p.manifest != null ? " · v" + p.manifest.version : "") + " · " + p.cosmetics.size() + Tr.t("btb.t.cosmetics");
			ui.text(ui.ellipsize(sub, listW - 50), m + 39, y + 18, t.textMuted);
			if (p.errorCount() > 0 || !p.usable) {
				ui.rounded(m + listW - 16, y + 13, 7, 7, 3, t.danger);
			} else if (p.warningCount() > 0) {
				ui.rounded(m + listW - 16, y + 13, 7, 7, 3, t.accent);
			}
			String k = key(p);
			ui.region(m + 4, y, listW - 8, 34, () -> {
				selectedPack = k;
				confirmUninstall = null;
				detailScroll = 0;
			});
			y += 37;
		}
		g.disableScissor();
		scrollMax = Math.max(0, packs.size() * 37 - 3 - (bottom - 4 - (top + 6)));
		scroll = Math.min(scroll, scrollMax);

		int dx = m + listW + 6, dw = width - m - dx;
		ui.glass(dx, top, dw, bottom - top, 10);
		LoadedPack p = packs.stream().filter(x -> key(x).equals(selectedPack)).findFirst().orElse(null);
		if (p != null) {
			drawDetails(p, dx, top, dw, bottom - top);
		} else {
			ui.textCentered(Tr.t("btb.t.ziehe_btbcosmeticpack_btbcosmetic_oder_b"), dx + dw / 2, top + 40, t.textMuted);
		}
		if (status != null) {
			int tw = font.width(status) + 20;
			ui.rounded(dx + (dw - tw) / 2, bottom - 26, tw, 18, 9, statusError ? (t.danger & 0xFFFFFF) | 0xE0000000 : t.dark ? 0xE6303644 : 0xF0FFFFFF);
			ui.text(status, dx + (dw - tw) / 2 + 10, bottom - 21, statusError ? 0xFFFFFFFF : t.text);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private Identifier packIcon(LoadedPack p) {
		if (!p.cosmetics.isEmpty()) {
			return CosmeticTextures.get(p.cosmetics.getFirst(), 0, CosmeticTextures.Layer.PREVIEW);
		}
		return null;
	}

	private static String key(LoadedPack p) {
		return p.origin + ":" + (p.manifest == null ? String.valueOf(p.source) : p.id());
	}

	private void drawDetails(LoadedPack p, int x, int y, int w, int h) {
		Theme t = ui.t;
		int cy = y + 8;
		String name = p.manifest == null ? Tr.t("btb.t.beschaedigtes_pack") : p.manifest.name(CosmeticLibrary.language());
		ui.title(ui.ellipsize(name, w - 16), x + 8, cy, t.text);
		cy += 12;
		if (p.manifest != null) {
			String meta = "ID " + p.id() + Tr.t("btb.t.version") + p.manifest.version + (p.manifest.author.isEmpty() ? "" : Tr.t("btb.t.von") + p.manifest.author);
			ui.text(ui.ellipsize(meta, w - 16), x + 8, cy, t.textMuted);
			cy += 11;
			String desc = p.manifest.description(CosmeticLibrary.language());
			if (!desc.isEmpty()) {
				for (var line : font.split(Component.literal(desc), w - 16)) {
					ui.g.text(font, line, x + 8, cy, t.textFaint, false);
					cy += 10;
				}
			}
		}
		if (p.source != null && p.origin != LoadedPack.Origin.BUILTIN) {
			ui.text(ui.ellipsize(p.source.toString(), w - 16), x + 8, cy, t.textFaint);
			cy += 11;
		}
		cy += 4;
		// buttons
		int bx = x + 8;
		if (p.usable && !p.cosmetics.isEmpty()) {
			int bw = font.width(Tr.t("btb.t.exportieren")) + 26;
			ui.button(bx, cy, bw, 18, Tr.t("btb.t.exportieren"), Icon.EXPORT, Ui.Style.SECONDARY, true, () -> exportPack(p), Tr.t("btb.t.als_btbcosmeticpack_in_den_exports_ordne"));
			bx += bw + 4;
		}
		if (p.origin == LoadedPack.Origin.INSTALLED) {
			boolean confirm = key(p).equals(confirmUninstall);
			String l = confirm ? Tr.t("btb.t.wirklich_deinstallieren") : Tr.t("btb.t.deinstallieren");
			int bw = font.width(l) + 26;
			ui.button(bx, cy, bw, 18, l, Icon.TRASH, Ui.Style.DANGER, true, () -> {
				if (confirm) {
					uninstall(p);
				} else {
					confirmUninstall = key(p);
				}
			}, null);
			bx += bw + 4;
		}
		if (p.origin == LoadedPack.Origin.CUSTOM) {
			int bw = font.width(Tr.t("btb.t.ordner_oeffnen")) + 26;
			ui.button(bx, cy, bw, 18, Tr.t("btb.t.ordner_oeffnen"), Icon.FOLDER, Ui.Style.SECONDARY, true, () -> com.mojang.blaze3d.Blaze3D.openPath(CosmeticLibrary.get().store.custom), null);
		}
		cy += 26;
		// cosmetics grid
		ui.text(Tr.t("btb.t.cosmetics_2") + p.cosmetics.size() + ")", x + 8, cy, t.textMuted);
		cy += 12;
		int listBottom = y + h - 8 - (p.problems.isEmpty() ? 0 : Math.min(90, 14 + p.problems.size() * 11));
		ui.g.enableScissor(x + 4, cy, x + w - 4, listBottom);
		int cx = x + 8, rowY = cy - detailScroll;
		for (CosmeticAsset c : p.cosmetics) {
			if (cx + 36 > x + w - 8) {
				cx = x + 8;
				rowY += 40;
			}
			ui.rounded(cx, rowY, 36, 36, 6, t.surface);
			Identifier tex = CosmeticTextures.get(c, 0, CosmeticTextures.Layer.PREVIEW);
			if (tex != null) {
				ui.g.blit(RenderPipelines.GUI_TEXTURED, tex, cx + 2, rowY + 2, 0, 0, 32, 32, 128, 128, 128, 128);
			}
			if (ui.hover(cx, rowY, 36, 36) && ui.mouseY >= cy && ui.mouseY < listBottom) {
				ui.tooltip(c.definition.name(CosmeticLibrary.language()) + "  (" + c.id() + ")");
				if (p.origin == LoadedPack.Origin.CUSTOM) {
					ui.region(cx, rowY, 36, 36, () -> exportCosmetic(c), () -> deleteCustom(c), null);
				}
			}
			cx += 40;
		}
		ui.g.disableScissor();
		detailMax = p.cosmetics.isEmpty() ? 0 : Math.max(0, rowY + 36 + detailScroll - cy - (listBottom - cy));
		detailScroll = Math.min(detailScroll, detailMax);
		if (p.origin == LoadedPack.Origin.CUSTOM && !p.cosmetics.isEmpty()) {
			ui.text(Tr.t("btb.t.klick_als_btbcosmetic_exportieren_rechts"), x + 8, listBottom - 10, t.textFaint);
		}
		if (!p.problems.isEmpty()) {
			int py = listBottom + 4;
			ui.text(Tr.t("btb.t.hinweise_2"), x + 8, py, t.textMuted);
			py += 12;
			for (PackProblem pr : p.problems) {
				if (py > y + h - 12) {
					break;
				}
				int col = pr.severity() == PackProblem.Severity.ERROR ? t.danger : pr.severity() == PackProblem.Severity.WARNING ? t.accent : t.textMuted;
				ui.rounded(x + 8, py + 2, 4, 4, 2, col);
				ui.text(ui.ellipsize(pr.toString(), w - 26), x + 16, py, t.text);
				py += 11;
			}
		}
	}

	private void status(String s, boolean error) {
		status = s;
		statusError = error;
	}

	private void pickFiles() {
		FileDialogs.openFiles(Tr.t("btb.t.cosmetics_bedrock"), "btbcosmeticpack;btbcosmetic;mcpack;mcaddon;zip;json;png", false, files -> {
			if (!files.isEmpty()) {
				minecraft.gui.setScreen(new ImportScreen(this, files));
			} else {
				status(Tr.t("btb.t.kein_datei_dialog_verfuegbar_dateien_ins"), false);
			}
		});
	}

	private void scanImportFolder() {
		Path dir = CosmeticLibrary.get().store.importDir;
		CompletableFuture.supplyAsync(() -> {
			try {
				Files.createDirectories(dir);
				try (Stream<Path> s = Files.list(dir)) {
					return s.toList();
				}
			} catch (Exception e) {
				return List.<Path>of();
			}
		}, CosmeticLibrary.IO).thenAcceptAsync(files -> {
			if (files.isEmpty()) {
				com.mojang.blaze3d.Blaze3D.openPath(dir);
				status(Tr.t("btb.t.import_ordner_ist_leer_dateien_hineinleg"), false);
			} else {
				minecraft.gui.setScreen(new ImportScreen(this, files));
			}
		}, Minecraft.getInstance());
	}

	private void exportPack(LoadedPack p) {
		CompletableFuture.supplyAsync(() -> {
			try {
				PackManifest m = p.manifest;
				if (p.origin == LoadedPack.Origin.CUSTOM) {
					m = new PackManifest();
					m.id = "my_cosmetics";
					m.names.put("de", "Meine Cosmetics");
					m.names.put("en", "My Cosmetics");
					m.author = minecraft.getUser().getName();
				}
				ArchiveFiles files = PackWriter.pack(m, p.icon, p.cosmetics);
				return CosmeticLibrary.get().store.export(m.id + PackStore.PACK_EXT, files.toZip());
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((path, err) -> {
			if (err != null) {
				status(Tr.t("btb.t.export_fehlgeschlagen") + err.getMessage(), true);
				LOG.error("Export fehlgeschlagen", err);
			} else {
				status(Tr.t("btb.t.exportiert") + path.getFileName(), false);
				com.mojang.blaze3d.Blaze3D.openPath(path.getParent());
			}
		}, Minecraft.getInstance());
	}

	private void exportCosmetic(CosmeticAsset c) {
		CompletableFuture.supplyAsync(() -> {
			try {
				return CosmeticLibrary.get().store.export(PackWriter.folderName(c.id()) + PackStore.COSMETIC_EXT, PackWriter.singleCosmetic(c));
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((path, err) -> {
			if (err != null) {
				status(Tr.t("btb.t.export_fehlgeschlagen") + err.getMessage(), true);
			} else {
				status(Tr.t("btb.t.exportiert") + path.getFileName(), false);
			}
		}, Minecraft.getInstance());
	}

	private void deleteCustom(CosmeticAsset c) {
		CompletableFuture.runAsync(() -> {
			try {
				CosmeticLibrary.get().store.deleteCustom(c.id());
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((v, err) -> {
			if (err != null) {
				status(Tr.t("btb.t.loeschen_fehlgeschlagen") + err.getMessage(), true);
			} else {
				CosmeticLibrary.get().reload();
				status(c.definition.name(CosmeticLibrary.language()) + Tr.t("btb.t.geloescht"), false);
			}
		}, Minecraft.getInstance());
	}

	private void uninstall(LoadedPack p) {
		CompletableFuture.runAsync(() -> {
			try {
				CosmeticLibrary.get().store.uninstall(p);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((v, err) -> {
			if (err != null) {
				status(Tr.t("btb.t.deinstallieren_fehlgeschlagen") + err.getMessage(), true);
			} else {
				selectedPack = null;
				confirmUninstall = null;
				CosmeticLibrary.get().reload();
				status(Tr.t("btb.t.pack_deinstalliert"), false);
			}
		}, Minecraft.getInstance());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (x < width * 2 / 5 + 8) {
			scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.round(sy * 20)));
		} else {
			detailScroll = Math.max(0, Math.min(detailMax, detailScroll - (int) Math.round(sy * 20)));
		}
		return true;
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		minecraft.gui.setScreen(new ImportScreen(this, files));
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
}
