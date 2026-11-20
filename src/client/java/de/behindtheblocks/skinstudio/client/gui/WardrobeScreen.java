package de.behindtheblocks.skinstudio.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
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
import de.behindtheblocks.skinstudio.client.skin.ClientSkins;
import de.behindtheblocks.skinstudio.client.skin.SkinLibrary;
import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticCategory;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.util.Colors;
import de.behindtheblocks.skinstudio.util.Tr;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The wardrobe with two areas:
 * <ul>
 *   <li><b>Cosmetics</b>: browse by category and sub-type, search, favorites, recent, outfits; try on by
 *       selecting, equip/unequip, recolor and personally adjust (move/scale) worn cosmetics.</li>
 *   <li><b>Skins</b>: Minecraft default skins, own skins (PNG import), Bedrock skin packs; try on and wear.</li>
 * </ul>
 * The 3D preview in the middle is the centerpiece and always shows the real render.
 */
public class WardrobeScreen extends Screen {
	private enum Tab {
		COSMETICS, SKINS
	}

	private enum Filter {
		ALL, FAVORITES, RECENT, EQUIPPED, CATEGORY, PACK
	}

	private final Screen parent;
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private EditBox search;
	private Tab tab = Tab.COSMETICS;
	private Filter filter = Filter.ALL;
	private CosmeticCategory category;
	private CosmeticType subType;
	private String packFilter;
	private String selectedId;
	private int pickColor;
	private boolean adjustMode;
	// skins
	private String skinGroup;
	private boolean skinFavorites;
	private String selectedSkin;
	private String confirmDeleteSkin;
	// layout & state
	private int gridScroll;
	private int sideScroll;
	private boolean draggingPreview;
	private boolean settingsOpen;
	private String toast;
	private long toastUntil;
	private boolean toastError;
	private int previewX0, previewY0, previewX1, previewY1;
	private int gridX, gridY, gridW, gridH;
	private int sideX, sideY, sideW, sideH;

	public WardrobeScreen(Screen parent) {
		super(Component.literal(Tr.t("btb.ui.wardrobe")));
		this.parent = parent;
	}

