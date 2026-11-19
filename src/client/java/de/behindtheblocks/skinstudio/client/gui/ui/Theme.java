package de.behindtheblocks.skinstudio.client.gui.ui;

import de.behindtheblocks.skinstudio.client.data.ClientSettings;

/**
 * BTB Studios design tokens: calm dark/light palettes, glass surfaces, one warm accent.
 * No neon, no gradients beyond a subtle top highlight on glass.
 */
public final class Theme {
	public final int scrim;
	public final int glass;
	public final int glassBorder;
	public final int glassHighlight;
	public final int surface;
	public final int surfaceHover;
	public final int surfaceSelected;
	public final int text;
	public final int textMuted;
	public final int textFaint;
	public final int accent;
	public final int accentHover;
	public final int onAccent;
	public final int danger;
	public final int success;
	public final int divider;
	public final int field;
	public final int stage;
	public final boolean dark;

	private Theme(boolean dark, int scrim, int glass, int glassBorder, int glassHighlight, int surface, int surfaceHover, int surfaceSelected,
				  int text, int textMuted, int textFaint, int accent, int accentHover, int onAccent, int danger, int success, int divider, int field, int stage) {
		this.dark = dark;
		this.scrim = scrim;
		this.glass = glass;
		this.glassBorder = glassBorder;
		this.glassHighlight = glassHighlight;
		this.surface = surface;
		this.surfaceHover = surfaceHover;
		this.surfaceSelected = surfaceSelected;
		this.text = text;
		this.textMuted = textMuted;
		this.textFaint = textFaint;
		this.accent = accent;
		this.accentHover = accentHover;
		this.onAccent = onAccent;
		this.danger = danger;
		this.success = success;
		this.divider = divider;
		this.field = field;
		this.stage = stage;
	}

	public static final Theme DARK = new Theme(true,
			0x9A0D0F14, 0xA61A1D25, 0x2EFFFFFF, 0x14FFFFFF,
			0x9C252933, 0xC0303541, 0xE03A404F,
			0xFFF2F4F8, 0xFFA7ADB9, 0xFF6F7583,
			0xFFF0B347, 0xFFF7C46A, 0xFF1D1A14,
			0xFFE5655C, 0xFF56C691, 0x22FFFFFF, 0xC0151820, 0x5A0B0D12);

	public static final Theme LIGHT = new Theme(false,
			0x70E8EBF1, 0xD9FAFBFD, 0x40FFFFFF, 0x66FFFFFF,
			0xE6EEF1F6, 0xFFFFFFFF, 0xFFFFFFFF,
			0xFF1B1E25, 0xFF5B6270, 0xFF8E95A2,
			0xFFC98712, 0xFFDB9A22, 0xFFFFFFFF,
			0xFFC9443B, 0xFF2E9E69, 0x1A000000, 0xFFFFFFFF, 0x30BFC6D3);

	public static Theme current() {
		return ClientSettings.get().theme == ClientSettings.Theme.LIGHT ? LIGHT : DARK;
	}
}
