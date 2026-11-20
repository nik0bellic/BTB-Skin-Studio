package de.behindtheblocks.skinstudio.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.skin.CapeService;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The capes of the own Java account: pick the one to show, or none. */
public class CapeScreen extends Screen {
	private final Screen parent;
	private final Ui ui = new Ui();
	private final List<CapeService.Cape> capes = new ArrayList<>();
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Map<String, int[]> SIZES = new HashMap<>();
	private boolean busy = true;
	private String status;
	private boolean statusError;

	public CapeScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.ui.cape.title")));
		this.parent = parent;
		load();
	}

	private void load() {
		busy = true;
		status = Tr.t("btb.ui.cape.loading");
		statusError = false;
		CapeService.list().whenCompleteAsync((list, err) -> {
			busy = false;
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				status = c.getMessage() == null ? c.toString() : c.getMessage();
				statusError = true;
				return;
			}
			capes.clear();
			capes.addAll(list);
			status = list.isEmpty() ? Tr.t("btb.ui.cape.none_owned") : null;
			for (CapeService.Cape c : list) {
				loadTexture(c);
			}
		}, Minecraft.getInstance());
	}

	private void loadTexture(CapeService.Cape c) {
		if (c.url() == null || TEXTURES.containsKey(c.id())) {
			return;
		}
		CapeService.texture(c.url()).thenAcceptAsync(bytes -> {
			try {
				NativeImage img = NativeImage.read(bytes);
				Identifier id = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "cape/" + Integer.toHexString(c.id().hashCode()));
				SIZES.put(c.id(), new int[]{img.getWidth(), img.getHeight()});
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "cape " + c.alias(), img));
				TEXTURES.put(c.id(), id);
			} catch (Exception ignored) {
				// no picture, the name is still shown
			}
		}, Minecraft.getInstance());
	}

	private void select(String id) {
		busy = true;
		status = Tr.t("btb.ui.cape.saving");
		statusError = false;
		CapeService.select(id).whenCompleteAsync((v, err) -> {
			busy = false;
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				status = c.getMessage() == null ? c.toString() : c.getMessage();
				statusError = true;
				return;
			}
			load();
			status = Tr.t("btb.ui.cape.saved");
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
		ui.title(Tr.t("btb.ui.cape.title"), m + 24, 12, t.text);
		String reload = Tr.t("btb.ui.skin.reload");
		int rw = font.width(reload) + 30;
		ui.button(width - m - rw, 7, rw, 18, reload, Icon.REFRESH, Ui.Style.SECONDARY, !busy, this::load, null);
		ui.glass(m, top, width - 2 * m, bottom - top, 10);
		int x = m + 10, w = width - 2 * m - 20, y = top + 10;
		for (var line : font.split(Component.literal(Tr.t("btb.ui.cape.info")), w)) {
			g.text(font, line, x, y, t.textMuted, false);
			y += 10;
		}
		y += 6;
		int cw = 64, ch = 96, gap = 8, cols = Math.max(1, (w + gap) / (cw + gap));
		boolean anyActive = capes.stream().anyMatch(CapeService.Cape::active);
		int count = capes.size() + 1;
		for (int i = 0; i < count; i++) {
			int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
			if (cy + ch > bottom - 30) {
				break;
			}
			CapeService.Cape c = i == 0 ? null : capes.get(i - 1);
			boolean on = c == null ? !anyActive && !capes.isEmpty() : c.active();
			boolean hov = ui.hover(cx, cy, cw, ch);
			ui.rounded(cx, cy, cw, ch, 8, on ? t.surfaceSelected : hov ? t.surfaceHover : t.surface);
			if (on) {
				ui.roundedOutline(cx, cy, cw, ch, 8, t.accent);
			}
			Identifier tex = c == null ? null : TEXTURES.get(c.id());
			if (tex != null) {
				int[] sz = SIZES.get(c.id());
				int s = Math.max(1, sz[0] / 64);
				// front of the cape: 10 × 16 pixels at (1, 1)
				g.blit(RenderPipelines.GUI_TEXTURED, tex, cx + 17, cy + 8, s, s, 30, 48, 10 * s, 16 * s, sz[0], sz[1]);
			} else if (c == null) {
				Icon.CLOSE.draw(g, cx + cw / 2 - 3, cy + 30, t.textMuted);
			}
			String name = c == null ? Tr.t("btb.ui.cape.off") : c.alias();
			var lines = font.split(Component.literal(name), cw - 6);
			int ly = cy + 62;
			for (int k = 0; k < Math.min(2, lines.size()); k++) {
				g.text(font, lines.get(k), cx + (cw - font.width(lines.get(k))) / 2, ly, t.text, false);
				ly += 10;
			}
			if (!busy && !on && !(c == null && capes.isEmpty())) {
				ui.region(cx, cy, cw, ch, () -> select(c == null ? null : c.id()));
			}
		}
		if (status != null) {
			var lines = font.split(Component.literal(status), w);
			int sy = bottom - 10 - lines.size() * 10;
			for (var line : lines) {
				g.text(font, line, x, sy, statusError ? t.danger : t.textMuted, false);
				sy += 10;
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return ui.click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
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
