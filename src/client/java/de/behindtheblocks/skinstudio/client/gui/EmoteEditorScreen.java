package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.emote.CustomEmoteLibrary;
import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.emote.CustomEmote;
import de.behindtheblocks.skinstudio.emote.Emotes;
import de.behindtheblocks.skinstudio.util.Tr;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Emote editor: build an own emote from keyframes. Pick a body part, move the time cursor on the timeline and
 * drag the sliders – that sets a keyframe for the part at that moment; in between the pose is eased. The figure
 * shows the pose at the cursor, or plays the whole emote. Saved emotes appear in the Emotes tab and can be put
 * on the wheel; other players with the mod see them too.
 */
public class EmoteEditorScreen extends Screen {
	private static final String[] ITEMS = {null, "minecraft:diamond", "minecraft:emerald", "minecraft:cookie", "minecraft:apple",
			"minecraft:golden_apple", "minecraft:cake", "minecraft:potion", "minecraft:red_banner", "minecraft:spyglass",
			"minecraft:goat_horn", "minecraft:diamond_pickaxe", "minecraft:diamond_sword", "minecraft:bow", "minecraft:fishing_rod",
			"minecraft:torch", "minecraft:book", "minecraft:poppy"};
	private static final float SNAP = 0.05f;

	private final Screen parent;
	/** Own emote being edited ({@code u_…}) or null for a new one. */
	private final String editId;
	private final CustomEmote emote;
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private EditBox name;
	private int part = Emotes.RIGHT_ARM / 3;
	private float time;
	private boolean playing;
	private long playStart;
	private float playFrom;
	private float[] clipboard;
	private String status;
	private long statusUntil;
	private boolean dirty;

	// interaction state (rectangles from the last frame)
	private int tlX, tlY, tlW, tlRowH, tlRows;
	private int pvX0, pvY0, pvX1, pvY1;
	private boolean scrubbing, rotating;
	private int dragAxis = -1;
	private int slX, slW;

	public EmoteEditorScreen(Screen parent, String editId) {
		super(Component.literal(Tr.t("btb.ui.emote_editor.title")));
		this.parent = parent;
		CustomEmote existing = editId == null ? null : Emotes.custom(editId);
		this.editId = existing == null ? null : editId;
		this.emote = existing == null ? starter() : existing.copy();
		preview.yaw = -30;
	}

	/** A new emote starts with a small wave, so there is something to change right away. */
	private static CustomEmote starter() {
		CustomEmote e = new CustomEmote();
		e.name = "";
		e.duration = 2f;
		int ra = Emotes.RIGHT_ARM / 3;
		e.setKey(ra, 0f, 0, 0, 0);
		e.setKey(ra, 0.5f, -160, 0, -25);
		e.setKey(ra, 1.5f, -160, 0, -25);
		e.setKey(ra, 2f, 0, 0, 0);
		return e;
	}

