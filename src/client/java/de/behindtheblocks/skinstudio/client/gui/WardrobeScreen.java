package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.editor.CosmeticEditorScreen;
import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.render.CosmeticTextures;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticCategory;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.util.Colors;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The wardrobe: browse, search, preview and equip cosmetics. The 3D preview in the middle is the
 * centerpiece; selecting a cosmetic tries it on immediately, equipping makes it permanent and
 * syncs it to the server. Several cosmetics can be worn at once; conflicting ones are swapped
 * automatically with a short notice.
 */
public class WardrobeScreen extends Screen {
	private enum Filter {
		ALL, FAVORITES, RECENT, EQUIPPED, CATEGORY, PACK
	}

	private final Screen parent;
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private EditBox search;
	private Filter filter = Filter.ALL;
	private CosmeticCategory category;
	private String packFilter;
	private String selectedId;
	private int pickColor;
	private int gridScroll;
	private int sideScroll;
	private int gridRows;
	private int gridCols;
	private boolean draggingPreview;
	private boolean settingsOpen;
	private String toast;
	private long toastUntil;
	private boolean toastError;
	private int previewX0, previewY0, previewX1, previewY1;
	private int gridX, gridY, gridW, gridH;
	private int sideX, sideY, sideW, sideH;

	public WardrobeScreen(Screen parent) {
		super(Component.literal("Garderobe"));
		this.parent = parent;
	}

