package de.behindtheblocks.skinstudio.client.gui.ui;

import de.behindtheblocks.skinstudio.util.Tr;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny immediate-mode toolkit for the BTB screens: rounded glass panels, pill buttons, chips,
 * pixel icons and click regions. Each frame the screen draws and registers hit areas;
 * clicks are resolved against the regions of the last frame (top-most wins).
 */
public final class Ui {
	public record Hit(int x, int y, int w, int h, Runnable action, Runnable rightAction, String tooltip) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();
	private List<Hit> lastHits = new ArrayList<>();
	public GuiGraphicsExtractor g;
	public Font font;
	public int mouseX;
	public int mouseY;
	public Theme t = Theme.DARK;
	private String tooltip;

	public void begin(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		this.g = g;
		this.font = font;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.t = Theme.current();
		this.hits.clear();
		this.tooltip = null;
		this.wheelTarget = null;
	}

	public void end() {
		lastHits = new ArrayList<>(hits);
		if (tooltip != null) {
			g.setTooltipForNextFrame(font, Component.literal(tooltip), mouseX, mouseY);
		}
	}

	public boolean hover(int x, int y, int w, int h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	public void region(int x, int y, int w, int h, Runnable action) {
		hits.add(new Hit(x, y, w, h, action, null, null));
	}

	public void region(int x, int y, int w, int h, Runnable action, Runnable right, String tip) {
		hits.add(new Hit(x, y, w, h, action, right, tip));
		if (tip != null && hover(x, y, w, h)) {
			tooltip = tip;
		}
	}

	public void tooltip(String text) {
		tooltip = text;
	}

	/** @return true if a region consumed the click */
	public boolean click(double mx, double my, int button) {
		for (int i = lastHits.size() - 1; i >= 0; i--) {
			Hit h = lastHits.get(i);
			if (h.contains(mx, my)) {
				Runnable r = button == 1 ? h.rightAction : h.action;
				if (r != null) {
					r.run();
					return true;
				}
				return button == 0;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ shapes

	public void rounded(int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
		}
		g.fill(x, y + r, x + w, y + h - r, color);
	}

	public void roundedOutline(int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		int prev = -1;
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			if (i == 0) {
				g.fill(x + inset, y, x + w - inset, y + 1, color);
				g.fill(x + inset, y + h - 1, x + w - inset, y + h, color);
			} else {
				int len = Math.max(1, prev - inset);
				g.fill(x + inset, y + i, x + inset + len, y + i + 1, color);
				g.fill(x + w - inset - len, y + i, x + w - inset, y + i + 1, color);
				g.fill(x + inset, y + h - i - 1, x + inset + len, y + h - i, color);
				g.fill(x + w - inset - len, y + h - i - 1, x + w - inset, y + h - i, color);
			}
			prev = inset;
		}
		g.fill(x, y + r, x + 1, y + h - r, color);
		g.fill(x + w - 1, y + r, x + w, y + h - r, color);
	}

	/** Frosted glass card: translucent fill, hairline border, faint top highlight. */
	public void glass(int x, int y, int w, int h, int r) {
		rounded(x, y, w, h, r, t.glass);
		roundedOutline(x, y, w, h, r, t.glassBorder);
		g.fill(x + r, y + 1, x + w - r, y + 2, t.glassHighlight);
	}

	// ------------------------------------------------------------------ text

	public void text(String s, int x, int y, int color) {
		g.text(font, s, x, y, color, false);
	}

	public void textCentered(String s, int cx, int y, int color) {
		g.text(font, s, cx - font.width(s) / 2, y, color, false);
	}

	public String ellipsize(String s, int maxW) {
		if (font.width(s) <= maxW) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
	}

	/** Bold-ish title by drawing twice (crisp at all GUI scales). */
	public void title(String s, int x, int y, int color) {
		g.text(font, s, x, y, color, false);
		g.text(font, s, x + 1, y, color, false);
	}

	// ------------------------------------------------------------------ controls

	public enum Style {
		PRIMARY, SECONDARY, GHOST, DANGER
	}

	public boolean button(int x, int y, int w, int h, String label, Style style, Runnable action) {
		return button(x, y, w, h, label, null, style, true, action, null);
	}

	public boolean button(int x, int y, int w, int h, String label, Icon icon, Style style, boolean enabled, Runnable action, String tip) {
		boolean hov = enabled && hover(x, y, w, h);
		int bg, fg;
		switch (style) {
			case PRIMARY -> {
				bg = hov ? t.accentHover : t.accent;
				fg = t.onAccent;
			}
			case DANGER -> {
				bg = hov ? (t.danger & 0x00FFFFFF) | 0xFF000000 : (t.danger & 0x00FFFFFF) | 0xC0000000;
				fg = 0xFFFFFFFF;
			}
			case GHOST -> {
				bg = hov ? t.surfaceHover : 0;
				fg = t.text;
			}
			default -> {
				bg = hov ? t.surfaceHover : t.surface;
				fg = t.text;
			}
		}
		if (!enabled) {
			bg = (bg & 0x00FFFFFF) | 0x50000000;
			fg = t.textFaint;
		}
		if (bg != 0) {
			rounded(x, y, w, h, Math.min(h / 2, 7), bg);
		}
		if (style == Style.SECONDARY) {
			roundedOutline(x, y, w, h, Math.min(h / 2, 7), t.glassBorder);
		}
		int contentW = (icon != null ? 9 : 0) + (label != null && !label.isEmpty() ? font.width(label) + (icon != null ? 4 : 0) : 0);
		int cx = x + (w - contentW) / 2;
		int cy = y + (h - 8) / 2;
		if (icon != null) {
			icon.draw(g, cx, cy, fg);
			cx += 13;
		}
		if (label != null && !label.isEmpty()) {
			g.text(font, ellipsize(label, w - 8 - (icon != null ? 13 : 0)), icon != null ? cx : cx, cy, fg, false);
		}
		if (enabled) {
			region(x, y, w, h, action, null, tip);
		}
		return hov;
	}

	public void iconButton(int x, int y, int size, Icon icon, boolean active, Runnable action, String tip) {
		boolean hov = hover(x, y, size, size);
		if (hov || active) {
			rounded(x, y, size, size, 5, active ? (t.accent & 0x00FFFFFF) | 0x40000000 : t.surfaceHover);
		}
		icon.draw(g, x + (size - 7) / 2, y + (size - 7) / 2, active ? t.accent : hov ? t.text : t.textMuted);
		region(x, y, size, size, action, null, tip);
	}

	/** Pill toggle with label on the left. */
	public void toggle(int x, int y, int w, String label, boolean on, Runnable action) {
		text(label, x, y + 2, t.text);
		int sw = 22, sh = 12, sx = x + w - sw;
		rounded(sx, y, sw, sh, 6, on ? t.accent : t.surfaceHover);
		rounded(on ? sx + sw - 11 : sx + 1, y + 1, 10, 10, 5, on ? t.onAccent : t.textMuted);
		region(x, y - 2, w, sh + 4, action);
	}

	public void chip(int x, int y, String label, boolean selected, Runnable action, Runnable remove) {
		int w = font.width(label) + (remove != null ? 22 : 12);
		boolean hov = hover(x, y, w, 14);
		rounded(x, y, w, 14, 7, selected ? t.accent : hov ? t.surfaceHover : t.surface);
		g.text(font, label, x + 6, y + 3, selected ? t.onAccent : t.text, false);
		region(x, y, w, 14, action);
		if (remove != null) {
			int rx = x + w - 13;
			Icon.CLOSE.draw(g, rx, y + 3, hover(rx - 2, y, 12, 14) ? t.danger : t.textMuted);
			region(rx - 2, y, 12, 14, remove, null, Tr.t("btb.t.ablegen"));
		}
	}

	public int chipWidth(String label, boolean removable) {
		return font.width(label) + (removable ? 22 : 12);
	}

	/** Hovered stepper this frame: mouse wheel changes it (see {@link #scroll}). */
	private java.util.function.DoubleConsumer wheelTarget;

	/**
	 * Numeric row: label, value, − / + buttons. Mouse wheel over the row also changes the value.
	 */
	public void stepper(int x, int y, int w, String label, float value, float step, float min, float max, String unit,
						java.util.function.Consumer<Float> set) {
		boolean hov = hover(x, y, w, 14);
		if (hov) {
			rounded(x - 2, y - 1, w + 4, 16, 5, t.surfaceHover);
			wheelTarget = d -> set.accept(clamp(value + (float) Math.signum(d) * step, min, max));
		}
		text(label, x + 2, y + 3, t.textMuted);
		String v = format(value, step) + unit;
		int bw = 14;
		int vx = x + w - bw * 2 - 4 - font.width(v) - 4;
		text(v, vx, y + 3, t.text);
		int mx = x + w - bw * 2 - 2;
		button(mx, y, bw, 14, "−", Style.SECONDARY, () -> set.accept(clamp(value - step, min, max)));
		button(mx + bw + 2, y, bw, 14, "+", Style.SECONDARY, () -> set.accept(clamp(value + step, min, max)));
	}

	/** Horizontal slider 0..1 drawn as a track; clicking sets the value. */
	public void slider(int x, int y, int w, String label, float value01, int fillColor, java.util.function.Consumer<Float> set) {
		text(label, x + 2, y + 1, t.textMuted);
		int lx = x + 52, lw = w - 52;
		rounded(lx, y + 3, lw, 5, 2, t.surface);
		rounded(lx, y + 3, Math.max(5, (int) (lw * value01)), 5, 2, fillColor);
		int kx = lx + (int) (lw * value01) - 3;
		rounded(kx, y, 7, 11, 3, t.text);
		region(lx - 3, y - 2, lw + 6, 14, () -> set.accept(clamp((mouseX - lx) / (float) lw, 0, 1)));
		if (hover(lx - 3, y - 2, lw + 6, 14)) {
			wheelTarget = d -> set.accept(clamp(value01 + (float) Math.signum(d) * 0.02f, 0, 1));
		}
	}

	/** Cycle selector: "< value >"; left click next, right click previous. */
	public void cycle(int x, int y, int w, String label, String value, Runnable next, Runnable prev) {
		text(label, x + 2, y + 3, t.textMuted);
		int bx = x + w / 2 - 6, bw = w - (bx - x);
		boolean hov = hover(bx, y, bw, 14);
		rounded(bx, y, bw, 14, 6, hov ? t.surfaceHover : t.surface);
		text("‹", bx + 4, y + 3, t.textFaint);
		text("›", bx + bw - 8, y + 3, t.textFaint);
		textCentered(ellipsize(value, bw - 20), bx + bw / 2, y + 3, t.text);
		region(bx, y, bw, 14, next, prev, Tr.t("btb.t.klick_weiter_rechtsklick_zurueck"));
	}

	public boolean scroll(double mx, double my, double amount) {
		if (wheelTarget != null && amount != 0) {
			wheelTarget.accept(amount);
			return true;
		}
		return false;
	}

	public void clearWheel() {
		wheelTarget = null;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String format(float v, float step) {
		if (step >= 1) {
			return String.valueOf(Math.round(v));
		}
		if (step >= 0.1f) {
			return String.format(java.util.Locale.ROOT, "%.1f", v);
		}
		return String.format(java.util.Locale.ROOT, "%.2f", v);
	}

	public void divider(int x, int y, int w) {
		g.fill(x, y, x + w, y + 1, t.divider);
	}
}
