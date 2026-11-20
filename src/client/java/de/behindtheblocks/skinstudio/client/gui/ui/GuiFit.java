package de.behindtheblocks.skinstudio.client.gui.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps BTB screens usable with a large GUI scale: if the scaled window is smaller than the space the screen
 * needs, the next smaller GUI scale is used while a BTB screen is open. The player's setting is not changed and
 * the original scale comes back as soon as a non-BTB screen (or the game) is shown again.
 */
public final class GuiFit {
	public static final int MIN_W = 600, MIN_H = 330;
	private static int original = -1;
	private static int applied = -1;

	private GuiFit() {
	}

	/** Call first thing in {@code init()}; updates the screen's width/height when the scale changes. */
	public static void fit(Screen screen) {
		Window w = Minecraft.getInstance().getWindow();
		int cur = w.getGuiScale();
		if (cur != applied) {
			original = cur; // the game recalculated the scale (resize, option change): start from that
		}
		int best = 1;
		for (int s = original; s >= 1; s--) {
			if (w.getWidth() / s >= MIN_W && w.getHeight() / s >= MIN_H) {
				best = s;
				break;
			}
		}
		if (best != cur) {
			w.setGuiScale(best);
		}
		applied = best;
		screen.width = w.getGuiScaledWidth();
		screen.height = w.getGuiScaledHeight();
	}

	/** Restores the player's GUI scale (call in {@code removed()}; the next BTB screen fits again in its init). */
	public static void restore() {
		if (original < 0) {
			return;
		}
		Window w = Minecraft.getInstance().getWindow();
		if (w.getGuiScale() == applied && applied != original) {
			w.setGuiScale(original);
		}
		original = applied = -1;
	}
}