	/** Adds a "Garderobe" button to the pause menu and the title screen. */
	public static void registerMenuButtons() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
				Button b = Button.builder(Component.literal("Garderobe"), btn -> client.gui.setScreen(new WardrobeScreen(screen)))
						.bounds(w - 86, 6, 80, 20).tooltip(Tooltip.create(Component.literal("BTB Skin Studio – Cosmetics (Taste J)"))).build();
				Screens.getWidgets(screen).add(b);
			}
		});
	}

	@Override
	protected void init() {
		String old = search == null ? "" : search.getValue();
		search = new EditBox(font, 0, 0, 100, 12, Component.literal("Suche"));
		search.setBordered(false);
		search.setMaxLength(64);
		search.setHint(Component.literal("Suchen …"));
		search.setValue(old);
		search.setResponder(s -> gridScroll = 0);
		addRenderableWidget(search);
		if (CosmeticLibrary.get().revision() == 0 && !CosmeticLibrary.get().isLoading()) {
			CosmeticLibrary.get().reload();
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ data

	private static String lang() {
		return CosmeticLibrary.language();
	}

	private List<CosmeticAsset> visible() {
		CosmeticLibrary lib = CosmeticLibrary.get();
		Wardrobe w = Wardrobe.get();
		List<CosmeticAsset> list = new ArrayList<>();
		switch (filter) {
			case RECENT -> {
				for (String id : w.recent()) {
					CosmeticAsset a = lib.find(id);
					if (a != null) {
						list.add(a);
					}
				}
			}
			case EQUIPPED -> {
				for (Loadout.Entry e : w.equipped().entries()) {
					CosmeticAsset a = lib.find(e.id());
					if (a != null) {
						list.add(a);
					}
				}
			}
			default -> {
				for (CosmeticAsset a : lib.all()) {
					if (filter == Filter.FAVORITES && !w.isFavorite(a.id())) {
						continue;
					}
					if (filter == Filter.CATEGORY && a.definition.category() != category) {
						continue;
					}
					if (filter == Filter.PACK && !a.packId.equals(packFilter)) {
						continue;
					}
					list.add(a);
				}
				list.sort(Comparator.comparing((CosmeticAsset a) -> a.definition.category().ordinal())
						.thenComparing(a -> a.definition.name(lang()).toLowerCase(Locale.ROOT)));
			}
		}
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (!q.isEmpty()) {
			// search always covers every installed cosmetic, regardless of the selected category
			List<CosmeticAsset> all = new ArrayList<>(lib.all());
			all.sort(Comparator.comparing(a -> a.definition.name(lang()).toLowerCase(Locale.ROOT)));
			all.removeIf(a -> !matches(a.definition, q));
			return all;
		}
		return list;
	}

	private static boolean matches(CosmeticDefinition d, String q) {
		if (d.id.contains(q) || d.type.label.toLowerCase(Locale.ROOT).contains(q) || d.category().labelDe.toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}
		for (String n : d.names.values()) {
			if (n.toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		for (String t : d.tags) {
			if (t.toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		return false;
	}

	/** Equipped cosmetics plus the selected one "tried on" (conflicts resolved like a real equip). */
	private List<ActiveCosmetic> previewCosmetics() {
		Loadout l = Wardrobe.get().equipped().copy();
		CosmeticAsset sel = selected();
		if (sel != null) {
			if (!l.contains(sel.id())) {
				l.equip(sel.definition, pickColor, "", CosmeticLibrary.get()::definition);
			} else if (pickColor != 0) {
				l.setColor(sel.id(), pickColor);
			}
		}
		return ClientCosmeticSync.resolve(l.entries(), false);
	}

	private CosmeticAsset selected() {
		return selectedId == null ? null : CosmeticLibrary.get().find(selectedId);
	}

	private void select(CosmeticAsset a) {
		if (a == null) {
			selectedId = null;
			return;
		}
		selectedId = a.id();
		Loadout.Entry e = Wardrobe.get().equipped().find(a.id());
		pickColor = e == null ? 0 : e.color();
		preview.focus = switch (a.definition.attachment) {
			case HEAD -> PlayerPreview.Focus.HEAD;
			case LEFT_LEG, RIGHT_LEG -> PlayerPreview.Focus.LEGS;
			case LEFT_SHOULDER, RIGHT_SHOULDER -> PlayerPreview.Focus.UPPER;
			default -> PlayerPreview.Focus.FULL;
		};
		// yaw 0 shows the front, 180 the back
		float yaw = ((preview.yaw % 360) + 360) % 360;
		if (a.definition.attachment == de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint.BACK || a.definition.type.category == CosmeticCategory.BACK) {
			preview.yaw = 155;
		} else if (yaw > 90 && yaw < 270) {
			preview.yaw = -25;
		}
	}

	private void toggleEquip(CosmeticAsset a) {
		Wardrobe w = Wardrobe.get();
		Loadout l = w.equipped().copy();
		if (l.contains(a.id())) {
			l.unequip(a.id());
			w.setEquipped(l);
			toast(a.definition.name(lang()) + " abgelegt", false);
			return;
		}
		Loadout.EquipResult r = l.equip(a.definition, pickColor, a.contentHash, CosmeticLibrary.get()::definition);
		if (!r.equipped()) {
			toast(r.message(), true);
			return;
		}
		w.markUsed(a.id());
		w.setEquipped(l);
		if (r.removed().isEmpty()) {
			toast(a.definition.name(lang()) + " angelegt", false);
		} else {
			List<String> names = new ArrayList<>();
			for (String id : r.removed()) {
				CosmeticDefinition d = CosmeticLibrary.get().definition(id);
				names.add(d == null ? id : d.name(lang()));
			}
			toast("Ersetzt " + String.join(", ", names) + " (gleicher Bereich)", false);
		}
	}

	private void toast(String msg, boolean error) {
		toast = msg;
		toastError = error;
		toastUntil = System.currentTimeMillis() + 2600;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(0, 0, width, height, Theme.current().scrim);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		int m = 8;
		int top = 30;
		// ---- header
		ui.title("BTB Skin Studio", m + 2, 11, t.text);
		ui.text("Garderobe", m + 2 + font.width("BTB Skin Studio") + 10, 11, t.textMuted);
		int bx = width - m - 18;
		ui.iconButton(bx, 6, 18, Icon.CLOSE, false, this::onClose, "Schließen");
		bx -= 22;
		ui.iconButton(bx, 6, 18, Icon.GEAR, settingsOpen, () -> settingsOpen = !settingsOpen, "Einstellungen");
		bx -= 22;
		ui.iconButton(bx, 6, 18, t.dark ? Icon.SUN : Icon.MOON, false, () -> {
			ClientSettings s = ClientSettings.get();
			s.theme = s.theme == ClientSettings.Theme.DARK ? ClientSettings.Theme.LIGHT : ClientSettings.Theme.DARK;
			s.save();
		}, t.dark ? "Helle Darstellung" : "Dunkle Darstellung");
		bx -= 4;
		int w1 = font.width("Packs") + 26;
		bx -= w1;
		ui.button(bx, 6, w1, 18, "Packs", Icon.BOX, Ui.Style.SECONDARY, true, () -> minecraft.gui.setScreen(new PackManagerScreen(this)), "Packs installieren, importieren, exportieren");
		int w2 = font.width("Editor") + 26;
		bx -= w2 + 4;
		ui.button(bx, 6, w2, 18, "Editor", Icon.EDIT, Ui.Style.SECONDARY, true, () -> minecraft.gui.setScreen(CosmeticEditorScreen.open(this, null)), "Eigenes Cosmetic erstellen");

		// ---- layout
		int bottom = height - m;
		sideX = m;
		sideY = top;
		sideW = Math.max(96, Math.min(124, width / 6));
		sideH = bottom - top;
		int stageX = sideX + sideW + 6;
		int stageW = Math.max(130, Math.min(220, (int) (width * 0.30)));
		int rightX = stageX + stageW + 6;
		int rightW = width - m - rightX;

		drawSidebar(sideX, sideY, sideW, sideH);
		drawStage(stageX, top, stageW, bottom - top);
		drawBrowser(rightX, top, rightW, bottom - top);

		if (settingsOpen) {
			drawSettings(width - m - 170, 28);
		}
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			int tw = font.width(toast) + 20;
			int tx = stageX + (stageW - tw) / 2, ty = bottom - 24;
			ui.rounded(tx, ty, tw, 18, 9, toastError ? (t.danger & 0xFFFFFF) | 0xE0000000 : t.dark ? 0xE6303644 : 0xF0FFFFFF);
			ui.text(toast, tx + 10, ty + 5, toastError ? 0xFFFFFFFF : t.text);
		}
		if (CosmeticLibrary.get().isLoading()) {
			ui.textCentered("Cosmetics werden geladen …", width / 2, height / 2, t.textMuted);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private void navItem(int x, int y, int w, String label, int count, boolean active, Icon icon, Runnable action) {
		Theme t = ui.t;
		boolean hov = ui.hover(x, y, w, 14);
		if (active || hov) {
			ui.rounded(x, y, w, 14, 5, active ? (t.accent & 0xFFFFFF) | 0x33000000 : t.surfaceHover);
		}
		int tx = x + 6;
		if (icon != null) {
			icon.draw(ui.g, x + 5, y + 3, active ? t.accent : t.textMuted);
			tx = x + 16;
		}
		String c = count < 0 ? "" : String.valueOf(count);
		ui.text(ui.ellipsize(label, w - (tx - x) - font.width(c) - 8), tx, y + 3, active ? t.text : t.textMuted);
		if (!c.isEmpty()) {
			ui.text(c, x + w - 5 - font.width(c), y + 3, t.textFaint);
		}
		ui.region(x, y, w, 14, action);
	}

	private void drawSidebar(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 8);
		CosmeticLibrary lib = CosmeticLibrary.get();
		Wardrobe wd = Wardrobe.get();
		ui.g.enableScissor(x + 1, y + 4, x + w - 1, y + h - 4);
		int cy = y + 6 - sideScroll;
		int iw = w - 8;
		int ix = x + 4;
		navItem(ix, cy, iw, "Alle", lib.all().size(), filter == Filter.ALL, Icon.LAYERS, () -> setFilter(Filter.ALL, null, null));
		cy += 15;
		navItem(ix, cy, iw, "Favoriten", wd.favorites().size(), filter == Filter.FAVORITES, Icon.STAR, () -> setFilter(Filter.FAVORITES, null, null));
		cy += 15;
		navItem(ix, cy, iw, "Zuletzt verwendet", -1, filter == Filter.RECENT, Icon.CLOCK, () -> setFilter(Filter.RECENT, null, null));
		cy += 15;
		navItem(ix, cy, iw, "Ausgerüstet", wd.equipped().entries().size(), filter == Filter.EQUIPPED, Icon.HANGER, () -> setFilter(Filter.EQUIPPED, null, null));
		cy += 19;
		ui.text("KATEGORIEN", ix + 4, cy, t.textFaint);
		cy += 11;
		for (CosmeticCategory c : CosmeticCategory.values()) {
			int count = (int) lib.all().stream().filter(a -> a.definition.category() == c).count();
			navItem(ix, cy, iw, c.labelDe, count, filter == Filter.CATEGORY && category == c, null, () -> setFilter(Filter.CATEGORY, c, null));
			cy += 15;
		}
		cy += 4;
		ui.text("PACKS", ix + 4, cy, t.textFaint);
		cy += 11;
		for (LoadedPack p : lib.packs()) {
			if (p.cosmetics.isEmpty()) {
				continue;
			}
			String pid = p.id();
			navItem(ix, cy, iw, p.manifest.name(lang()), p.cosmetics.size(), filter == Filter.PACK && pid.equals(packFilter), null, () -> setFilter(Filter.PACK, null, pid));
			cy += 15;
		}
		if (!wd.outfits().isEmpty()) {
			cy += 4;
			ui.text("OUTFITS", ix + 4, cy, t.textFaint);
			cy += 11;
			for (var e : new ArrayList<>(wd.outfits().entrySet())) {
				String name = e.getKey();
				boolean hov = ui.hover(ix, cy, iw, 14);
				if (hov) {
					ui.rounded(ix, cy, iw, 14, 5, t.surfaceHover);
				}
				ui.text(ui.ellipsize(name, iw - 22), ix + 6, cy + 3, t.textMuted);
				ui.region(ix, cy, iw - 14, 14, () -> {
					wd.setEquipped(e.getValue());
					toast("Outfit „" + name + "“ angelegt", false);
				}, null, "Outfit anlegen");
				Icon.CLOSE.draw(ui.g, ix + iw - 11, cy + 3, ui.hover(ix + iw - 14, cy, 14, 14) ? t.danger : t.textFaint);
				ui.region(ix + iw - 14, cy, 14, 14, () -> wd.deleteOutfit(name), null, "Outfit löschen");
				cy += 15;
			}
		}
		int contentH = cy + sideScroll - (y + 6);
		sideScroll = Math.max(0, Math.min(sideScroll, contentH - h + 12));
		ui.g.disableScissor();
	}

	private void setFilter(Filter f, CosmeticCategory c, String pack) {
		filter = f;
		category = c;
		packFilter = pack;
		gridScroll = 0;
	}

	private void drawStage(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		int chipsH = 0;
		List<Loadout.Entry> equipped = Wardrobe.get().equipped().entries();
		// equipped chips (wrap)
		List<int[]> chipPos = new ArrayList<>();
		int cx = x + 8, cyy = 0, rows = equipped.isEmpty() ? 0 : 1;
		for (Loadout.Entry e : equipped) {
			CosmeticDefinition d = CosmeticLibrary.get().definition(e.id());
			String label = ui.ellipsize(d == null ? e.id() : d.name(lang()), w - 40);
			int cw = ui.chipWidth(label, true);
			if (cx + cw > x + w - 8) {
				cx = x + 8;
				rows++;
			}
			chipPos.add(new int[]{cx, rows - 1});
			cx += cw + 4;
		}
		chipsH = rows * 17;
		int buttonsH = 22;
		previewX0 = x + 4;
		previewY0 = y + 4;
		previewX1 = x + w - 4;
		previewY1 = y + h - 8 - chipsH - buttonsH - (rows > 0 ? 14 : 16);
		ui.rounded(previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, 8, t.stage);
		preview.draw(ui.g, previewX0, previewY0, previewX1, previewY1, previewCosmetics());
		// hint + view buttons
		ui.text("Ziehen zum Drehen", previewX0 + 6, previewY1 - 12, t.textFaint);
		int vx = previewX1 - 4;
		for (PlayerPreview.Focus f : new PlayerPreview.Focus[]{PlayerPreview.Focus.LEGS, PlayerPreview.Focus.UPPER, PlayerPreview.Focus.HEAD, PlayerPreview.Focus.FULL}) {
			String l = switch (f) {
				case FULL -> "Ganz";
				case HEAD -> "Kopf";
				case UPPER -> "Oben";
				case LEGS -> "Beine";
			};
			int bw = font.width(l) + 8;
			vx -= bw;
			boolean act = preview.focus == f;
			boolean hov = ui.hover(vx, previewY0 + 4, bw, 12);
			ui.rounded(vx, previewY0 + 4, bw, 12, 6, act ? t.accent : hov ? t.surfaceHover : t.surface);
			ui.text(l, vx + 4, previewY0 + 6, act ? t.onAccent : t.textMuted);
			PlayerPreview.Focus ff = f;
			ui.region(vx, previewY0 + 4, bw, 12, () -> preview.focus = ff);
			vx -= 3;
		}
		ui.region(previewX0, previewY0 + 18, previewX1 - previewX0, previewY1 - previewY0 - 18, () -> draggingPreview = true);

		// equipped chips
		int chipsY = previewY1 + 6;
		if (rows > 0) {
			ui.text("Getragen (" + equipped.size() + ")", x + 8, chipsY, t.textMuted);
			chipsY += 12;
			for (int i = 0; i < equipped.size(); i++) {
				Loadout.Entry e = equipped.get(i);
				CosmeticDefinition d = CosmeticLibrary.get().definition(e.id());
				String label = ui.ellipsize(d == null ? e.id() : d.name(lang()), w - 40);
				int[] p = chipPos.get(i);
				String id = e.id();
				ui.chip(p[0], chipsY + p[1] * 17, label, id.equals(selectedId), () -> select(CosmeticLibrary.get().find(id)), () -> {
					Loadout l = Wardrobe.get().equipped().copy();
					l.unequip(id);
					Wardrobe.get().setEquipped(l);
				});
			}
		} else {
			ui.text("Noch nichts angelegt", x + 8, chipsY, t.textFaint);
		}
		// bottom buttons
		int by = y + h - 4 - buttonsH + 2;
		int half = (w - 20) / 2;
		ui.button(x + 8, by, half, 18, "Alle ablegen", null, Ui.Style.SECONDARY, !equipped.isEmpty(), () -> {
			Wardrobe.get().setEquipped(new Loadout());
			toast("Alle Cosmetics abgelegt", false);
		}, null);
		ui.button(x + 12 + half, by, half, 18, "Als Outfit", null, Ui.Style.SECONDARY, !equipped.isEmpty(), () -> {
			String name = "Outfit " + (Wardrobe.get().outfits().size() + 1);
			Wardrobe.get().saveOutfit(name, Wardrobe.get().equipped());
			toast("„" + name + "“ gespeichert", false);
		}, "Aktuelle Kombination als Outfit speichern");
	}

	private void drawBrowser(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		// search field
		int sx = x + 8, sy = y + 8, sw = w - 16;
		ui.rounded(sx, sy, sw, 18, 9, t.field);
		ui.roundedOutline(sx, sy, sw, 18, 9, search.isFocused() ? t.accent : t.glassBorder);
		Icon.SEARCH.draw(ui.g, sx + 7, sy + 5, t.textMuted);
		search.setX(sx + 19);
		search.setY(sy + 5);
		search.setWidth(sw - 26);
		search.setTextColor(t.text);

		// detail panel
		CosmeticAsset sel = selected();
		int detailH = sel == null ? 0 : (sel.definition.tint.enabled ? 92 : 74);
		gridX = x + 8;
		gridY = sy + 24;
		gridW = w - 16;
		gridH = y + h - 8 - gridY - (detailH > 0 ? detailH + 6 : 0);

		List<CosmeticAsset> list = visible();
		int cardW = 52, cardH = 62, gap = 5;
		gridCols = Math.max(1, (gridW + gap) / (cardW + gap));
		cardW = (gridW - (gridCols - 1) * gap) / gridCols;
		gridRows = (list.size() + gridCols - 1) / gridCols;
		int contentH = gridRows * (cardH + gap);
		gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, contentH - gridH)));
		ui.g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
		if (list.isEmpty()) {
			String msg = switch (filter) {
				case FAVORITES -> "Noch keine Favoriten – markiere Cosmetics mit dem Stern.";
				case RECENT -> "Noch nichts verwendet.";
				case EQUIPPED -> "Du trägst gerade nichts.";
				default -> search.getValue().isEmpty() ? "Hier gibt es noch keine Cosmetics." : "Nichts gefunden für „" + search.getValue() + "“.";
			};
			ui.textCentered(ui.ellipsize(msg, gridW - 10), gridX + gridW / 2, gridY + 30, t.textMuted);
		}
		Wardrobe wd = Wardrobe.get();
		for (int i = 0; i < list.size(); i++) {
			int col = i % gridCols, row = i / gridCols;
			int cx = gridX + col * (cardW + gap), cy = gridY + row * (cardH + gap) - gridScroll;
			if (cy + cardH < gridY || cy > gridY + gridH) {
				continue;
			}
			drawCard(list.get(i), cx, cy, cardW, cardH, wd);
		}
		ui.g.disableScissor();
		if (contentH > gridH) {
			int barH = Math.max(14, gridH * gridH / contentH);
			int barY = gridY + (int) ((gridH - barH) * (gridScroll / (float) Math.max(1, contentH - gridH)));
			ui.rounded(x + w - 5, barY, 3, barH, 1, t.textFaint);
		}
		if (sel != null) {
			drawDetail(sel, x + 8, y + h - 8 - detailH, w - 16, detailH);
		}
	}

	private void drawCard(CosmeticAsset a, int x, int y, int w, int h, Wardrobe wd) {
		Theme t = ui.t;
		boolean hov = ui.hover(x, y, w, h) && ui.mouseY >= gridY && ui.mouseY < gridY + gridH;
		boolean sel = a.id().equals(selectedId);
		boolean equipped = wd.equipped().contains(a.id());
		ui.rounded(x, y, w, h, 7, sel ? t.surfaceSelected : hov ? t.surfaceHover : t.surface);
		if (sel || equipped) {
			ui.roundedOutline(x, y, w, h, 7, sel ? t.accent : (t.accent & 0xFFFFFF) | 0x70000000);
		}
		int icon = Math.min(w - 8, h - 18);
		Identifier tex = CosmeticTextures.get(a, 0, CosmeticTextures.Layer.PREVIEW);
		int ix = x + (w - icon) / 2, iy = y + 4;
		if (tex != null) {
			ui.g.blit(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0, 0, icon, icon, 128, 128, 128, 128);
		} else {
			ui.rounded(ix + 4, iy + 4, icon - 8, icon - 8, 6, t.stage);
			String letter = a.definition.name(lang()).substring(0, 1).toUpperCase(Locale.ROOT);
			ui.textCentered(letter, ix + icon / 2, iy + icon / 2 - 4, t.textMuted);
		}
		ui.textCentered(ui.ellipsize(a.definition.name(lang()), w - 6), x + w / 2, y + h - 12, sel ? t.text : t.textMuted);
		if (wd.isFavorite(a.id())) {
			Icon.STAR_FILLED.draw(ui.g, x + 4, y + 4, t.accent);
		}
		if (equipped) {
			ui.rounded(x + w - 13, y + 3, 10, 10, 5, t.accent);
			Icon.CHECK.draw(ui.g, x + w - 12, y + 5, t.onAccent);
		}
		if (ui.mouseY >= gridY && ui.mouseY < gridY + gridH) {
			ui.region(x, y, w, h, () -> {
				if (a.id().equals(selectedId)) {
					toggleEquip(a);
				} else {
					select(a);
				}
			}, () -> wd.toggleFavorite(a.id()), null);
		}
	}

	private void drawDetail(CosmeticAsset a, int x, int y, int w, int h) {
		Theme t = ui.t;
		CosmeticDefinition d = a.definition;
		ui.rounded(x, y, w, h, 8, t.surface);
		int px = x + 8, py = y + 7;
		ui.title(ui.ellipsize(d.name(lang()), w - 40), px, py, t.text);
		boolean fav = Wardrobe.get().isFavorite(a.id());
		ui.iconButton(x + w - 22, y + 4, 16, fav ? Icon.STAR_FILLED : Icon.STAR, fav, () -> Wardrobe.get().toggleFavorite(a.id()), fav ? "Aus Favoriten entfernen" : "Zu Favoriten");
		py += 11;
		LoadedPack pack = CosmeticLibrary.get().packs().stream().filter(p -> p.id().equals(a.packId)).findFirst().orElse(null);
		String meta = d.category().labelDe + " · " + d.type.label + " · " + d.attachment.label + (pack != null ? " · " + pack.manifest.name(lang()) : "");
		ui.text(ui.ellipsize(meta, w - 16), px, py, t.textMuted);
		py += 11;
		String desc = d.description(lang());
		if (!desc.isEmpty()) {
			var lines = font.split(Component.literal(desc), w - 16);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				ui.g.text(font, lines.get(i), px, py, t.textFaint, false);
				py += 10;
			}
		}
		if (d.tint.enabled) {
			int sy = y + h - 42;
			ui.text("Farbe", px, sy + 3, t.textMuted);
			int cx = px + font.width("Farbe") + 8;
			List<Integer> pal = new ArrayList<>(d.tint.palette);
			if (pal.isEmpty()) {
				pal.add(0xFFFFFFFF);
			}
			for (int c : pal) {
				boolean act = (pickColor == 0 ? d.tint.defaultColor : pickColor) == c;
				ui.rounded(cx, sy, 14, 14, 7, act ? t.accent : t.glassBorder);
				ui.rounded(cx + 2, sy + 2, 10, 10, 5, c | 0xFF000000);
				int cc = c;
				ui.region(cx, sy, 14, 14, () -> applyColor(a, cc), null, Colors.format(c));
				cx += 17;
			}
			// hue strip for free colors
			int hx = cx + 4, hw = Math.max(30, x + w - 8 - hx);
			for (int i = 0; i < hw; i++) {
				ui.g.fill(hx + i, sy + 3, hx + i + 1, sy + 11, Colors.hsv(i * 360f / hw, 0.55f, 1f));
			}
			ui.roundedOutline(hx - 1, sy + 2, hw + 2, 10, 2, t.glassBorder);
			ui.region(hx, sy, hw, 14, () -> applyColor(a, Colors.hsv((ui.mouseX - hx) * 360f / hw, 0.55f, 1f)), null, "Eigene Farbe");
		}
		int by = y + h - 24;
		boolean equipped = Wardrobe.get().equipped().contains(a.id());
		int bw = Math.min(110, (w - 24) / 2);
		ui.button(px, by, bw, 18, equipped ? "Ablegen" : "Anlegen", equipped ? Icon.CLOSE : Icon.HANGER, equipped ? Ui.Style.SECONDARY : Ui.Style.PRIMARY, true, () -> toggleEquip(a), null);
		boolean editable = a.packId.equals(de.behindtheblocks.skinstudio.pack.PackStore.CUSTOM_PACK_ID);
		ui.button(px + bw + 6, by, bw, 18, "Bearbeiten", Icon.EDIT, Ui.Style.SECONDARY, true,
				() -> minecraft.gui.setScreen(CosmeticEditorScreen.open(this, a.id())), editable ? null : "Eingebaute und installierte Cosmetics werden als eigene Kopie bearbeitet");
	}

	private void applyColor(CosmeticAsset a, int color) {
		pickColor = color == a.definition.tint.defaultColor ? 0 : color;
		if (Wardrobe.get().equipped().contains(a.id())) {
			Loadout l = Wardrobe.get().equipped().copy();
			l.setColor(a.id(), pickColor);
			Wardrobe.get().setEquipped(l);
		}
	}

	private void drawSettings(int x, int y) {
		Theme t = ui.t;
		ClientSettings s = ClientSettings.get();
		int w = 170, h = 128;
		ui.rounded(x, y, w, h, 9, t.dark ? 0xF21C1F27 : 0xFAFFFFFF);
		ui.roundedOutline(x, y, w, h, 9, t.glassBorder);
		ui.title("Einstellungen", x + 10, y + 9, t.text);
		int cy = y + 26;
		ui.toggle(x + 10, cy, w - 20, "Cosmetics anderer zeigen", s.showOtherPlayers, () -> { s.showOtherPlayers = !s.showOtherPlayers; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, "Animationen", s.animations, () -> { s.animations = !s.animations; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, "Partikel", s.particles, () -> { s.particles = !s.particles; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, "Lichtschein", s.glowEffects, () -> { s.glowEffects = !s.glowEffects; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, "Eigene Cosmetics teilen", s.shareOwnCosmetics, () -> { s.shareOwnCosmetics = !s.shareOwnCosmetics; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, "Fremde herunterladen", s.downloadOthers, () -> { s.downloadOthers = !s.downloadOthers; s.save(); });
		ui.region(x, y, w, h, () -> { });
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (search.isMouseOver(event.x(), event.y())) {
			return super.mouseClicked(event, doubleClick);
		}
		search.setFocused(false);
		if (ui.click(event.x(), event.y(), event.button())) {
			return true;
		}
		if (settingsOpen) {
			settingsOpen = false;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingPreview = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingPreview) {
			preview.drag(dx, dy);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (x >= gridX && x < gridX + gridW && y >= gridY && y < gridY + gridH) {
			gridScroll -= (int) (sy * 24);
			return true;
		}
		if (x >= sideX && x < sideX + sideW && y >= sideY && y < sideY + sideH) {
			sideScroll = Math.max(0, sideScroll - (int) (sy * 16));
			return true;
		}
		return super.mouseScrolled(x, y, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation() && selected() != null && !search.isFocused()) {
			toggleEquip(selected());
			return true;
		}
		if (!search.isFocused() && event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk()) {
			setFocused(search);
			search.setFocused(true);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		minecraft.gui.setScreen(new ImportScreen(this, files));
	}

	/** Used by the development AutoTest only. */
	public void autotest(String[] args) {
		switch (args[0]) {
			case "select" -> select(CosmeticLibrary.get().find(args[1]));
			case "equip" -> {
				CosmeticAsset a = CosmeticLibrary.get().find(args[1]);
				select(a);
				toggleEquip(a);
			}
			case "category" -> setFilter(Filter.CATEGORY, CosmeticCategory.byId(args[1]), null);
			case "search" -> search.setValue(args.length > 1 ? args[1] : "");
			case "settings" -> settingsOpen = !settingsOpen;
			case "yaw" -> preview.yaw = Float.parseFloat(args[1]);
			case "focus" -> preview.focus = PlayerPreview.Focus.valueOf(args[1].toUpperCase(Locale.ROOT));
			case "color" -> {
				CosmeticAsset a = selected();
				if (a != null) {
					applyColor(a, Colors.parse(args[1]));
				}
			}
			default -> {
			}
		}
	}
}