	/** Adds a "Wardrobe" button to the pause menu and the title screen. */
	public static void registerMenuButtons() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
				String label = Tr.t("btb.ui.wardrobe");
				int bw = client.font.width(label) + 20;
				Button b = Button.builder(Component.literal(label), btn -> client.gui.setScreen(new WardrobeScreen(screen)))
						.bounds(w - bw - 6, 6, bw, 20).tooltip(Tooltip.create(Component.literal(Tr.t("btb.ui.wardrobe.tooltip")))).build();
				Screens.getWidgets(screen).add(b);
			}
		});
	}

	@Override
	protected void init() {
		Tr.setLanguage(CosmeticLibrary.language());
		String old = search == null ? "" : search.getValue();
		search = new EditBox(font, 0, 0, 100, 12, Component.literal(Tr.t("btb.ui.search")));
		search.setBordered(false);
		search.setMaxLength(64);
		search.setHint(Component.literal(Tr.t("btb.ui.search.hint")));
		search.setValue(old);
		search.setResponder(s -> gridScroll = 0);
		addRenderableWidget(search);
		if (CosmeticLibrary.get().revision() == 0 && !CosmeticLibrary.get().isLoading()) {
			CosmeticLibrary.get().reload();
		}
		if (selectedSkin == null) {
			selectedSkin = Wardrobe.get().skin();
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

	private static String lang() {
		return CosmeticLibrary.language();
	}

	// ------------------------------------------------------------------ cosmetic data

	private List<CosmeticAsset> visibleCosmetics() {
		CosmeticLibrary lib = CosmeticLibrary.get();
		Wardrobe w = Wardrobe.get();
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (!q.isEmpty()) {
			List<CosmeticAsset> all = new ArrayList<>(lib.all());
			all.removeIf(a -> !matches(a.definition, q));
			all.sort(Comparator.comparing(a -> a.definition.name(lang()).toLowerCase(Locale.ROOT)));
			return all;
		}
		List<CosmeticAsset> list = new ArrayList<>();
		switch (filter) {
			case RECENT -> w.recent().stream().map(lib::find).filter(java.util.Objects::nonNull).forEach(list::add);
			case EQUIPPED -> w.equipped().entries().stream().map(e -> lib.find(e.id())).filter(java.util.Objects::nonNull).forEach(list::add);
			default -> {
				for (CosmeticAsset a : lib.all()) {
					if (filter == Filter.FAVORITES && !w.isFavorite(a.id())
							|| filter == Filter.CATEGORY && (a.definition.category() != category || subType != null && a.definition.type != subType)
							|| filter == Filter.PACK && !a.packId.equals(packFilter)) {
						continue;
					}
					list.add(a);
				}
				list.sort(Comparator.comparing((CosmeticAsset a) -> a.definition.category().ordinal())
						.thenComparing(a -> a.definition.type.ordinal())
						.thenComparing(a -> a.definition.name(lang()).toLowerCase(Locale.ROOT)));
			}
		}
		return list;
	}

	private static boolean matches(CosmeticDefinition d, String q) {
		if (d.id.contains(q) || typeLabel(d.type).toLowerCase(Locale.ROOT).contains(q) || categoryLabel(d.category()).toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}
		return d.names.values().stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(q))
				|| d.tags.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(q));
	}

	public static String categoryLabel(CosmeticCategory c) {
		return Tr.t("btb.category." + c.id());
	}

	public static String typeLabel(CosmeticType t) {
		return Tr.t("btb.type." + t.id());
	}

	public static String attachmentLabel(AttachmentPoint a) {
		return Tr.t("btb.attachment." + a.id());
	}

	private List<ActiveCosmetic> previewCosmetics() {
		Loadout l = Wardrobe.get().equipped().copy();
		CosmeticAsset sel = tab == Tab.COSMETICS ? selected() : null;
		if (sel != null) {
			if (!l.contains(sel.id())) {
				l.equip(sel.definition, pickColor, "", CosmeticLibrary.get()::definition);
			} else if (pickColor != 0) {
				l.setColor(sel.id(), pickColor);
			}
		}
		List<ActiveCosmetic> out = new ArrayList<>();
		if (tab == Tab.COSMETICS || selectedSkin == null) {
			out.addAll(ClientSkins.extrasFor(SkinLibrary.get().find(Wardrobe.get().skin())));
		}
		out.addAll(ClientCosmeticSync.resolve(l.entries(), false));
		return out;
	}

	private CosmeticAsset selected() {
		return selectedId == null ? null : CosmeticLibrary.get().find(selectedId);
	}

	private void select(CosmeticAsset a) {
		adjustMode = false;
		if (a == null) {
			selectedId = null;
			return;
		}
		selectedId = a.id();
		Loadout.Entry e = Wardrobe.get().equipped().find(a.id());
		pickColor = e == null ? 0 : e.color();
		preview.focus = focusFor(a.definition.attachment);
		float yaw = ((preview.yaw % 360) + 360) % 360;
		if (a.definition.attachment == AttachmentPoint.BACK || a.definition.category() == CosmeticCategory.BACK) {
			preview.yaw = 155;
		} else if (yaw > 90 && yaw < 270) {
			preview.yaw = -25;
		}
	}

	static PlayerPreview.Focus focusFor(AttachmentPoint ap) {
		return switch (ap) {
			case HEAD -> PlayerPreview.Focus.HEAD;
			case LEFT_LEG, RIGHT_LEG -> PlayerPreview.Focus.LEGS;
			case LEFT_SHOULDER, RIGHT_SHOULDER -> PlayerPreview.Focus.UPPER;
			default -> PlayerPreview.Focus.FULL;
		};
	}

	private void toggleEquip(CosmeticAsset a) {
		Wardrobe w = Wardrobe.get();
		Loadout l = w.equipped().copy();
		String name = a.definition.name(lang());
		if (l.contains(a.id())) {
			l.unequip(a.id());
			w.setEquipped(l);
			toast(Tr.t("btb.ui.toast.removed", name), false);
			return;
		}
		Loadout.EquipResult r = l.equip(a.definition, pickColor, a.contentHash, CosmeticLibrary.get()::definition);
		if (!r.equipped()) {
			toast(Tr.t("btb.ui.toast.max", Loadout.MAX_ENTRIES), true);
			return;
		}
		w.markUsed(a.id());
		w.setEquipped(l);
		if (r.removed().isEmpty()) {
			toast(Tr.t("btb.ui.toast.equipped", name), false);
		} else {
			List<String> names = new ArrayList<>();
			for (String id : r.removed()) {
				CosmeticDefinition d = CosmeticLibrary.get().definition(id);
				names.add(d == null ? id : d.name(lang()));
			}
			toast(Tr.t("btb.ui.toast.replaced", String.join(", ", names)), false);
		}
	}

	private void toast(String msg, boolean error) {
		toast = msg;
		toastError = error;
		toastUntil = System.currentTimeMillis() + 2800;
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
		int m = 8, top = 32, bottom = height - m;
		drawHeader(m);

		sideX = m;
		sideY = top;
		sideW = Math.max(100, Math.min(130, width / 6));
		sideH = bottom - top;
		int stageX = sideX + sideW + 6;
		int stageW = Math.max(140, Math.min(230, (int) (width * 0.30)));
		int rightX = stageX + stageW + 6;
		int rightW = width - m - rightX;

		if (tab == Tab.COSMETICS) {
			drawSidebar(sideX, sideY, sideW, sideH);
		} else {
			drawSkinSidebar(sideX, sideY, sideW, sideH);
		}
		drawStage(stageX, top, stageW, bottom - top);
		if (tab == Tab.COSMETICS) {
			drawBrowser(rightX, top, rightW, bottom - top);
		} else {
			drawSkinBrowser(rightX, top, rightW, bottom - top);
		}
		if (settingsOpen) {
			drawSettings(width - m - 190, 30);
		}
		if (toast != null && System.currentTimeMillis() < toastUntil) {
			String msg = ui.ellipsize(toast, stageW + rightW);
			int tw = font.width(msg) + 20;
			int tx = Math.max(m, stageX + (stageW - tw) / 2), ty = bottom - 22;
			ui.rounded(tx, ty, tw, 18, 9, toastError ? (t.danger & 0xFFFFFF) | 0xE0000000 : t.dark ? 0xEE303644 : 0xF4FFFFFF);
			ui.text(msg, tx + 10, ty + 5, toastError ? 0xFFFFFFFF : t.text);
		}
		if (CosmeticLibrary.get().isLoading()) {
			ui.textCentered(Tr.t("btb.ui.loading"), width / 2, height / 2, t.textMuted);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private void drawHeader(int m) {
		Theme t = ui.t;
		ui.title("BTB Skin Studio", m + 2, 12, t.text);
		// tabs
		int tx = m + 2 + font.width("BTB Skin Studio") + 16;
		for (Tab tb : Tab.values()) {
			String label = Tr.t(tb == Tab.COSMETICS ? "btb.ui.tab.cosmetics" : "btb.ui.tab.skins");
			int w = font.width(label) + 20;
			boolean act = tb == tab;
			boolean hov = ui.hover(tx, 7, w, 18);
			ui.rounded(tx, 7, w, 18, 9, act ? t.accent : hov ? t.surfaceHover : t.surface);
			ui.text(label, tx + 10, 12, act ? t.onAccent : t.text);
			ui.region(tx, 7, w, 18, () -> {
				tab = tb;
				gridScroll = 0;
				sideScroll = 0;
				search.setValue("");
			});
			tx += w + 4;
		}
		int bx = width - m - 18;
		ui.iconButton(bx, 7, 18, Icon.CLOSE, false, this::onClose, Tr.t("btb.ui.close"));
		bx -= 22;
		ui.iconButton(bx, 7, 18, Icon.GEAR, settingsOpen, () -> settingsOpen = !settingsOpen, Tr.t("btb.ui.settings"));
		bx -= 22;
		ui.iconButton(bx, 7, 18, t.dark ? Icon.SUN : Icon.MOON, false, () -> {
			ClientSettings s = ClientSettings.get();
			s.theme = s.theme == ClientSettings.Theme.DARK ? ClientSettings.Theme.LIGHT : ClientSettings.Theme.DARK;
			s.save();
		}, Tr.t(t.dark ? "btb.ui.theme.light" : "btb.ui.theme.dark"));
		String packs = Tr.t("btb.ui.packs"), editor = Tr.t("btb.ui.editor");
		int w1 = font.width(packs) + 28;
		bx -= w1 + 6;
		ui.button(bx, 7, w1, 18, packs, Icon.BOX, Ui.Style.SECONDARY, true, () -> minecraft.gui.setScreen(new PackManagerScreen(this)), Tr.t("btb.ui.packs.tooltip"));
		int w2 = font.width(editor) + 28;
		bx -= w2 + 4;
		ui.button(bx, 7, w2, 18, editor, Icon.EDIT, Ui.Style.SECONDARY, true, () -> minecraft.gui.setScreen(CosmeticEditorScreen.open(this, null)), Tr.t("btb.ui.editor.tooltip"));
	}

	private void navItem(int x, int y, int w, String label, int count, boolean active, Icon icon, Runnable action) {
		Theme t = ui.t;
		boolean hov = ui.hover(x, y, w, 15);
		if (active || hov) {
			ui.rounded(x, y, w, 15, 5, active ? (t.accent & 0xFFFFFF) | 0x38000000 : t.surfaceHover);
		}
		int tx = x + 6;
		if (icon != null) {
			icon.draw(ui.g, x + 5, y + 4, active ? t.accent : t.textMuted);
			tx = x + 16;
		}
		String c = count < 0 ? "" : String.valueOf(count);
		ui.text(ui.ellipsize(label, w - (tx - x) - font.width(c) - 9), tx, y + 4, active ? t.text : t.textMuted);
		if (!c.isEmpty()) {
			ui.text(c, x + w - 5 - font.width(c), y + 4, t.textFaint);
		}
		ui.region(x, y, w, 15, action);
	}

	private void sectionLabel(String key, int x, int y) {
		ui.text(Tr.t(key).toUpperCase(Locale.ROOT), x, y, ui.t.textFaint);
	}

	private void drawSidebar(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 8);
		CosmeticLibrary lib = CosmeticLibrary.get();
		Wardrobe wd = Wardrobe.get();
		ui.g.enableScissor(x + 1, y + 4, x + w - 1, y + h - 4);
		int cy = y + 6 - sideScroll, iw = w - 8, ix = x + 4;
		navItem(ix, cy, iw, Tr.t("btb.ui.filter.all"), lib.all().size(), filter == Filter.ALL, Icon.LAYERS, () -> setFilter(Filter.ALL, null, null));
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.filter.favorites"), wd.favorites().size(), filter == Filter.FAVORITES, Icon.STAR, () -> setFilter(Filter.FAVORITES, null, null));
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.filter.recent"), -1, filter == Filter.RECENT, Icon.CLOCK, () -> setFilter(Filter.RECENT, null, null));
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.filter.equipped"), wd.equipped().entries().size(), filter == Filter.EQUIPPED, Icon.HANGER, () -> setFilter(Filter.EQUIPPED, null, null));
		cy += 21;
		sectionLabel("btb.ui.section.categories", ix + 4, cy);
		cy += 11;
		for (CosmeticCategory c : CosmeticCategory.values()) {
			int count = (int) lib.all().stream().filter(a -> a.definition.category() == c).count();
			navItem(ix, cy, iw, categoryLabel(c), count, filter == Filter.CATEGORY && category == c, null, () -> setFilter(Filter.CATEGORY, c, null));
			cy += 16;
		}
		cy += 5;
		sectionLabel("btb.ui.section.packs", ix + 4, cy);
		cy += 11;
		for (LoadedPack p : lib.packs()) {
			if (p.cosmetics.isEmpty()) {
				continue;
			}
			String pid = p.id();
			navItem(ix, cy, iw, packName(p), p.cosmetics.size(), filter == Filter.PACK && pid.equals(packFilter), null, () -> setFilter(Filter.PACK, null, pid));
			cy += 16;
		}
		if (!wd.outfits().isEmpty()) {
			cy += 5;
			sectionLabel("btb.ui.section.outfits", ix + 4, cy);
			cy += 11;
			for (var e : new ArrayList<>(wd.outfits().entrySet())) {
				String name = e.getKey();
				boolean hov = ui.hover(ix, cy, iw, 15);
				if (hov) {
					ui.rounded(ix, cy, iw, 15, 5, t.surfaceHover);
				}
				Icon.HANGER.draw(ui.g, ix + 5, cy + 4, t.textMuted);
				ui.text(ui.ellipsize(name, iw - 32), ix + 16, cy + 4, t.textMuted);
				ui.region(ix, cy, iw - 14, 15, () -> {
					wd.setEquipped(e.getValue());
					toast(Tr.t("btb.ui.toast.outfit_applied", name), false);
				}, null, Tr.t("btb.ui.outfit.apply"));
				Icon.CLOSE.draw(ui.g, ix + iw - 11, cy + 4, ui.hover(ix + iw - 14, cy, 14, 15) ? t.danger : t.textFaint);
				ui.region(ix + iw - 14, cy, 14, 15, () -> wd.deleteOutfit(name), null, Tr.t("btb.ui.outfit.delete"));
				cy += 16;
			}
		}
		int contentH = cy + sideScroll - (y + 6);
		sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, contentH - h + 12)));
		ui.g.disableScissor();
	}

	static String packName(LoadedPack p) {
		if (p.id().equals(PackStore.CUSTOM_PACK_ID)) {
			return Tr.t("btb.ui.pack.custom");
		}
		if (p.id().startsWith("btb_builtin")) {
			return Tr.t("btb.ui.pack.starter");
		}
		return p.manifest.name(lang());
	}

	private void setFilter(Filter f, CosmeticCategory c, String pack) {
		filter = f;
		category = c;
		subType = null;
		packFilter = pack;
		gridScroll = 0;
		search.setValue("");
	}

	private void drawStage(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		List<Loadout.Entry> equipped = Wardrobe.get().equipped().entries();
		List<int[]> chipPos = new ArrayList<>();
		List<String> chipLabels = new ArrayList<>();
		int cx = x + 8, rows = equipped.isEmpty() ? 0 : 1;
		for (Loadout.Entry e : equipped) {
			CosmeticDefinition d = CosmeticLibrary.get().definition(e.id());
			String label = ui.ellipsize(d == null ? e.id() : d.name(lang()), w - 40);
			int cw = ui.chipWidth(label, true);
			if (cx + cw > x + w - 8) {
				cx = x + 8;
				rows++;
			}
			chipPos.add(new int[]{cx, rows - 1});
			chipLabels.add(label);
			cx += cw + 4;
		}
		rows = Math.min(rows, 3);
		int chipsH = rows * 17;
		int buttonsH = 24;
		previewX0 = x + 4;
		previewY0 = y + 4;
		previewX1 = x + w - 4;
		previewY1 = y + h - 8 - chipsH - buttonsH - 16;
		ui.rounded(previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, 8, t.stage);
		preview.skinOverride = tab == Tab.SKINS ? SkinLibrary.get().find(selectedSkin) : null;
		preview.draw(ui.g, previewX0, previewY0, previewX1, previewY1, previewCosmetics());
		ui.text(Tr.t("btb.ui.drag_to_rotate"), previewX0 + 6, previewY1 - 12, t.textFaint);
		int vx = previewX1 - 4;
		PlayerPreview.Focus[] order = {PlayerPreview.Focus.LEGS, PlayerPreview.Focus.UPPER, PlayerPreview.Focus.HEAD, PlayerPreview.Focus.FULL};
		for (PlayerPreview.Focus f : order) {
			String l = Tr.t("btb.ui.view." + f.name().toLowerCase(Locale.ROOT));
			int bw = font.width(l) + 10;
			vx -= bw;
			boolean act = preview.focus == f, hov = ui.hover(vx, previewY0 + 4, bw, 13);
			ui.rounded(vx, previewY0 + 4, bw, 13, 6, act ? t.accent : hov ? t.surfaceHover : t.surface);
			ui.text(l, vx + 5, previewY0 + 7, act ? t.onAccent : t.textMuted);
			ui.region(vx, previewY0 + 4, bw, 13, () -> preview.focus = f);
			vx -= 3;
		}
		ui.region(previewX0, previewY0 + 20, previewX1 - previewX0, previewY1 - previewY0 - 20, () -> draggingPreview = true);

		int chipsY = previewY1 + 5;
		ui.text(equipped.isEmpty() ? Tr.t("btb.ui.worn.none") : Tr.t("btb.ui.worn", equipped.size()), x + 8, chipsY, equipped.isEmpty() ? t.textFaint : t.textMuted);
		chipsY += 12;
		for (int i = 0; i < equipped.size(); i++) {
			int[] p = chipPos.get(i);
			if (p[1] >= rows) {
				ui.text("+" + (equipped.size() - i), x + w - 22, chipsY + (rows - 1) * 17 + 3, t.textFaint);
				break;
			}
			String id = equipped.get(i).id();
			ui.chip(p[0], chipsY + p[1] * 17, chipLabels.get(i), id.equals(selectedId) && tab == Tab.COSMETICS, () -> {
				tab = Tab.COSMETICS;
				select(CosmeticLibrary.get().find(id));
			}, () -> {
				Loadout l = Wardrobe.get().equipped().copy();
				l.unequip(id);
				Wardrobe.get().setEquipped(l);
			});
		}
		int by = y + h - 4 - buttonsH + 2;
		int half = (w - 20) / 2;
		ui.button(x + 8, by, half, 18, Tr.t("btb.ui.remove_all"), null, Ui.Style.SECONDARY, !equipped.isEmpty(), () -> {
			Wardrobe.get().setEquipped(new Loadout());
			toast(Tr.t("btb.ui.toast.all_removed"), false);
		}, null);
		ui.button(x + 12 + half, by, half, 18, Tr.t("btb.ui.save_outfit"), null, Ui.Style.SECONDARY, !equipped.isEmpty(), () -> {
			String name = Tr.t("btb.ui.outfit.name", Wardrobe.get().outfits().size() + 1);
			Wardrobe.get().saveOutfit(name, Wardrobe.get().equipped());
			toast(Tr.t("btb.ui.toast.outfit_saved", name), false);
		}, Tr.t("btb.ui.save_outfit.tooltip"));
	}

	private void searchField(int sx, int sy, int sw) {
		Theme t = ui.t;
		ui.rounded(sx, sy, sw, 18, 9, t.field);
		ui.roundedOutline(sx, sy, sw, 18, 9, search.isFocused() ? t.accent : t.glassBorder);
		Icon.SEARCH.draw(ui.g, sx + 7, sy + 5, t.textMuted);
		search.setX(sx + 19);
		search.setY(sy + 5);
		search.setWidth(sw - 26);
		search.setTextColor(t.text);
		search.visible = true;
	}

	private void drawBrowser(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		searchField(x + 8, y + 8, w - 16);
		int cy = y + 30;
		// sub-type chips inside a category
		if (filter == Filter.CATEGORY && category != null && search.getValue().isEmpty()) {
			Map<CosmeticType, Integer> counts = new LinkedHashMap<>();
			for (CosmeticAsset a : CosmeticLibrary.get().all()) {
				if (a.definition.category() == category) {
					counts.merge(a.definition.type, 1, Integer::sum);
				}
			}
			int cx = x + 8;
			String all = Tr.t("btb.ui.filter.all");
			ui.chip(cx, cy, all, subType == null, () -> {
				subType = null;
				gridScroll = 0;
			}, null);
			cx += ui.chipWidth(all, false) + 4;
			for (CosmeticType ty : CosmeticType.values()) {
				Integer n = counts.get(ty);
				if (n == null) {
					continue;
				}
				String label = typeLabel(ty);
				int cw = ui.chipWidth(label, false);
				if (cx + cw > x + w - 8) {
					cx = x + 8;
					cy += 17;
				}
				ui.chip(cx, cy, label, subType == ty, () -> {
					subType = ty;
					gridScroll = 0;
				}, null);
				cx += cw + 4;
			}
			cy += 19;
		}
		CosmeticAsset sel = selected();
		int detailH = sel == null ? 0 : (adjustMode ? 104 : sel.definition.tint.enabled ? 96 : 78);
		gridX = x + 8;
		gridY = cy;
		gridW = w - 16;
		gridH = y + h - 8 - gridY - (detailH > 0 ? detailH + 6 : 0);
		List<CosmeticAsset> list = visibleCosmetics();
		int cardW = 54, cardH = 64, gap = 5;
		int cols = Math.max(1, (gridW + gap) / (cardW + gap));
		cardW = (gridW - (cols - 1) * gap) / cols;
		int rows = (list.size() + cols - 1) / cols;
		int contentH = rows * (cardH + gap);
		gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, contentH - gridH)));
		ui.g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
		if (list.isEmpty()) {
			String msg = !search.getValue().isEmpty() ? Tr.t("btb.ui.empty.search", search.getValue()) : switch (filter) {
				case FAVORITES -> Tr.t("btb.ui.empty.favorites");
				case RECENT -> Tr.t("btb.ui.empty.recent");
				case EQUIPPED -> Tr.t("btb.ui.empty.equipped");
				default -> Tr.t("btb.ui.empty.default");
			};
			ui.textCentered(ui.ellipsize(msg, gridW - 10), gridX + gridW / 2, gridY + 30, t.textMuted);
		}
		Wardrobe wd = Wardrobe.get();
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols, row = i / cols;
			int cx = gridX + col * (cardW + gap), cyy = gridY + row * (cardH + gap) - gridScroll;
			if (cyy + cardH < gridY || cyy > gridY + gridH) {
				continue;
			}
			drawCard(list.get(i), cx, cyy, cardW, cardH, wd);
		}
		ui.g.disableScissor();
		scrollbar(x + w - 5, contentH);
		if (sel != null) {
			if (adjustMode && wd.equipped().contains(sel.id())) {
				drawAdjust(sel, x + 8, y + h - 8 - detailH, w - 16, detailH);
			} else {
				adjustMode = false;
				drawDetail(sel, x + 8, y + h - 8 - detailH, w - 16, detailH);
			}
		}
	}

	private void scrollbar(int x, int contentH) {
		if (contentH > gridH) {
			int barH = Math.max(14, gridH * gridH / contentH);
			int barY = gridY + (int) ((gridH - barH) * (gridScroll / (float) Math.max(1, contentH - gridH)));
			ui.rounded(x, barY, 3, barH, 1, ui.t.textFaint);
		}
	}

	private boolean inGrid() {
		return ui.mouseY >= gridY && ui.mouseY < gridY + gridH;
	}

	private void drawCard(CosmeticAsset a, int x, int y, int w, int h, Wardrobe wd) {
		Theme t = ui.t;
		boolean hov = ui.hover(x, y, w, h) && inGrid();
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
			ui.rounded(ix + 6, iy + 6, icon - 12, icon - 12, 6, t.stage);
		}
		ui.textCentered(ui.ellipsize(a.definition.name(lang()), w - 6), x + w / 2, y + h - 12, sel ? t.text : t.textMuted);
		if (wd.isFavorite(a.id())) {
			Icon.STAR_FILLED.draw(ui.g, x + 4, y + 4, t.accent);
		}
		if (equipped) {
			ui.rounded(x + w - 13, y + 3, 10, 10, 5, t.accent);
			Icon.CHECK.draw(ui.g, x + w - 12, y + 5, t.onAccent);
		}
		if (inGrid()) {
			ui.region(x, y, w, h, () -> {
				if (a.id().equals(selectedId)) {
					toggleEquip(a);
				} else {
					select(a);
				}
			}, () -> wd.toggleFavorite(a.id()), hov ? a.definition.name(lang()) : null);
		}
	}

	private void drawDetail(CosmeticAsset a, int x, int y, int w, int h) {
		Theme t = ui.t;
		CosmeticDefinition d = a.definition;
		ui.rounded(x, y, w, h, 8, t.surface);
		int px = x + 8, py = y + 7;
		ui.title(ui.ellipsize(d.name(lang()), w - 40), px, py, t.text);
		boolean fav = Wardrobe.get().isFavorite(a.id());
		ui.iconButton(x + w - 22, y + 4, 16, fav ? Icon.STAR_FILLED : Icon.STAR, fav, () -> Wardrobe.get().toggleFavorite(a.id()),
				Tr.t(fav ? "btb.ui.favorite.remove" : "btb.ui.favorite.add"));
		py += 11;
		LoadedPack pack = CosmeticLibrary.get().packs().stream().filter(p -> p.id().equals(a.packId)).findFirst().orElse(null);
		String meta = categoryLabel(d.category()) + " · " + typeLabel(d.type) + " · " + attachmentLabel(d.attachment) + (pack != null ? " · " + packName(pack) : "");
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
			int sy = y + h - 44;
			String colorLabel = Tr.t("btb.ui.color");
			ui.text(colorLabel, px, sy + 3, t.textMuted);
			int cx = px + font.width(colorLabel) + 8;
			List<Integer> pal = new ArrayList<>(d.tint.palette);
			if (pal.isEmpty()) {
				pal.add(0xFFFFFFFF);
			}
			for (int c : pal) {
				if (cx + 14 > x + w - 50) {
					break;
				}
				boolean act = (pickColor == 0 ? d.tint.defaultColor : pickColor) == c;
				ui.rounded(cx, sy, 14, 14, 7, act ? t.accent : t.glassBorder);
				ui.rounded(cx + 2, sy + 2, 10, 10, 5, c | 0xFF000000);
				ui.region(cx, sy, 14, 14, () -> applyColor(a, c), null, Colors.format(c));
				cx += 17;
			}
			int hx = cx + 4, hw = Math.max(30, x + w - 8 - hx);
			for (int i = 0; i < hw; i++) {
				ui.g.fill(hx + i, sy + 3, hx + i + 1, sy + 11, Colors.hsv(i * 360f / hw, 0.55f, 1f));
			}
			ui.roundedOutline(hx - 1, sy + 2, hw + 2, 10, 2, t.glassBorder);
			ui.region(hx, sy, hw, 14, () -> applyColor(a, Colors.hsv((ui.mouseX - hx) * 360f / hw, 0.55f, 1f)), null, Tr.t("btb.ui.color.custom"));
		}
		// actions: sized by their labels so nothing overflows
		int by = y + h - 24;
		boolean equipped = Wardrobe.get().equipped().contains(a.id());
		int bx = px;
		String l1 = Tr.t(equipped ? "btb.ui.unequip" : "btb.ui.equip");
		int w1 = font.width(l1) + 32;
		ui.button(bx, by, w1, 18, l1, equipped ? Icon.CLOSE : Icon.HANGER, equipped ? Ui.Style.SECONDARY : Ui.Style.PRIMARY, true, () -> toggleEquip(a), null);
		bx += w1 + 5;
		if (equipped) {
			String l2 = Tr.t("btb.ui.adjust");
			int w2 = font.width(l2) + 32;
			ui.button(bx, by, w2, 18, l2, Icon.LAYERS, Ui.Style.SECONDARY, true, () -> adjustMode = true, Tr.t("btb.ui.adjust.tooltip"));
			bx += w2 + 5;
		}
		String l3 = Tr.t("btb.ui.edit");
		int w3 = font.width(l3) + 32;
		if (bx + w3 <= x + w - 4) {
			boolean own = a.packId.equals(PackStore.CUSTOM_PACK_ID);
			ui.button(bx, by, w3, 18, l3, Icon.EDIT, Ui.Style.SECONDARY, true, () -> minecraft.gui.setScreen(CosmeticEditorScreen.open(this, a.id())),
					own ? null : Tr.t("btb.ui.edit.copy_tooltip"));
		}
	}

	/** Personal adjustment of a worn cosmetic: move and scale; synced to other players. */
	private void drawAdjust(CosmeticAsset a, int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.rounded(x, y, w, h, 8, t.surface);
		Loadout.Entry e = Wardrobe.get().equipped().find(a.id());
		Loadout.Adjust adj = e == null ? Loadout.Adjust.NONE : e.adjust();
		ui.title(ui.ellipsize(Tr.t("btb.ui.adjust.title", a.definition.name(lang())), w - 30), x + 8, y + 7, t.text);
		ui.iconButton(x + w - 22, y + 4, 16, Icon.BACK, false, () -> adjustMode = false, Tr.t("btb.ui.back"));
		int col = (w - 24) / 2;
		int sy = y + 22;
		ui.stepper(x + 8, sy, col, Tr.t("btb.ui.adjust.x"), adj.x(), 0.5f, -Loadout.Adjust.MAX_OFFSET, Loadout.Adjust.MAX_OFFSET, " px", v -> setAdjust(a, new Loadout.Adjust(v, adj.y(), adj.z(), adj.scale())));
		ui.stepper(x + 16 + col, sy, col, Tr.t("btb.ui.adjust.y"), adj.y(), 0.5f, -Loadout.Adjust.MAX_OFFSET, Loadout.Adjust.MAX_OFFSET, " px", v -> setAdjust(a, new Loadout.Adjust(adj.x(), v, adj.z(), adj.scale())));
		sy += 17;
		ui.stepper(x + 8, sy, col, Tr.t("btb.ui.adjust.z"), adj.z(), 0.5f, -Loadout.Adjust.MAX_OFFSET, Loadout.Adjust.MAX_OFFSET, " px", v -> setAdjust(a, new Loadout.Adjust(adj.x(), adj.y(), v, adj.scale())));
		ui.stepper(x + 16 + col, sy, col, Tr.t("btb.ui.adjust.scale"), adj.scale(), 0.05f, Loadout.Adjust.MIN_SCALE, Loadout.Adjust.MAX_SCALE, "×", v -> setAdjust(a, new Loadout.Adjust(adj.x(), adj.y(), adj.z(), v)));
		sy += 19;
		ui.text(ui.ellipsize(Tr.t("btb.ui.adjust.hint"), w - 16), x + 8, sy, t.textFaint);
		String reset = Tr.t("btb.ui.reset");
		int rw = font.width(reset) + 32;
		ui.button(x + 8, y + h - 24, rw, 18, reset, Icon.REFRESH, Ui.Style.SECONDARY, !adj.isNone(), () -> setAdjust(a, Loadout.Adjust.NONE), null);
	}

	private void setAdjust(CosmeticAsset a, Loadout.Adjust adj) {
		Loadout l = Wardrobe.get().equipped().copy();
		l.setAdjust(a.id(), adj);
		Wardrobe.get().setEquipped(l);
	}

	private void applyColor(CosmeticAsset a, int color) {
		pickColor = color == a.definition.tint.defaultColor ? 0 : color;
		if (Wardrobe.get().equipped().contains(a.id())) {
			Loadout l = Wardrobe.get().equipped().copy();
			l.setColor(a.id(), pickColor);
			Wardrobe.get().setEquipped(l);
		}
	}

	// ------------------------------------------------------------------ skins

	private List<SkinData> visibleSkins() {
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		List<SkinData> out = new ArrayList<>();
		for (SkinData s : SkinLibrary.get().all()) {
			if (!q.isEmpty()) {
				if (!s.name(lang()).toLowerCase(Locale.ROOT).contains(q) && !SkinLibrary.groupOf(s).toLowerCase(Locale.ROOT).contains(q)) {
					continue;
				}
			} else if (skinFavorites ? !Wardrobe.get().favoriteSkins().contains(s.id) : skinGroup != null && !SkinLibrary.groupOf(s).equals(skinGroup)) {
				continue;
			}
			out.add(s);
		}
		return out;
	}

	private void drawSkinSidebar(int x, int y, int w, int h) {
		ui.glass(x, y, w, h, 8);
		SkinLibrary lib = SkinLibrary.get();
		ui.g.enableScissor(x + 1, y + 4, x + w - 1, y + h - 4);
		int cy = y + 6 - sideScroll, iw = w - 8, ix = x + 4;
		navItem(ix, cy, iw, Tr.t("btb.ui.filter.all"), lib.all().size(), skinGroup == null && !skinFavorites, Icon.LAYERS, () -> setSkinGroup(null, false));
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.filter.favorites"), Wardrobe.get().favoriteSkins().size(), skinFavorites, Icon.STAR, () -> setSkinGroup(null, true));
		cy += 21;
		sectionLabel("btb.ui.section.skin_groups", ix + 4, cy);
		cy += 11;
		for (String gname : lib.groups()) {
			int count = (int) lib.all().stream().filter(s -> SkinLibrary.groupOf(s).equals(gname)).count();
			navItem(ix, cy, iw, gname, count, gname.equals(skinGroup) && !skinFavorites, null, () -> setSkinGroup(gname, false));
			cy += 16;
		}
		cy += 5;
		sectionLabel("btb.ui.section.add_skins", ix + 4, cy);
		cy += 11;
		navItem(ix, cy, iw, Tr.t("btb.ui.skin.import_png"), -1, false, Icon.IMPORT, this::importSkinPng);
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.skin.packs_folder"), -1, false, Icon.FOLDER, () -> com.mojang.blaze3d.Blaze3D.openPath(SkinLibrary.get().skinPacksDir));
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.bedrock.scan"), -1, false, Icon.SEARCH, () -> minecraft.gui.setScreen(new BedrockScanScreen(this)));
		cy += 16;
		navItem(ix, cy, iw, Tr.t("btb.ui.skin.reload"), -1, false, Icon.REFRESH, () -> SkinLibrary.get().reload().thenRun(() -> toast(Tr.t("btb.ui.toast.reloaded"), false)));
		cy += 16;
		int contentH = cy + sideScroll - (y + 6);
		sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, contentH - h + 12)));
		ui.g.disableScissor();
	}

	private void setSkinGroup(String g, boolean favorites) {
		skinGroup = g;
		skinFavorites = favorites;
		gridScroll = 0;
		search.setValue("");
	}

	private void drawSkinBrowser(int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.glass(x, y, w, h, 10);
		searchField(x + 8, y + 8, w - 16);
		SkinData sel = SkinLibrary.get().find(selectedSkin);
		int detailH = 74;
		gridX = x + 8;
		gridY = y + 30;
		gridW = w - 16;
		gridH = y + h - 8 - gridY - detailH - 6;
		List<SkinData> list = visibleSkins();
		int cardW = 46, cardH = 82, gap = 5;
		int cols = Math.max(1, (gridW + gap) / (cardW + gap));
		cardW = (gridW - (cols - 1) * gap) / cols;
		int rows = (list.size() + cols - 1) / cols;
		int contentH = rows * (cardH + gap);
		gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, contentH - gridH)));
		ui.g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
		if (list.isEmpty()) {
			ui.textCentered(ui.ellipsize(Tr.t(skinFavorites ? "btb.ui.empty.skin_favorites" : "btb.ui.empty.skins"), gridW - 10), gridX + gridW / 2, gridY + 30, t.textMuted);
		}
		String worn = Wardrobe.get().skin();
		for (int i = 0; i < list.size(); i++) {
			int col = i % cols, row = i / cols;
			int cx = gridX + col * (cardW + gap), cy = gridY + row * (cardH + gap) - gridScroll;
			if (cy + cardH < gridY || cy > gridY + gridH) {
				continue;
			}
			SkinData s = list.get(i);
			boolean selected = s.id.equals(selectedSkin), wearing = s.id.equals(worn);
			boolean hov = ui.hover(cx, cy, cardW, cardH) && inGrid();
			ui.rounded(cx, cy, cardW, cardH, 7, selected ? t.surfaceSelected : hov ? t.surfaceHover : t.surface);
			if (selected || wearing) {
				ui.roundedOutline(cx, cy, cardW, cardH, 7, selected ? t.accent : (t.accent & 0xFFFFFF) | 0x70000000);
			}
			Identifier icon = ClientSkins.icon(s);
			if (icon != null) {
				int iw = 24, ih = 48;
				ui.g.blit(RenderPipelines.GUI_TEXTURED, icon, cx + (cardW - iw) / 2, cy + 6, 0, 0, iw, ih, 64, 128, 64, 128);
			}
			ui.textCentered(ui.ellipsize(s.name(lang()), cardW - 6), cx + cardW / 2, cy + cardH - 22, selected ? t.text : t.textMuted);
			ui.textCentered(s.slim ? Tr.t("btb.ui.skin.slim_short") : Tr.t("btb.ui.skin.wide_short"), cx + cardW / 2, cy + cardH - 11, t.textFaint);
			if (wearing) {
				ui.rounded(cx + cardW - 13, cy + 3, 10, 10, 5, t.accent);
				Icon.CHECK.draw(ui.g, cx + cardW - 12, cy + 5, t.onAccent);
			}
			if (Wardrobe.get().favoriteSkins().contains(s.id)) {
				Icon.STAR_FILLED.draw(ui.g, cx + 4, cy + 4, t.accent);
			}
			if (inGrid()) {
				ui.region(cx, cy, cardW, cardH, () -> {
					if (s.id.equals(selectedSkin)) {
						wearSkin(s);
					} else {
						selectedSkin = s.id;
						confirmDeleteSkin = null;
					}
				}, () -> Wardrobe.get().toggleFavoriteSkin(s.id), hov ? s.name(lang()) : null);
			}
		}
		ui.g.disableScissor();
		scrollbar(x + w - 5, contentH);
		drawSkinDetail(sel, x + 8, y + h - 8 - detailH, w - 16, detailH);
	}

	private void drawSkinDetail(SkinData s, int x, int y, int w, int h) {
		Theme t = ui.t;
		ui.rounded(x, y, w, h, 8, t.surface);
		String worn = Wardrobe.get().skin();
		if (s == null) {
			ui.title(Tr.t("btb.ui.skin.account"), x + 8, y + 7, t.text);
			ui.text(ui.ellipsize(Tr.t("btb.ui.skin.account.desc"), w - 16), x + 8, y + 19, t.textMuted);
		} else {
			ui.title(ui.ellipsize(s.name(lang()), w - 40), x + 8, y + 7, t.text);
			boolean fav = Wardrobe.get().favoriteSkins().contains(s.id);
			ui.iconButton(x + w - 22, y + 4, 16, fav ? Icon.STAR_FILLED : Icon.STAR, fav, () -> Wardrobe.get().toggleFavoriteSkin(s.id),
					Tr.t(fav ? "btb.ui.favorite.remove" : "btb.ui.favorite.add"));
			String meta = SkinLibrary.groupOf(s) + " · " + Tr.t(s.slim ? "btb.ui.skin.slim" : "btb.ui.skin.wide")
					+ (s.accessories != null ? " · " + Tr.t("btb.ui.skin.has_accessories") : "");
			ui.text(ui.ellipsize(meta, w - 16), x + 8, y + 19, t.textMuted);
		}
		int by = y + h - 24, bx = x + 8;
		if (s != null) {
			boolean wearing = s.id.equals(worn);
			String l1 = Tr.t(wearing ? "btb.ui.skin.worn" : "btb.ui.skin.wear");
			int w1 = font.width(l1) + 32;
			ui.button(bx, by, w1, 18, l1, Icon.HANGER, Ui.Style.PRIMARY, !wearing, () -> wearSkin(s), null);
			bx += w1 + 5;
		}
		String l2 = Tr.t("btb.ui.skin.use_account");
		int w2 = font.width(l2) + 20;
		ui.button(bx, by, w2, 18, l2, null, Ui.Style.SECONDARY, !worn.isEmpty(), () -> {
			Wardrobe.get().setSkin("");
			selectedSkin = "";
			toast(Tr.t("btb.ui.toast.account_skin"), false);
		}, null);
		bx += w2 + 5;
		if (s != null && s.source == SkinData.Source.CUSTOM) {
			boolean confirm = s.id.equals(confirmDeleteSkin);
			String l3 = Tr.t(confirm ? "btb.ui.confirm_delete" : "btb.ui.delete");
			int w3 = font.width(l3) + 32;
			if (bx + w3 <= x + w - 4) {
				ui.button(bx, by, w3, 18, l3, Icon.TRASH, Ui.Style.DANGER, true, () -> {
					if (confirm) {
						deleteSkin(s);
					} else {
						confirmDeleteSkin = s.id;
					}
				}, null);
			}
		}
	}

	private void wearSkin(SkinData s) {
		Wardrobe.get().setSkin(s.id);
		selectedSkin = s.id;
		toast(Tr.t("btb.ui.toast.skin_worn", s.name(lang())), false);
	}

	private void deleteSkin(SkinData s) {
		CompletableFuture.runAsync(() -> {
			try {
				SkinLibrary.get().deleteCustom(s.id);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((v, err) -> {
			if (err != null) {
				toast(err.getMessage(), true);
				return;
			}
			if (s.id.equals(Wardrobe.get().skin())) {
				Wardrobe.get().setSkin("");
			}
			confirmDeleteSkin = null;
			selectedSkin = Wardrobe.get().skin();
			SkinLibrary.get().reload();
			toast(Tr.t("btb.ui.toast.deleted", s.name(lang())), false);
		}, Minecraft.getInstance());
	}

	private void importSkinPng() {
		FileDialogs.openFiles(Tr.t("btb.ui.skin.png_filter"), "png;btbskin;zip;mcpack", false, this::importSkinFiles);
	}

	/** PNG → own skin; .btbskin → own skin; skin pack (.zip/.mcpack with skins.json) → skin_packs folder. */
	private void importSkinFiles(List<Path> files) {
		if (files.isEmpty()) {
			toast(Tr.t("btb.ui.toast.no_dialog"), false);
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			int ok = 0;
			List<String> errors = new ArrayList<>();
			for (Path p : files) {
				String name = p.getFileName().toString();
				try {
					String lower = name.toLowerCase(Locale.ROOT);
					if (lower.endsWith(".png")) {
						SkinLibrary.get().saveCustom(SkinIO.fromPng(Files.readAllBytes(p), name, null));
						ok++;
					} else {
						var arch = de.behindtheblocks.skinstudio.pack.ArchiveFiles.read(p);
						if (SkinIO.isSkinArchive(arch)) {
							SkinData s = SkinIO.read(arch, SkinData.Source.CUSTOM, "");
							SkinLibrary.get().saveCustom(s);
							ok++;
						} else if (arch.unwrapSingleFolder().has("skins.json")) {
							Files.createDirectories(SkinLibrary.get().skinPacksDir);
							Files.copy(p, SkinLibrary.get().skinPacksDir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
							ok++;
						} else {
							errors.add(Tr.t("btb.err.not_a_skin", name));
						}
					}
				} catch (Exception e) {
					errors.add(name + ": " + e.getMessage());
				}
			}
			return new Object[]{ok, errors};
		}, CosmeticLibrary.IO).thenAcceptAsync(r -> {
			int ok = (int) r[0];
			@SuppressWarnings("unchecked") List<String> errors = (List<String>) r[1];
			SkinLibrary.get().reload();
			if (!errors.isEmpty()) {
				toast(errors.getFirst(), true);
			} else {
				toast(Tr.t("btb.ui.toast.skins_imported", ok), false);
			}
		}, Minecraft.getInstance());
	}

	// ------------------------------------------------------------------ settings

	private void drawSettings(int x, int y) {
		Theme t = ui.t;
		ClientSettings s = ClientSettings.get();
		int w = 190, h = 146;
		ui.rounded(x, y, w, h, 9, t.dark ? 0xF41C1F27 : 0xFCFFFFFF);
		ui.roundedOutline(x, y, w, h, 9, t.glassBorder);
		ui.title(Tr.t("btb.ui.settings"), x + 10, y + 9, t.text);
		int cy = y + 26;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.show_others"), s.showOtherPlayers, () -> { s.showOtherPlayers = !s.showOtherPlayers; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.layers3d"), s.skinLayers3d, () -> { s.skinLayers3d = !s.skinLayers3d; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.animations"), s.animations, () -> { s.animations = !s.animations; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.particles"), s.particles, () -> { s.particles = !s.particles; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.glow"), s.glowEffects, () -> { s.glowEffects = !s.glowEffects; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.share"), s.shareOwnCosmetics, () -> { s.shareOwnCosmetics = !s.shareOwnCosmetics; s.save(); });
		cy += 17;
		ui.toggle(x + 10, cy, w - 20, Tr.t("btb.ui.setting.download"), s.downloadOthers, () -> { s.downloadOthers = !s.downloadOthers; s.save(); });
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
		if (ui.scroll(x, y, sy)) {
			return true;
		}
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
		if (event.isConfirmation() && !search.isFocused()) {
			if (tab == Tab.COSMETICS && selected() != null) {
				toggleEquip(selected());
				return true;
			}
			SkinData s = SkinLibrary.get().find(selectedSkin);
			if (tab == Tab.SKINS && s != null) {
				wearSkin(s);
				return true;
			}
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
		if (tab == Tab.SKINS) {
			importSkinFiles(files);
		} else {
			minecraft.gui.setScreen(new ImportScreen(this, files));
		}
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
			case "subtype" -> subType = CosmeticType.byId(args[1]);
			case "search" -> search.setValue(args.length > 1 ? args[1] : "");
			case "settings" -> settingsOpen = !settingsOpen;
			case "yaw" -> preview.yaw = Float.parseFloat(args[1]);
			case "focus" -> preview.focus = PlayerPreview.Focus.valueOf(args[1].toUpperCase(Locale.ROOT));
			case "tab" -> tab = Tab.valueOf(args[1].toUpperCase(Locale.ROOT));
			case "adjust" -> adjustMode = true;
			case "adjustset" -> {
				CosmeticAsset a = selected();
				if (a != null) {
					setAdjust(a, new Loadout.Adjust(Float.parseFloat(args[1]), Float.parseFloat(args[2]), Float.parseFloat(args[3]), Float.parseFloat(args[4])));
				}
			}
			case "skin" -> {
				selectedSkin = args[1];
				SkinData s = SkinLibrary.get().find(args[1]);
				if (s != null && args.length > 2 && args[2].equals("wear")) {
					wearSkin(s);
				}
			}
			case "skingroup" -> setSkinGroup(args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null, false);
			case "importskin" -> importSkinFiles(java.util.Arrays.stream(args, 1, args.length).map(Path::of).toList());
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
