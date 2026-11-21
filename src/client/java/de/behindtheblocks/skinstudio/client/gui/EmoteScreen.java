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
 * showing the hovered emote. Point with the mouse and click, or hold the key, point and let go.
 */
public class EmoteScreen extends Screen {
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private String hovered;
	/** Selected segment: follows the mouse direction (anywhere on screen) and the scroll wheel. */
	private int sel = -1;
	private double lastMx = Double.NaN, lastMy = Double.NaN;
	private int cx, cy, inner, outer;
	private int[] fakeMouse;
	private final long openedAt = System.currentTimeMillis();

	public EmoteScreen() {
		super(Component.literal(Tr.t("btb.ui.emotes.wheel")));
		preview.loopEmote = true;
	}

	/** The wheel key was held when the wheel opened; true until it is let go once. */
	private boolean keyHeld = true;

	/**
	 * Hold the key, point, let go: the key state is read directly every tick, because the release does not
	 * always arrive as an event at a screen that was opened by that same key.
	 */
	@Override
	public void tick() {
		super.tick();
		var km = de.behindtheblocks.skinstudio.client.BtbSkinStudioClient.emoteKeyMapping();
		if (km == null || !keyHeld) {
			return;
		}
		var key = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(km);
		if (key.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD) {
			keyHeld = false;
			return;
		}
		if (!com.mojang.blaze3d.platform.InputConstants.isKeyDown(key.getValue())) {
			keyHeld = false;
			if (System.currentTimeMillis() - openedAt > 300) {
				// held long enough to aim: play what is selected (or just close)
				if (sel >= 0) {
					play(sel);
				} else {
					onClose();
				}
			}
			// a short tap keeps the wheel open for clicking
		}
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
		if (fakeMouse != null) {
			mouseX = fakeMouse[0];
			mouseY = fakeMouse[1];
		}

		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		boolean pad = controllerActive();
		// the hint below the wheel is measured first, so wheel + hint always fit on the screen
		String hint = Tr.t(pad ? "btb.ui.emotes.hint_pad" : "btb.ui.emotes.hint");
		var hl = font.split(Component.literal(hint), Math.min(width - 20, 300));
		int hintH = hl.size() * 10 + 10;
		int outer = Math.max(40, Math.min(100, (height - hintH - 24) / 2)), inner = (int) (outer * 0.4);
		// the wheel sits exactly in the middle of the screen, where the mouse cursor is put when the wheel opens:
		// moving the mouse from there points at a segment (hold the key, point, let go). The figure is beside it.
		int cx = width / 2, cy = Math.max(outer + 6, (height - (outer * 2 + hintH)) / 2 + outer);
		int gap = 16;
		int previewW = Math.min(120, width - (cx + outer + gap) - 8);
		this.cx = cx;
		this.cy = cy;
		this.inner = inner;
		this.outer = outer;
		// only updated when the mouse moves, so the scroll wheel and the arrow keys can pick too
		if (mouseX != lastMx || mouseY != lastMy) {
			boolean first = Double.isNaN(lastMx);
			lastMx = mouseX;
			lastMy = mouseY;
			double dx = mouseX - cx, dy = mouseY - cy;
			double d = Math.sqrt(dx * dx + dy * dy);
			if (!first || d >= inner * 0.6) {
				// pointing anywhere (inside or outside the ring) picks the segment in that direction; the middle picks nothing
				sel = d < inner * 0.6 ? -1 : de.behindtheblocks.skinstudio.client.gui.ui.WheelTexture.segmentAt(dx, dy, 0, Double.MAX_VALUE);
			}
		}
		int seg = sel;
		// ring with five segments; the selected one lights up
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, de.behindtheblocks.skinstudio.client.gui.ui.WheelTexture.ring(t),
				cx - outer, cy - outer, 0, 0, outer * 2, outer * 2, outer * 2, outer * 2);
		if (seg >= 0) {
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, de.behindtheblocks.skinstudio.client.gui.ui.WheelTexture.highlight(seg, t),
					cx - outer, cy - outer, 0, 0, outer * 2, outer * 2, outer * 2, outer * 2);
		}
		String hover = seg >= 0 ? EmoteKeys.slot(seg) : null;
		// pointer from the centre towards the mouse
		if (seg >= 0) {
			double a0 = Math.toRadians(-90 + seg * 72);
			int r0 = (int) (inner * 0.82);
			int pxp = cx + (int) Math.round(Math.cos(a0) * r0), pyp = cy + (int) Math.round(Math.sin(a0) * r0);
			ui.rounded(pxp - 3, pyp - 3, 6, 6, 3, t.accent | 0xFF000000);
		}
		for (int i = 0; i < 5; i++) {
			double ang = Math.toRadians(-90 + i * 72);
			int mid = (inner + outer) / 2;
			int sx = cx + (int) Math.round(Math.cos(ang) * mid), sy = cy + (int) Math.round(Math.sin(ang) * mid);
			String id = EmoteKeys.slot(i);
			boolean hov = i == seg;
			int fg = hov ? t.onAccent : t.text;
			// key cap (keyboard) or round button (controller) above the name
			if (pad) {
				padButton(sx, sy - 12, String.valueOf(i + 1), hov);
			} else {
				keyCap(sx, sy - 12, String.valueOf(i + 1), hov);
			}
			// the full name, wrapped onto up to three lines – never cut off
			var lines = font.split(Component.literal(id == null ? "–" : de.behindtheblocks.skinstudio.client.emote.CustomEmoteLibrary.name(id)), (int) (outer * 0.62));
			int ly = sy;
			for (var l : lines) {
				g.text(font, l, sx - font.width(l) / 2, ly, fg, false);
				ly += 9;
			}
		}
		// centre: hovered name or title
		String title = hover != null ? de.behindtheblocks.skinstudio.client.emote.CustomEmoteLibrary.name(hover) : Tr.t("btb.ui.emotes.wheel");
		var tl = font.split(Component.literal(title), inner * 2 - 8);
		int ty = cy - tl.size() * 5;
		for (var l : tl) {
			g.text(font, l, cx - font.width(l) / 2, ty, t.text, false);
			ty += 10;
		}
		// hint below the wheel
		int hy = cy + outer + 8;
		int hcx = cx;
		for (var l : hl) {
			int lx = Math.max(4, Math.min(width - 4 - font.width(l), hcx - font.width(l) / 2));
			g.text(font, l, lx, hy, 0xFFE8E8EC, true);
			hy += 10;
		}
		// figure next to the wheel showing the hovered emote
		if (hover != null && !hover.equals(preview.emote)) {
			preview.playEmote(hover);
		}
		if (hover == null && hovered != null) {
			preview.emote = null;
		}
		hovered = hover;
		if (previewW >= 60) {
			int px = cx + outer + gap;
			ui.rounded(px, cy - outer, previewW, outer * 2, 16, t.dark ? 0xC81A1D24 : 0xD8FFFFFF);
			preview.draw(g, px, cy - outer + 4, px + previewW, cy + outer - 4, cosmetics());
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	/** A keyboard key: light top, darker rim at the bottom. */
	private void keyCap(int x, int y, String label, boolean active) {
		int w = Math.max(14, font.width(label) + 8), h = 14;
		int x0 = x - w / 2;
		ui.rounded(x0, y, w, h, 3, active ? 0xFF6A5420 : 0xFF2A2D36);
		ui.rounded(x0, y, w, h - 2, 3, active ? 0xFFFFD27A : 0xFFE6E8EE);
		ui.rounded(x0 + 1, y + 1, w - 2, h - 5, 2, active ? 0xFFFFE2A0 : 0xFFF8F9FB);
		ui.g.text(font, label, x - font.width(label) / 2, y + 3, 0xFF22252C, false);
	}

	/** A controller face button (shown when Controlify reports controller input). */
	private void padButton(int x, int y, String label, boolean active) {
		int d = 14;
		ui.rounded(x - d / 2, y, d, d, d / 2, active ? 0xFFFFD27A : 0xFF3A3F4C);
		ui.rounded(x - d / 2 + 1, y + 1, d - 2, d - 2, (d - 2) / 2, active ? 0xFFFFE2A0 : 0xFF525867);
		ui.g.text(font, label, x - font.width(label) / 2 + 1, y + 3, active ? 0xFF22252C : 0xFFFFFFFF, false);
	}

	/**
	 * True when Controlify is installed and the last input came from a controller (checked by reflection through
	 * its public API, so there is no hard dependency).
	 */
	static boolean controllerActive() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("controlify")) {
			return false;
		}
		try {
			Class<?> api = Class.forName("dev.isxander.controlify.api.ControlifyApi");
			Object inst = api.getMethod("get").invoke(null);
			Object mode = api.getMethod("currentInputMode").invoke(inst);
			return mode != null && mode.toString().toUpperCase(java.util.Locale.ROOT).contains("CONTROLLER");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	private static List<ActiveCosmetic> cosmetics() {
		List<ActiveCosmetic> out = new ArrayList<>(ClientSkins.extrasFor(SkinLibrary.get().find(Wardrobe.get().skin())));
		out.addAll(ClientCosmeticSync.resolve(Wardrobe.get().equipped().entries(), false));
		return out;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();

		// arrow keys / Enter for keyboard-only use
		if (k == 262 || k == 264) {
			sel = sel < 0 ? 0 : (sel + 1) % 5;
			return true;
		}
		if (k == 263 || k == 265) {
			sel = sel < 0 ? 0 : (sel + 4) % 5;
			return true;
		}
		if ((k == 257 || k == 335) && sel >= 0) {
			play(sel);
			return true;
		}
		var km = de.behindtheblocks.skinstudio.client.BtbSkinStudioClient.emoteKeyMapping();
		if (km != null && km.matches(event)) {
			// held key repeats arrive as presses: ignore them; a new press after letting go closes the wheel
			if (!keyHeld && System.currentTimeMillis() - openedAt > 300) {
				onClose();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ui.click(event.x(), event.y(), event.button())) {
			return true;
		}
		// left click anywhere plays the selected emote, right click closes
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			onClose();
			return true;
		}
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && sel >= 0) {
			play(sel);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (sy != 0) {
			sel = sel < 0 ? 0 : Math.floorMod(sel + (sy < 0 ? 1 : -1), 5);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	/** Hold the wheel key, point, release: plays the selected emote. A short tap keeps the wheel open. */
	@Override
	public boolean keyReleased(KeyEvent event) {

		var km = de.behindtheblocks.skinstudio.client.BtbSkinStudioClient.emoteKeyMapping();
		if (km != null && km.matches(event) && keyHeld && System.currentTimeMillis() - openedAt > 300) {
			keyHeld = false;
			if (sel >= 0) {
				play(sel);
			} else {
				onClose();
			}
			return true;
		}
		if (km != null && km.matches(event)) {
			keyHeld = false;
		}
		return super.keyReleased(event);
	}

	public void autotest(String[] args) {
		if (args[0].equals("play")) {
			play(Integer.parseInt(args[1]));
		} else if (args[0].equals("mouse")) {
			if (fakeMouse == null) {
				lastMx = lastMy = -1; // counts as moved
			}
			fakeMouse = new int[]{Integer.parseInt(args[1]), Integer.parseInt(args[2])};
		} else if (args[0].equals("hover")) {
			sel = Integer.parseInt(args[1]);
			lastMx = lastMy = -1;
		}
	}

	@Override
	protected void init() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.fit(this);
		super.init();
		// opened by a key while playing: make sure the cursor is free (centred on the wheel), otherwise mouse
		// movement keeps turning the camera instead of reaching the wheel
		if (minecraft.mouseHandler.isMouseGrabbed()) {
			minecraft.mouseHandler.releaseMouse();
		}
		minecraft.mouseHandler.resyncMousePosition();
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