	@Override
	protected void init() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.fit(this);
		String old = name == null ? emote.name : name.getValue();
		name = new EditBox(font, 0, 0, 140, 12, Component.literal(Tr.t("btb.ui.emote_editor.name_hint")));
		name.setBordered(false);
		name.setMaxLength(32);
		name.setHint(Component.literal(Tr.t("btb.ui.emote_editor.name_hint")));
		name.setValue(old);
		name.setResponder(s -> dirty = true);
		addRenderableWidget(name);
		super.init();
	}

	@Override
	public void removed() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.restore();
		super.removed();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ editing

	private float[] valueOf(int p) {
		float[] v = emote.valueAt(p, time);
		return v == null ? new float[3] : v;
	}

	private void setAxis(int axis, float value) {
		float[] v = valueOf(part);
		v[axis] = value;
		emote.setKey(part, time, v[0], v[1], v[2]);
		dirty = true;
	}

	private void setTime(float t) {
		time = Math.max(0, Math.min(emote.duration, Math.round(t / SNAP) * SNAP));
	}

	private void jumpKey(int dir) {
		float best = Float.NaN;
		for (CustomEmote.Key k : emote.keysOf(part)) {
			if (dir > 0 && k.t() > time + 0.001f && (Float.isNaN(best) || k.t() < best)) {
				best = k.t();
			}
			if (dir < 0 && k.t() < time - 0.001f && (Float.isNaN(best) || k.t() > best)) {
				best = k.t();
			}
		}
		if (!Float.isNaN(best)) {
			time = best;
		}
	}

	/** The pose at the cursor becomes its mirror image: left and right swap. */
	private void mirror() {
		float[][] now = new float[CustomEmote.PARTS.length][];
		for (int p = 0; p < now.length; p++) {
			now[p] = emote.valueAt(p, time);
		}
		int[] other = {0, 1, 3, 2, 5, 4, 6, 7};
		for (int p = 0; p < now.length; p++) {
			float[] src = now[other[p]];
			if (src == null) {
				continue;
			}
			if (p == 7) {
				emote.setKey(p, time, -src[0], src[1], src[2]);
			} else {
				emote.setKey(p, time, src[0], -src[1], -src[2]);
			}
		}
		dirty = true;
	}

	private void copyPose() {
		clipboard = new float[CustomEmote.PARTS.length * 3];
		java.util.Arrays.fill(clipboard, Float.NaN);
		emote.poseAt(time, clipboard);
		flash(Tr.t("btb.ui.emote_editor.copied"));
	}

	private void pastePose() {
		if (clipboard == null) {
			return;
		}
		for (int p = 0; p < CustomEmote.PARTS.length; p++) {
			int o = p * 3;
			if (!Float.isNaN(clipboard[o])) {
				emote.setKey(p, time, clipboard[o], clipboard[o + 1], clipboard[o + 2]);
			}
		}
		dirty = true;
	}

	private void setDuration(float wanted) {
		float d = Math.max(CustomEmote.MIN_DURATION, Math.min(CustomEmote.MAX_DURATION, Math.round(wanted * 10) / 10f));
		if (d < emote.duration) {
			// keyframes after the new end are dropped
			emote.keys.removeIf(k -> k.t() > d + 0.001f);
		}
		emote.duration = d;
		time = Math.min(time, d);
		dirty = true;
	}

	private void save() {
		emote.name = name.getValue().strip();
		if (emote.name.isEmpty()) {
			emote.name = Tr.t("btb.ui.emote_editor.default_name");
			name.setValue(emote.name);
		}
		try {
			String id = CustomEmoteLibrary.save(editId, emote);
			dirty = false;
			if (parent instanceof WardrobeScreen w) {
				w.showEmote(id);
			}
			minecraft.gui.setScreen(parent);
		} catch (java.io.IOException e) {
			flash(Tr.t("btb.ui.emote_editor.save_failed", e.getMessage()));
		}
	}

	private void flash(String s) {
		status = s;
		statusUntil = System.currentTimeMillis() + 2600;
	}

	private void togglePlay() {
		playing = !playing;
		if (playing) {
			playFrom = time >= emote.duration - 0.01f ? 0 : time;
			playStart = System.nanoTime();
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(0, 0, width, height, Theme.current().scrim);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (playing) {
			float t = playFrom + (System.nanoTime() - playStart) / 1e9f;
			time = emote.duration <= 0 ? 0 : t % emote.duration;
		}
		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		int m = 8, top = 32, bottom = height - m;

		// top bar: back, title, name, save
		ui.iconButton(m, 7, 18, Icon.BACK, false, this::onClose, Tr.t("btb.ui.emote_editor.cancel"));
		String title = Tr.t("btb.ui.emote_editor.title");
		ui.title(title, m + 24, 12, t.text);
		String saveL = Tr.t("btb.ui.emote_editor.save");
		int sw = font.width(saveL) + 30;
		ui.button(width - m - sw, 7, sw, 18, saveL, Icon.SAVE, Ui.Style.PRIMARY, true, this::save, null);
		int nx = m + 30 + ui.titleWidth(title), nw = Math.max(80, Math.min(200, width - m - sw - 10 - nx));
		ui.rounded(nx, 7, nw, 18, 6, t.field);
		name.setX(nx + 6);
		name.setY(12);
		name.setWidth(nw - 12);

		// layout
		int tlH = 8 * 11 + 30;
		int upperBottom = bottom - tlH - 6;
		int leftW = 132;
		int rightW = Math.max(200, Math.min(280, width - 2 * m - leftW - 12 - 150));
		int pvW = width - 2 * m - leftW - rightW - 12;
		int lx = m, px = lx + leftW + 6, rx = px + pvW + 6;

		drawParts(lx, top, leftW, upperBottom - top);
		// figure
		ui.glass(px, top, pvW, upperBottom - top, 10);
		pvX0 = px;
		pvY0 = top;
		pvX1 = px + pvW;
		pvY1 = upperBottom;
		float[] pose = new float[Emotes.SIZE + 1];
		java.util.Arrays.fill(pose, Float.NaN);
		emote.poseAt(time, pose);
		pose[Emotes.SIZE] = 1;
		preview.poseOverride = pose;
		preview.itemOverride = emote.item;
		preview.draw(g, px + 4, top + 4, px + pvW - 4, upperBottom - 4, cosmetics());
		ui.text(ui.ellipsize(Tr.t("btb.ui.drag_to_rotate"), pvW - 12), px + 8, upperBottom - 14, t.textFaint);
		drawSliders(rx, top, rightW, upperBottom - top);
		drawTimeline(m, upperBottom + 6, width - 2 * m, bottom - upperBottom - 6);

		if (status != null && System.currentTimeMillis() < statusUntil) {
			int w = font.width(status) + 20;
			ui.rounded((width - w) / 2, top + 6, w, 16, 8, t.surfaceSelected);
			ui.textCentered(status, width / 2, top + 10, t.text);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private void drawParts(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		int cy = y + 8;
		ui.text(Tr.t("btb.ui.emote_editor.parts").toUpperCase(Locale.ROOT), x + 8, cy, t.textFaint);
		cy += 12;
		int rowH = Math.max(13, Math.min(16, (h - 60) / 8));
		for (int p = 0; p < CustomEmote.PARTS.length; p++) {
			boolean act = p == part;
			boolean hov = ui.hover(x + 4, cy, w - 8, rowH - 1);
			ui.rounded(x + 4, cy, w - 8, rowH - 1, 6, act ? (t.accent & 0xFFFFFF) | 0x38000000 : hov ? t.surfaceHover : 0);
			boolean keyed = !emote.keysOf(p).isEmpty();
			ui.rounded(x + 9, cy + rowH / 2 - 3, 5, 5, 2, keyed ? t.accent : t.divider);
			ui.text(ui.ellipsize(Tr.t("btb.ui.emote_editor.part." + CustomEmote.PARTS[p]), w - 28), x + 19, cy + (rowH - 8) / 2, act ? t.text : t.textMuted);
			final int pp = p;
			ui.region(x + 4, cy, w - 8, rowH - 1, () -> part = pp);
			cy += rowH;
		}
		// item in the right hand
		int iy = y + h - 22;
		ui.cycle(x + 6, iy, w - 12, Tr.t("btb.ui.emote_editor.item"), itemName(emote.item), () -> cycleItem(1), () -> cycleItem(-1));
	}

	private String itemName(String id) {
		if (id == null) {
			return Tr.t("btb.ui.emote_editor.item.none");
		}
		var stack = de.behindtheblocks.skinstudio.client.emote.ClientEmotes.item(id);
		return stack == null ? id : stack.getHoverName().getString();
	}

	private void cycleItem(int dir) {
		int i = java.util.Arrays.asList(ITEMS).indexOf(emote.item);
		emote.item = ITEMS[Math.floorMod(i + dir, ITEMS.length)];
		dirty = true;
	}

	private void drawSliders(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		int ix = x + 10, iw = w - 20, cy = y + 8;
		String partName = Tr.t("btb.ui.emote_editor.part." + CustomEmote.PARTS[part]);
		ui.title(ui.ellipsize(partName, iw), ix, cy, t.text);
		cy += 12;
		boolean keyed = emote.keyAt(part, time) != null;
		ui.text(ui.ellipsize(Tr.t(keyed ? "btb.ui.emote_editor.has_key" : "btb.ui.emote_editor.no_key",
				String.format(Locale.ROOT, "%.2f", time)), iw), ix, cy, keyed ? t.accent : t.textMuted);
		cy += 14;
		float[] v = valueOf(part);
		boolean move = part == 7;
		float limit = CustomEmote.limit(part);
		slX = ix;
		slW = iw;
		for (int axis = 0; axis < 3; axis++) {
			String label = Tr.t("btb.ui.emote_editor.axis." + (move ? "move" : "rot") + "." + "xyz".charAt(axis));
			String val = move ? String.format(Locale.ROOT, "%.1f px", v[axis]) : String.format(Locale.ROOT, "%.0f°", v[axis]);
			ui.text(ui.ellipsize(label, iw - 70), ix, cy, t.textMuted);
			ui.text(val, ix + iw - 36 - font.width(val), cy, t.text);
			float step = move ? 0.5f : 5f;
			final int ax = axis;
			ui.button(ix + iw - 32, cy - 3, 14, 13, "−", Ui.Style.SECONDARY, () -> setAxis(ax, Math.max(-limit, valueOf(part)[ax] - step)));
			ui.button(ix + iw - 16, cy - 3, 14, 13, "+", Ui.Style.SECONDARY, () -> setAxis(ax, Math.min(limit, valueOf(part)[ax] + step)));
			cy += 12;
			// track: centre = 0
			float f = (v[axis] + limit) / (2 * limit);
			ui.rounded(ix, cy + 2, iw, 5, 2, t.field);
			ui.roundedOutline(ix, cy + 2, iw, 5, 2, t.divider);
			int mid = ix + iw / 2;
			int kx = ix + Math.round(iw * f);
			ui.g.fill(Math.min(mid, kx), cy + 2, Math.max(mid, kx), cy + 7, t.accent | 0xFF000000);
			ui.g.fill(mid, cy, mid + 1, cy + 9, t.divider);
			ui.rounded(kx - 3, cy - 1, 7, 11, 3, dragAxis == axis ? t.accent | 0xFF000000 : t.text);
			ui.region(ix - 3, cy - 3, iw + 6, 14, () -> {
				dragAxis = ax;
				dragSlider(ui.mouseX);
			});
			cy += 16;
		}
		// keyframe tools
		int bw = (iw - 8) / 3;
		ui.button(ix, cy, bw, 16, "◀ " + Tr.t("btb.ui.emote_editor.key_prev"), Ui.Style.SECONDARY, () -> jumpKey(-1));
		ui.button(ix + bw + 4, cy, bw, 16, Tr.t("btb.ui.emote_editor.key_next") + " ▶", Ui.Style.SECONDARY, () -> jumpKey(1));
		ui.button(ix + 2 * (bw + 4), cy, bw, 16, Tr.t("btb.ui.emote_editor.key_delete"), Icon.TRASH, Ui.Style.SECONDARY, keyed, () -> {
			emote.removeKey(part, time);
			dirty = true;
		}, null);
		cy += 20;
		ui.button(ix, cy, bw, 16, Tr.t("btb.ui.emote_editor.mirror"), Icon.MIRROR, Ui.Style.SECONDARY, true, this::mirror,
				Tr.t("btb.ui.emote_editor.mirror_tip"));
		ui.button(ix + bw + 4, cy, bw, 16, Tr.t("btb.ui.emote_editor.copy"), Icon.COPY, Ui.Style.SECONDARY, true, this::copyPose,
				Tr.t("btb.ui.emote_editor.copy_tip"));
		ui.button(ix + 2 * (bw + 4), cy, bw, 16, Tr.t("btb.ui.emote_editor.paste"), Icon.IMPORT, Ui.Style.SECONDARY, clipboard != null, this::pastePose,
				Tr.t("btb.ui.emote_editor.paste_tip"));
		cy += 22;
		for (var line : font.split(Component.literal(Tr.t("btb.ui.emote_editor.hint")), iw)) {
			if (cy > y + h - 12) {
				break;
			}
			ui.g.text(font, line, ix, cy, t.textFaint, false);
			cy += 10;
		}
	}

	private void dragSlider(double mx) {
		if (dragAxis < 0) {
			return;
		}
		float limit = CustomEmote.limit(part);
		float f = (float) Math.max(0, Math.min(1, (mx - slX) / slW));
		float v = -limit + f * 2 * limit;
		v = part == 7 ? Math.round(v * 2) / 2f : Math.round(v);
		// a small dead zone at 0 makes the straight pose easy to hit
		if (Math.abs(v) < (part == 7 ? 0.5f : 3f)) {
			v = 0;
		}
		setAxis(dragAxis, v);
	}

	private void drawTimeline(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		int cy = y + 6;
		// header: play, time, length, repeat
		String playL = Tr.t(playing ? "btb.ui.emote_editor.pause" : "btb.ui.emote_editor.play");
		int pw = font.width(playL) + 30;
		ui.button(x + 8, cy, pw, 16, playL, playing ? Icon.PAUSE : Icon.PLAY, Ui.Style.PRIMARY, true, this::togglePlay, null);
		String tl = String.format(Locale.ROOT, "%.2f / %.1f s", time, emote.duration);
		ui.text(tl, x + 14 + pw, cy + 4, t.text);
		int hx = x + 22 + pw + font.width("00.00 / 00.0 s");
		String lenL = Tr.t("btb.ui.emote_editor.length");
		ui.text(lenL, hx, cy + 4, t.textMuted);
		hx += font.width(lenL) + 4;
		ui.button(hx, cy + 1, 14, 14, "−", Ui.Style.SECONDARY, () -> setDuration(emote.duration - 0.5f));
		String lv = String.format(Locale.ROOT, "%.1f s", emote.duration);
		ui.text(lv, hx + 18, cy + 4, t.text);
		hx += 22 + font.width("00.0 s");
		ui.button(hx, cy + 1, 14, 14, "+", Ui.Style.SECONDARY, () -> setDuration(emote.duration + 0.5f));
		hx += 22;
		String loopL = Tr.t("btb.ui.emote_editor.loop");
		int loopW = font.width(loopL) + 34;
		if (hx + loopW < x + w - 8) {
			ui.toggle(hx, cy + 2, loopW, loopL, emote.loop, () -> {
				emote.loop = !emote.loop;
				dirty = true;
			});
		}
		cy += 22;
		// tracks: one row per part, keyframes as diamonds, the cursor as a line
		int labelW = 86;
		tlX = x + 8 + labelW;
		tlW = w - 16 - labelW - 6;
		tlY = cy;
		tlRows = CustomEmote.PARTS.length;
		tlRowH = Math.max(8, (y + h - 6 - cy) / tlRows);
		for (int p = 0; p < tlRows; p++) {
			int ry = cy + p * tlRowH;
			boolean act = p == part;
			if (act) {
				ui.rounded(x + 4, ry, w - 8, tlRowH, 4, (t.accent & 0xFFFFFF) | 0x28000000);
			}
			String label = ui.ellipsize(Tr.t("btb.ui.emote_editor.part." + CustomEmote.PARTS[p]), labelW - 6);
			ui.g.text(font, label, x + 10, ry + (tlRowH - 8) / 2, act ? t.text : t.textMuted, false);
			ui.g.fill(tlX, ry + tlRowH / 2, tlX + tlW, ry + tlRowH / 2 + 1, t.divider);
			for (CustomEmote.Key k : emote.keysOf(p)) {
				int kx = tlX + Math.round(tlW * k.t() / emote.duration);
				int kc = act ? t.accent | 0xFF000000 : t.textMuted;
				int r = Math.max(2, Math.min(4, tlRowH / 2 - 1));
				for (int d = 0; d <= r; d++) {
					ui.g.fill(kx - (r - d), ry + tlRowH / 2 - d, kx + (r - d) + 1, ry + tlRowH / 2 - d + 1, kc);
					ui.g.fill(kx - (r - d), ry + tlRowH / 2 + d, kx + (r - d) + 1, ry + tlRowH / 2 + d + 1, kc);
				}
			}
		}
		// time marks every 0.5 s
		for (float s = 0; s <= emote.duration + 0.001f; s += 0.5f) {
			int mx = tlX + Math.round(tlW * s / emote.duration);
			ui.g.fill(mx, cy - 3, mx + 1, cy, t.textFaint);
		}
		int cx = tlX + Math.round(tlW * time / emote.duration);
		ui.g.fill(cx, cy - 4, cx + 1, cy + tlRows * tlRowH, t.accent | 0xFF000000);
		ui.rounded(cx - 3, cy - 6, 7, 5, 2, t.accent | 0xFF000000);
	}

	private void scrub(double mx, double my) {
		float f = (float) Math.max(0, Math.min(1, (mx - tlX) / tlW));
		float target = f * emote.duration;
		int row = (int) ((my - tlY) / tlRowH);
		if (row >= 0 && row < tlRows) {
			part = row;
			// snap onto a keyframe of that row when clicked close to it
			for (CustomEmote.Key k : emote.keysOf(row)) {
				if (Math.abs((k.t() - target) / emote.duration * tlW) <= 4) {
					time = k.t();
					return;
				}
			}
		}
		setTime(target);
	}

	private static List<ActiveCosmetic> cosmetics() {
		List<ActiveCosmetic> out = new ArrayList<>(ClientSkins.extrasFor(SkinLibrary.get().find(Wardrobe.get().skin())));
		out.addAll(ClientCosmeticSync.resolve(Wardrobe.get().equipped().entries(), false));
		return out;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (name.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		name.setFocused(false);
		if (ui.click(event.x(), event.y(), event.button())) {
			return true;
		}
		double mx = event.x(), my = event.y();
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			if (mx >= tlX - 4 && mx <= tlX + tlW + 4 && my >= tlY - 8 && my < tlY + tlRows * tlRowH) {
				playing = false;
				scrubbing = true;
				scrub(mx, my);
				return true;
			}
			if (mx >= pvX0 && mx < pvX1 && my >= pvY0 && my < pvY1) {
				rotating = true;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragAxis >= 0) {
			dragSlider(event.x());
			return true;
		}
		if (scrubbing) {
			setTime((float) Math.max(0, Math.min(1, (event.x() - tlX) / tlW)) * emote.duration);
			return true;
		}
		if (rotating) {
			preview.drag(dx, dy);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragAxis = -1;
		scrubbing = false;
		rotating = false;
		ui.endDrag();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (ui.scroll(mx, my, sy)) {
			return true;
		}
		if (mx >= tlX && mx <= tlX + tlW && my >= tlY && my < tlY + tlRows * tlRowH && sy != 0) {
			playing = false;
			setTime(time + (sy > 0 ? SNAP : -SNAP));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!name.isFocused()) {
			int k = event.key();
			if (k == 32) { // space
				togglePlay();
				return true;
			}
			if (k == 262 || k == 263) { // right / left: move the cursor
				playing = false;
				setTime(time + (k == 262 ? SNAP : -SNAP));
				return true;
			}
			if (k == 264 || k == 265) { // down / up: other body part
				part = Math.floorMod(part + (k == 264 ? 1 : -1), CustomEmote.PARTS.length);
				return true;
			}
			if (k == 261 || k == 259) { // delete / backspace: remove the keyframe at the cursor
				emote.removeKey(part, time);
				dirty = true;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	/** Autotest hooks: "time 1.2", "part 2", "set 0 -90", "play", "save", "name Text". */
	public void autotest(String[] args) {
		switch (args[0]) {
			case "time" -> {
				playing = false;
				setTime(Float.parseFloat(args[1]));
			}
			case "part" -> part = Integer.parseInt(args[1]);
			case "set" -> setAxis(Integer.parseInt(args[1]), Float.parseFloat(args[2]));
			case "play" -> togglePlay();
			case "mirror" -> mirror();
			case "length" -> setDuration(Float.parseFloat(args[1]));
			case "item" -> cycleItem(Integer.parseInt(args[1]));
			case "name" -> name.setValue(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
			case "save" -> save();
			default -> {
			}
		}
	}
}
