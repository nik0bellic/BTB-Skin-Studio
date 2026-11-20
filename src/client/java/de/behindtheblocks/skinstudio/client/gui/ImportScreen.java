package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.importer.ImportPlan;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Import preview: every selected or dropped file is analysed in the background and shown with its
 * cosmetics (live 3D preview), problems and the action that would be taken. Nothing is installed
 * until "Importieren" is pressed.
 */
public class ImportScreen extends Screen {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Import");
	private final Screen parent;
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private final List<ImportPlan> plans = new ArrayList<>();
	private int pending;
	private int selectedPlan;
	private int selectedCosmetic;
	private ActiveCosmetic previewCosmetic;
	private Object previewKey;
	private boolean dragging;
	private boolean busy;
	private String status;
	private int problemScroll;

	public ImportScreen(Screen parent, List<Path> files) {
		super(Component.literal(Tr.t("btb.t.import")));
		this.parent = parent;
		this.pending = files.size();
		for (Path p : files) {
			CompletableFuture.supplyAsync(() -> ImportPlan.analyze(p), CosmeticLibrary.IO).whenCompleteAsync((plan, err) -> {
				pending--;
				if (plan != null) {
					plans.add(plan);
				} else {
					LOG.error("Analyse von {} fehlgeschlagen", p, err);
				}
			}, Minecraft.getInstance());
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
		int m = 8, top = 30;
		ui.iconButton(m, 6, 18, Icon.BACK, false, this::onClose, Tr.t("btb.t.zurueck"));
		ui.title(Tr.t("btb.t.import_vorschau"), m + 24, 11, t.text);
		ui.text(Tr.t("btb.t.pruefen_ansehen_dann_uebernehmen"), m + 24 + ui.titleWidth(Tr.t("btb.t.import_vorschau")) + 10, 11, t.textMuted);

		int listW = Math.max(110, width / 5);
		int stageW = Math.max(130, width / 4);
		int bottom = height - m;
		// list of files
		ui.glass(m, top, listW, bottom - top, 8);
		int y = top + 6;
		for (int i = 0; i < plans.size(); i++) {
			ImportPlan p = plans.get(i);
			boolean sel = i == selectedPlan;
			boolean hov = ui.hover(m + 4, y, listW - 8, 26);
			if (sel || hov) {
				ui.rounded(m + 4, y, listW - 8, 26, 6, sel ? t.surfaceSelected : t.surfaceHover);
			}
			int color = p.allowed ? t.success : t.danger;
			ui.rounded(m + 9, y + 9, 7, 7, 3, color);
			ui.text(ui.ellipsize(p.title, listW - 30), m + 21, y + 4, t.text);
			ui.text(ui.ellipsize(p.kind.label() + (p.summary.isEmpty() ? "" : " · " + p.summary), listW - 30), m + 21, y + 15, t.textMuted);
			int idx = i;
			ui.region(m + 4, y, listW - 8, 26, () -> {
				selectedPlan = idx;
				selectedCosmetic = 0;
				problemScroll = 0;
			});
			y += 29;
		}
		if (pending > 0) {
			ui.text(Tr.t("btb.t.analysiere") + pending + Tr.t("btb.t.datei_en"), m + 10, y + 4, t.textMuted);
		} else if (plans.isEmpty()) {
			ui.text(Tr.t("btb.t.keine_dateien"), m + 10, y + 4, t.textMuted);
		}

		ImportPlan plan = selectedPlan < plans.size() ? plans.get(selectedPlan) : null;
		// stage
		int sx = m + listW + 6;
		ui.glass(sx, top, stageW, bottom - top, 10);
		ui.rounded(sx + 4, top + 4, stageW - 8, bottom - top - 8, 8, t.stage);
		CosmeticAsset asset = plan != null && selectedCosmetic < plan.cosmetics.size() ? plan.cosmetics.get(selectedCosmetic) : null;
		if (asset != previewKey) {
			previewKey = asset;
			previewCosmetic = asset == null ? null : ActiveCosmetic.transient_(asset, 0);
			if (asset != null) {
				preview.focus = asset.definition.attachment.part == de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint.Part.HEAD ? PlayerPreview.Focus.HEAD : PlayerPreview.Focus.FULL;
			}
		}
		preview.draw(g, sx + 4, top + 4, sx + stageW - 4, bottom - 8, previewCosmetic == null ? List.of() : List.of(previewCosmetic));
		ui.region(sx + 4, top + 4, stageW - 8, bottom - top - 8, () -> dragging = true);
		ui.hint(Tr.t("btb.t.vorschau_ziehen_zum_drehen"), sx + 4, sx + stageW - 4, bottom - 24);

		// details
		int dx = sx + stageW + 6, dw = width - m - dx;
		ui.glass(dx, top, dw, bottom - top, 10);
		if (plan != null) {
			int cy = top + 8;
			ui.title(ui.ellipsize(plan.title, dw - 16), dx + 8, cy, t.text);
			cy += 12;
			ui.text(ui.ellipsize(plan.source.toString(), dw - 16), dx + 8, cy, t.textFaint);
			cy += 14;
			if (!plan.cosmetics.isEmpty()) {
				ui.text(Tr.t("btb.t.enthaltene_cosmetics") + plan.cosmetics.size() + ")", dx + 8, cy, t.textMuted);
				cy += 12;
				int cx = dx + 8;
				for (int i = 0; i < plan.cosmetics.size(); i++) {
					CosmeticAsset c = plan.cosmetics.get(i);
					String label = ui.ellipsize(c.definition.name(CosmeticLibrary.language()), dw - 30);
					int w = ui.chipWidth(label, false);
					if (cx + w > dx + dw - 8) {
						cx = dx + 8;
						cy += 17;
					}
					if (cy > top + (bottom - top) / 2) {
						ui.text(Tr.t("btb.t.und") + (plan.cosmetics.size() - i) + Tr.t("btb.t.weitere"), cx, cy + 3, t.textFaint);
						break;
					}
					int idx = i;
					ui.chip(cx, cy, label, i == selectedCosmetic, () -> selectedCosmetic = idx, null);
					cx += w + 4;
				}
				cy += 22;
			}
			ui.text(plan.problems.isEmpty() ? Tr.t("btb.t.keine_probleme_gefunden") : Tr.t("btb.t.hinweise") + plan.problems.size() + ")", dx + 8, cy, plan.problems.isEmpty() ? t.success : t.textMuted);
			cy += 12;
			int listBottom = bottom - 34;
			g.enableScissor(dx + 4, cy, dx + dw - 4, listBottom);
			int py = cy - problemScroll;
			for (PackProblem p : plan.problems) {
				int col = switch (p.severity()) {
					case ERROR -> t.danger;
					case WARNING -> t.accent;
					case INFO -> t.textMuted;
				};
				ui.rounded(dx + 8, py + 3, 4, 4, 2, col);
				var lines = font.split(Component.literal(p.toString()), dw - 30);
				for (var line : lines) {
					g.text(font, line, dx + 16, py, t.text, false);
					py += 10;
				}
				py += 3;
			}
			g.disableScissor();
			String label = busy ? de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.wait") : plan.actionLabel;
			int bw = Math.min(font.width(label) + 32, dw - 16);
			ui.button(dx + dw - 8 - bw, bottom - 26, bw, 18, label, Icon.IMPORT, Ui.Style.PRIMARY,
					plan.allowed && !busy, () -> run(plan), plan.allowed ? null : de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.import.blocked"));
			if (plan.canRename && !busy) {
				String rl = de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.import.rename");
				int rw = font.width(rl) + 32;
				ui.button(dx + dw - 8 - bw - 6 - rw, bottom - 26, rw, 18, rl, Icon.EDIT, Ui.Style.SECONDARY, true, () -> rename(plan),
						de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.import.rename.tooltip"));
			}
			if (status != null) {
				ui.text(ui.ellipsize(status, dw - bw - 24), dx + 8, bottom - 21, t.textMuted);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private void rename(ImportPlan plan) {
		busy = true;
		CompletableFuture.runAsync(plan::renameConflicts, CosmeticLibrary.IO).whenCompleteAsync((v, err) -> {
			busy = false;
			status = err != null ? de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.error", err.getMessage()) : de.behindtheblocks.skinstudio.util.Tr.t("btb.ui.import.renamed");
			selectedCosmetic = 0;
		}, Minecraft.getInstance());
	}

	private void run(ImportPlan plan) {
		busy = true;
		status = null;
		CompletableFuture.supplyAsync(() -> {
			try {
				return plan.execute();
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((msg, err) -> {
			if (err != null) {
				busy = false;
				status = Tr.t("btb.t.fehler") + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
				LOG.error("Import fehlgeschlagen", err);
				return;
			}
			CosmeticLibrary.get().reload().thenRun(() -> {
				busy = false;
				plan.allowed = false;
				status = msg + " ✓";
			});
		}, Minecraft.getInstance());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
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
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		problemScroll = Math.max(0, problemScroll - (int) (sy * 20));
		return true;
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		minecraft.gui.setScreen(new ImportScreen(parent, files));
	}

	/** Used by the development AutoTest only. */
	public void autotest(String[] args) {
		switch (args[0]) {
			case "plan" -> {
				selectedPlan = Integer.parseInt(args[1]);
				selectedCosmetic = 0;
			}
			case "cosmetic" -> selectedCosmetic = Integer.parseInt(args[1]);
			case "run" -> {
				if (selectedPlan < plans.size()) {
					run(plans.get(selectedPlan));
				}
			}
			default -> {
			}
		}
	}
}
