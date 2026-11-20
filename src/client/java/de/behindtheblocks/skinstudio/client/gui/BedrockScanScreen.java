package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.bedrock.BedrockLocator;
import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Finds Minecraft Bedrock on this computer and offers its skins, skin packs and Character Creator pieces
 * for use in Java. Encrypted Marketplace content is listed but cannot be used.
 */
public class BedrockScanScreen extends Screen {
	private final Screen parent;
	private final Ui ui = new Ui();
	private final List<Path> roots = new ArrayList<>();
	private final List<BedrockLocator.Found> found = new ArrayList<>();
	private boolean scanning;
	private String status;
	private boolean statusError;
	private int scroll;

	public BedrockScanScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.ui.bedrock.scan")));
		this.parent = parent;
		scan(null);
	}

	private void scan(Path custom) {
		scanning = true;
		CompletableFuture.supplyAsync(() -> {
			List<Path> r = custom != null ? List.of(custom) : BedrockLocator.installations();
			List<BedrockLocator.Found> f = new ArrayList<>();
			for (Path p : r) {
				f.addAll(BedrockLocator.scan(p));
			}
			return new Object[]{r, f};
		}, CosmeticLibrary.IO).thenAcceptAsync(res -> {
			roots.clear();
			found.clear();
			@SuppressWarnings("unchecked") List<Path> r = (List<Path>) res[0];
			@SuppressWarnings("unchecked") List<BedrockLocator.Found> f = (List<BedrockLocator.Found>) res[1];
			roots.addAll(r);
			found.addAll(f);
			scanning = false;
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
		ui.title(Tr.t("btb.ui.bedrock.scan"), m + 24, 12, t.text);
		String pick = Tr.t("btb.ui.bedrock.pick_folder"), again = Tr.t("btb.ui.bedrock.rescan");
		int w1 = font.width(pick) + 28, w2 = font.width(again) + 28;
		ui.button(width - m - w1, 7, w1, 18, pick, Icon.FOLDER, Ui.Style.SECONDARY, !scanning,
				() -> FileDialogs.openFiles("", "", true, paths -> {
					if (!paths.isEmpty()) {
						scan(paths.getFirst());
					}
				}), Tr.t("btb.ui.bedrock.pick_folder.tooltip"));
		ui.button(width - m - w1 - 4 - w2, 7, w2, 18, again, Icon.REFRESH, Ui.Style.SECONDARY, !scanning, () -> scan(null), null);

		ui.glass(m, top, width - 2 * m, bottom - top, 10);
		int x = m + 10, w = width - 2 * m - 20;
		int y = top + 8;
		if (scanning) {
			ui.text(Tr.t("btb.ui.bedrock.scanning"), x, y, t.textMuted);
		} else if (roots.isEmpty()) {
			for (var line : font.split(Component.literal(Tr.t("btb.ui.bedrock.none")), w)) {
				g.text(font, line, x, y, t.textMuted, false);
				y += 10;
			}
		} else {
			ui.text(ui.ellipsize(Tr.t("btb.ui.bedrock.found_in", roots.size()) + "  " + roots.getFirst(), w), x, y, t.textMuted);
			y += 14;
			for (var line : font.split(Component.literal(Tr.t("btb.ui.bedrock.legal")), w)) {
				g.text(font, line, x, y, t.textFaint, false);
				y += 10;
			}
			y += 4;
			if (found.isEmpty()) {
				ui.text(Tr.t("btb.ui.bedrock.nothing"), x, y, t.textMuted);
			}
			g.enableScissor(m + 2, y, width - m - 2, bottom - 30);
			int ry = y - scroll;
			for (BedrockLocator.Found f : found) {
				boolean hov = ui.hover(x - 4, ry, w + 8, 24) && ui.mouseY >= y && ui.mouseY < bottom - 30;
				ui.rounded(x - 4, ry, w + 8, 24, 6, hov ? t.surfaceHover : t.surface);
				String kind = Tr.t("btb.ui.bedrock.kind." + f.kind().name().toLowerCase(java.util.Locale.ROOT));
				ui.text(ui.ellipsize(f.label(), w / 2), x + 4, ry + 4, t.text);
				ui.text(ui.ellipsize(kind + "  ·  " + f.path(), w - 160), x + 4, ry + 14, t.textFaint);
				if (f.encrypted()) {
					ui.text(Tr.t("btb.ui.bedrock.encrypted"), x + w - font.width(Tr.t("btb.ui.bedrock.encrypted")) - 4, ry + 8, t.danger);
				} else {
					String action = Tr.t(f.kind() == BedrockLocator.Kind.PERSONA ? "btb.ui.bedrock.import" : "btb.ui.bedrock.use");
					int bw = font.width(action) + 20;
					ui.button(x + w - bw, ry + 3, bw, 18, action, null, Ui.Style.PRIMARY, true, () -> use(f), null);
				}
				ry += 27;
			}
			g.disableScissor();
		}
		if (status != null) {
			ui.text(ui.ellipsize(status, w), x, bottom - 20, statusError ? t.danger : t.textMuted);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private void use(BedrockLocator.Found f) {
		if (f.kind() == BedrockLocator.Kind.PERSONA) {
			minecraft.gui.setScreen(new ImportScreen(this, List.of(f.path())));
			return;
		}
		CompletableFuture.runAsync(() -> {
			try {
				if (f.kind() == BedrockLocator.Kind.SKIN_PNG) {
					SkinLibrary.get().saveCustom(SkinIO.fromPng(Files.readAllBytes(f.path()), f.label(), null));
				} else {
					Path target = SkinLibrary.get().skinPacksDir.resolve(f.label());
					PackStore.deleteRecursively(target);
					try (Stream<Path> walk = Files.walk(f.path())) {
						for (Path src : walk.toList()) {
							Path dst = target.resolve(f.path().relativize(src).toString());
							if (Files.isDirectory(src)) {
								Files.createDirectories(dst);
							} else {
								Files.createDirectories(dst.getParent());
								Files.copy(src, dst);
							}
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((v, err) -> {
			if (err != null) {
				status = Tr.t("btb.ui.error", err.getMessage());
				statusError = true;
				return;
			}
			statusError = false;
			status = Tr.t("btb.ui.bedrock.taken", f.label());
			SkinLibrary.get().reload();
		}, Minecraft.getInstance());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scroll = Math.max(0, scroll - (int) (sy * 20));
		return true;
	}
}
