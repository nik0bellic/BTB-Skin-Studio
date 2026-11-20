package de.behindtheblocks.skinstudio.client.editor;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.anim.AnimationIO;
import de.behindtheblocks.skinstudio.anim.AnimationPresets;
import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.bedrock.BedrockImporter;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.gui.FileDialogs;
import de.behindtheblocks.skinstudio.client.gui.PlayerPreview;
import de.behindtheblocks.skinstudio.client.gui.ui.Icon;
import de.behindtheblocks.skinstudio.client.gui.ui.Theme;
import de.behindtheblocks.skinstudio.client.gui.ui.Ui;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticJson;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackReader;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.pack.PackWriter;
import de.behindtheblocks.skinstudio.pack.Png;
import de.behindtheblocks.skinstudio.util.Colors;
import de.behindtheblocks.skinstudio.util.FormatException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Cosmetic editor: import model and texture, place the cosmetic on a body part (offset, rotation,
 * scale, pivot), add animations, set colors and effects – everything previewed live on the player.
 * Saves into "Meine Cosmetics" and exports as {@code .btbcosmetic}.
 */
public class CosmeticEditorScreen extends Screen {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Editor");
	private static final String[] PARTICLES = {"minecraft:small_flame", "minecraft:flame", "minecraft:soul_fire_flame", "minecraft:end_rod",
			"minecraft:firefly", "minecraft:cherry_leaves", "minecraft:happy_villager", "minecraft:glow", "minecraft:snowflake",
			"minecraft:heart", "minecraft:note", "minecraft:electric_spark", "minecraft:wax_on", "minecraft:smoke"};
	private static final int[] QUICK_COLORS = {0xFFFFFFFF, 0xFFFFB347, 0xFFFF6A5A, 0xFFFFE066, 0xFF7CFFB0, 0xFF7FD8FF, 0xFFB08CFF, 0xFFFF8FD0, 0xFF303030};

	private enum Tab {
		GENERAL("btb.t.allgemein"), MODEL("btb.t.modell"), COLOR("btb.t.farbe"), ANIMATION("btb.t.animation"), EFFECTS("btb.t.effekte");

		private final String labelKey;

		String label() {
			return Tr.t(labelKey);
		}

		Tab(String label) {
			this.labelKey = label;
		}
	}

	private final Screen parent;
	private final Ui ui = new Ui();
	private final PlayerPreview preview = new PlayerPreview();
	private Tab tab = Tab.GENERAL;

	// working copy
	private CosmeticDefinition def;
	private GeoModel model;
	private final Map<String, CosmeticAnimation> animations = new LinkedHashMap<>();
	private final ArchiveFiles files = ArchiveFiles.empty();
	private final String originalId;

	private CosmeticAsset built;
	private ActiveCosmetic active;
	private boolean dirty = true;
	private boolean unsaved;
	private String error;
	private final List<PackProblem> warnings = new ArrayList<>();
	private String status;
	private boolean withOutfit;
	private boolean dragging;
	private int previewColor;
	private AttachmentPoint lastAttachment;

	private EditBox nameBox, idBox, descBox;
	private AnimationPresets.Preset preset = AnimationPresets.Preset.BOB;
	private int presetBone;
	private float presetSpeed = 1;
	private float presetAmp = 1.5f;

	private CosmeticEditorScreen(Screen parent, CosmeticDefinition def, GeoModel model, Map<String, CosmeticAnimation> anims, ArchiveFiles extra, String originalId) {
		super(Component.literal(Tr.t("btb.t.cosmetic_editor")));
		this.parent = parent;
		this.def = def;
		this.model = model;
		this.animations.putAll(anims);
		for (String p : extra.paths()) {
			files.put(p, extra.get(p));
		}
		this.originalId = originalId;
	}

	/** Opens a copy of an existing cosmetic (own ones are edited in place) or a new blank one. */
	public static Screen open(Screen parent, String id) {
		CosmeticAsset src = id == null ? null : CosmeticLibrary.get().find(id);
		if (src == null) {
			return blank(parent);
		}
		CosmeticDefinition d = src.definition.copy();
		ArchiveFiles extra = ArchiveFiles.empty();
		for (String p : src.files.paths()) {
			String l = p.toLowerCase(Locale.ROOT);
			if (l.endsWith(".png")) {
				extra.put(p, src.files.get(p));
			}
		}
		String original = null;
		if (src.packId.equals(PackStore.CUSTOM_PACK_ID)) {
			original = src.id();
		} else {
			d.id = "custom:" + BedrockImporter.sanitize(d.path().replace('/', '_')) + "_copy";
			d.names.replaceAll((k, v) -> v + (k.equals("de") ? " (Kopie)" : " (Copy)"));
			d.author = Minecraft.getInstance().getUser().getName();
		}
		return new CosmeticEditorScreen(parent, d, src.model.copy(), src.animations, extra, original);
	}

	private static Screen blank(Screen parent) {
		CosmeticDefinition d = new CosmeticDefinition();
		d.id = "custom:mein_cosmetic_" + Long.toString(System.currentTimeMillis() % 1_000_000, 36);
		d.names.put("de", "Mein Cosmetic");
		d.names.put("en", "My Cosmetic");
		d.author = Minecraft.getInstance().getUser().getName();
		d.type = CosmeticType.HAT;
		d.attachment = AttachmentPoint.HEAD;
		GeoModel m = new GeoModel("geometry.custom.cosmetic", 32, 32);
		GeoBone b = new GeoBone("cosmetic");
		b.pivot = new float[]{0, 32, 0};
		b.cubes.add(GeoCube.box(-3, 32, -3, 6, 3, 6, 0, 0));
		b.cubes.add(GeoCube.box(-1, 35, -1, 2, 2, 2, 0, 9));
		m.bones.add(b);
		ArchiveFiles extra = ArchiveFiles.empty();
		extra.put("texture.png", de.behindtheblocks.skinstudio.bedrock.Images.png(starterTexture()));
		return new CosmeticEditorScreen(parent, d, m, Map.of(), extra, null);
	}

	/** Neutral starter texture so a new cosmetic is visible right away. */
	private static BufferedImage starterTexture() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		java.util.Random r = new java.util.Random(7);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int v = 200 + r.nextInt(30);
				boolean edge = (y == 0 || y == 8 || y == 9 || y == 13 || x % 6 == 0);
				int c = edge ? v - 40 : v;
				img.setRGB(x, y, 0xFF000000 | (c << 16) | ((c - 10) << 8) | (c - 30));
			}
		}
		return img;
	}

	// ------------------------------------------------------------------ lifecycle

	@Override
	protected void init() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.fit(this);
		// name and description are edited in the game language; the other language is only filled if empty
		String lang = Tr.german() ? "de" : "en", other = Tr.german() ? "en" : "de";
		String n = nameBox == null ? def.name(lang) : nameBox.getValue();
		String i = idBox == null ? def.id : idBox.getValue();
		String ds = descBox == null ? def.description(lang) : descBox.getValue();
		nameBox = box(n, 48, s -> {
			def.names.put(lang, s);
			if (!def.names.containsKey(other) || def.names.get(other).isBlank()) {
				def.names.put(other, s);
			}
			changed();
		});
		idBox = box(i, 120, s -> {
			def.id = s.trim().toLowerCase(Locale.ROOT);
			changed();
		});
		descBox = box(ds, 200, s -> {
			def.descriptions.put(Tr.german() ? "de" : "en", s);
			changed();
		});
	}

	private EditBox box(String value, int max, Consumer<String> onChange) {
		EditBox b = new EditBox(font, 0, 0, 100, 12, Component.empty());
		b.setBordered(false);
		b.setMaxLength(max);
		b.setValue(value);
		b.moveCursorToStart(false); // show the beginning of long texts, not the end
		b.setResponder(onChange);
		addRenderableWidget(b);
		return b;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void changed() {
		dirty = true;
		unsaved = true;
	}

	private void rebuild() {
		dirty = false;
		warnings.clear();
		try {
			if (!CosmeticJson.isValidId(def.id)) {
				throw new FormatException(Tr.t("btb.t.id_muss_die_form_namespace_name_haben_nu"));
			}
			CosmeticDefinition d = def.copy();
			ArchiveFiles out = PackWriter.cosmeticFiles(d, model, animations, files);
			built = PackReader.readCosmetic(out, PackStore.CUSTOM_PACK_ID, "", warnings);
			active = ActiveCosmetic.transient_(built, previewColor);
			error = null;
		} catch (FormatException e) {
			error = e.getMessage();
		} catch (RuntimeException e) {
			error = Tr.t("btb.t.fehler") + e.getMessage();
			LOG.warn("Editor-Vorschau fehlgeschlagen", e);
		}
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(0, 0, width, height, Theme.current().scrim);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (dirty) {
			rebuild();
		}
		ui.begin(g, font, mouseX, mouseY);
		Theme t = ui.t;
		int m = 8, top = 30, bottom = height - m;
		ui.iconButton(m, 6, 18, Icon.BACK, false, this::onClose, unsaved ? Tr.t("btb.t.zurueck_ungespeicherte_aenderungen_gehen") : Tr.t("btb.t.zurueck"));
		ui.title(Tr.t("btb.t.cosmetic_editor"), m + 24, 11, t.text);
		ui.text(ui.ellipsize(def.name(Tr.german() ? "de" : "en") + (unsaved ? " •" : ""), 200), m + 24 + ui.titleWidth(Tr.t("btb.t.cosmetic_editor")) + 10, 11, t.textMuted);

		int panelW = Math.max(190, Math.min(250, width * 2 / 5));
		ui.glass(m, top, panelW, bottom - top, 8);
		// tabs
		int tx = m + 6;
		int textSum = 0;
		for (Tab tb : Tab.values()) {
			textSum += font.width(tb.label());
		}
		// padding shrinks so that all tabs fit into the panel (German labels are longer)
		int pad = Math.max(4, Math.min(12, (panelW - 12 - textSum - 2 * (Tab.values().length - 1)) / Tab.values().length));
		for (Tab tb : Tab.values()) {
			int w = font.width(tb.label()) + pad;
			boolean act = tb == tab;
			boolean hov = ui.hover(tx, top + 6, w, 16);
			ui.rounded(tx, top + 6, w, 16, 8, act ? t.accent : hov ? t.surfaceHover : 0);
			ui.text(tb.label(), tx + pad / 2, top + 10, act ? t.onAccent : t.textMuted);
			ui.region(tx, top + 6, w, 16, () -> tab = tb);
			tx += w + 2;
		}
		int cx = m + 10, cw = panelW - 20, cy = top + 30;
		nameBox.visible = idBox.visible = descBox.visible = tab == Tab.GENERAL;
		switch (tab) {
			case GENERAL -> drawGeneral(cx, cy, cw);
			case MODEL -> drawModel(cx, cy, cw);
			case COLOR -> drawColor(cx, cy, cw);
			case ANIMATION -> drawAnimation(cx, cy, cw);
			case EFFECTS -> drawEffects(cx, cy, cw);
		}

		// preview stage
		int sx = m + panelW + 6, sw = width - m - sx;
		ui.glass(sx, top, sw, bottom - top, 10);
		int py1 = bottom - 40;
		ui.rounded(sx + 4, top + 4, sw - 8, py1 - top - 4, 8, t.stage);
		List<ActiveCosmetic> list = new ArrayList<>();
		if (withOutfit) {
			for (ActiveCosmetic c : de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync.resolve(Wardrobe.get().equipped().entries(), false)) {
				if (!c.asset().id().equals(def.id) && !c.asset().id().equals(originalId)) {
					list.add(c);
				}
			}
		}
		if (active != null) {
			list.add(active);
		}
		if (def.attachment != lastAttachment) {
			lastAttachment = def.attachment;
			preview.yaw = def.attachment == AttachmentPoint.BACK ? 155 : -25;
		}
		preview.focus = switch (def.attachment.part) {
			case HEAD -> PlayerPreview.Focus.HEAD;
			case LEFT_LEG, RIGHT_LEG -> PlayerPreview.Focus.LEGS;
			default -> def.attachment == AttachmentPoint.LEFT_SHOULDER || def.attachment == AttachmentPoint.RIGHT_SHOULDER ? PlayerPreview.Focus.UPPER : PlayerPreview.Focus.FULL;
		};
		preview.draw(g, sx + 4, top + 4, sx + sw - 4, py1, list);
		ui.region(sx + 4, top + 22, sw - 8, py1 - top - 22, () -> dragging = true);
		ui.toggle(sx + 10, top + 10, 112, Tr.t("btb.t.mit_meinem_outfit"), withOutfit, () -> withOutfit = !withOutfit);
		ui.hint(Tr.t("btb.t.ziehen_zum_drehen_live_vorschau"), sx, sx + sw, py1 - 17);
		if (error != null) {
			ui.rounded(sx + 10, top + 28, sw - 20, 28, 6, (t.danger & 0xFFFFFF) | 0xD0000000);
			var lines = font.split(Component.literal(error), sw - 32);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				g.text(font, lines.get(i), sx + 16, top + 32 + i * 10, 0xFFFFFFFF, false);
			}
		} else if (!warnings.isEmpty()) {
			ui.text(ui.ellipsize("⚠ " + warnings.getFirst().message(), sw - 20), sx + 10, top + 28, t.accent);
		}

		// bottom actions
		int by = bottom - 30;
		// button widths follow their labels, scaled down together when space is short
		String[] labels = {Tr.t("btb.t.speichern"), Tr.t("btb.t.speichern_anlegen"), Tr.t("btb.t.exportieren")};
		int[] bws = new int[3];
		int need = 0;
		for (int k = 0; k < 3; k++) {
			bws[k] = Math.max(70, font.width(labels[k]) + 34);
			need += bws[k];
		}
		float scale = Math.min(1f, (sw - 24f) / need);
		int bx = sx + 8;
		for (int k = 0; k < 3; k++) {
			bws[k] = (int) (bws[k] * scale);
		}
		ui.button(bx, by, bws[0], 20, labels[0], Icon.SAVE, Ui.Style.PRIMARY, error == null, () -> save(false), Tr.t("btb.t.in_meine_cosmetics_speichern"));
		bx += bws[0] + 4;
		ui.button(bx, by, bws[1], 20, labels[1], Icon.HANGER, Ui.Style.SECONDARY, error == null, () -> save(true), null);
		bx += bws[1] + 4;
		ui.button(bx, by, bws[2], 20, labels[2], Icon.EXPORT, Ui.Style.SECONDARY, error == null, this::export, Tr.t("btb.t.als_btbcosmetic_in_den_exports_ordner"));
		if (status != null) {
			ui.text(ui.ellipsize(status, sw - 20), sx + 10, by - 12, t.textMuted);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		ui.end();
	}

	private void field(EditBox box, int x, int y, int w, String label) {
		Theme t = ui.t;
		ui.text(label, x + 2, y, t.textMuted);
		ui.rounded(x, y + 10, w, 16, 7, t.field);
		ui.roundedOutline(x, y + 10, w, 16, 7, box.isFocused() ? t.accent : t.glassBorder);
		box.setX(x + 6);
		box.setY(y + 14);
		box.setWidth(w - 12);
		box.setTextColor(t.text);
	}

	private void drawGeneral(int x, int y, int w) {
		Theme t = ui.t;
		field(nameBox, x, y, w, Tr.t("btb.t.name"));
		y += 32;
		field(idBox, x, y, w, Tr.t("btb.t.id_namespace_name"));
		y += 32;
		field(descBox, x, y, w, Tr.t("btb.t.beschreibung"));
		y += 34;
		CosmeticType[] types = CosmeticType.values();
		ui.cycle(x, y, w, Tr.t("btb.t.typ"), de.behindtheblocks.skinstudio.client.gui.WardrobeScreen.categoryLabel(def.type.category) + " › " + de.behindtheblocks.skinstudio.client.gui.WardrobeScreen.typeLabel(def.type), () -> {
			def.type = types[(def.type.ordinal() + 1) % types.length];
			def.slots.clear();
			changed();
		}, () -> {
			def.type = types[(def.type.ordinal() + types.length - 1) % types.length];
			def.slots.clear();
			changed();
		});
		y += 18;
		AttachmentPoint[] aps = AttachmentPoint.values();
		ui.cycle(x, y, w, Tr.t("btb.t.befestigung"), de.behindtheblocks.skinstudio.client.gui.WardrobeScreen.attachmentLabel(def.attachment), () -> {
			def.attachment = aps[(def.attachment.ordinal() + 1) % aps.length];
			changed();
		}, () -> {
			def.attachment = aps[(def.attachment.ordinal() + aps.length - 1) % aps.length];
			changed();
		});
		y += 22;
		ui.text(Tr.t("btb.t.belegt") + String.join(", ", def.effectiveSlots().stream().map(sl -> Tr.t("btb.slot." + sl.id())).toList()), x + 2, y, t.textFaint);
		y += 16;
		ui.text(Tr.t("btb.t.hautebenen_ausblenden"), x + 2, y, t.textMuted);
		y += 12;
		for (String layer : new String[]{"hat", "jacket", "sleeves", "pants"}) {
			String label = switch (layer) {
				case "hat" -> Tr.t("btb.ui.layer.hat");
				case "jacket" -> Tr.t("btb.ui.layer.jacket");
				case "sleeves" -> Tr.t("btb.ui.layer.sleeves");
				default -> Tr.t("btb.ui.layer.pants");
			};
			ui.toggle(x, y, w, label, def.hideLayers.contains(layer), () -> {
				if (!def.hideLayers.remove(layer)) {
					def.hideLayers.add(layer);
				}
				changed();
			});
			y += 16;
		}
	}

	private void drawModel(int x, int y, int w) {
		Theme t = ui.t;
		int half = (w - 4) / 2;
		ui.button(x, y, half, 18, Tr.t("btb.t.modell_importieren"), Icon.IMPORT, Ui.Style.PRIMARY, true,
				() -> pick(Tr.t("btb.t.bedrock_blockbench_modell"), "json", this::loadFile), Tr.t("btb.t.geo_json_aus_blockbench_bedrock_modell_o"));
		ui.button(x + half + 4, y, half, 18, Tr.t("btb.t.textur_tauschen"), Icon.PALETTE, Ui.Style.SECONDARY, true,
				() -> pick(Tr.t("btb.t.png_textur"), "png", this::loadFile), Tr.t("btb.t.png_passend_zum_modell"));
		y += 22;
		boolean hasEmissive = def.emissiveTexture != null;
		ui.button(x, y, half, 18, hasEmissive ? Tr.t("btb.t.leuchttextur_ersetzen") : Tr.t("btb.t.leuchttextur"), Icon.SUN, Ui.Style.SECONDARY, true,
				() -> pick(Tr.t("btb.t.leuchtende_bereiche_png"), "png", p -> loadTexture(p, true)), Tr.t("btb.t.bereiche_die_im_dunkeln_leuchten"));
		ui.button(x + half + 4, y, half, 18, Tr.t("btb.t.leuchten_entfernen"), Icon.TRASH, Ui.Style.GHOST, hasEmissive, () -> {
			def.emissiveTexture = null;
			changed();
		}, null);
		y += 24;
		ui.text(model.bones.size() + Tr.t("btb.t.bones") + model.cubeCount() + Tr.t("btb.t.wuerfel_textur") + model.textureWidth + "×" + model.textureHeight, x + 2, y, t.textFaint);
		y += 14;
		CosmeticDefinition.Transform tr = def.transform;
		ui.text(Tr.t("btb.t.position_pixel"), x + 2, y, t.textFaint);
		y += 11;
		String[] ax = {"X", "Y", "Z"};
		for (int i = 0; i < 3; i++) {
			int k = i;
			ui.stepper(x, y, w, Tr.t("btb.t.verschieben") + ax[i], tr.offset[i], 0.5f, -64, 64, " px", v -> {
				tr.offset[k] = v;
				changed();
			});
			y += 15;
		}
		ui.text("DREHUNG", x + 2, y + 2, t.textFaint);
		y += 13;
		for (int i = 0; i < 3; i++) {
			int k = i;
			ui.stepper(x, y, w, Tr.t("btb.t.drehen") + ax[i], tr.rotation[i], 5, -180, 180, "°", v -> {
				tr.rotation[k] = v;
				changed();
			});
			y += 15;
		}
		ui.stepper(x, y, w, Tr.t("btb.t.groesse"), tr.scale[0], 0.05f, 0.1f, 8, "×", v -> {
			tr.scale[0] = tr.scale[1] = tr.scale[2] = v;
			changed();
		});
		y += 17;
		ui.text(Tr.t("btb.t.drehpunkt_relativ_zum_befestigungspunkt"), x + 2, y, t.textFaint);
		y += 11;
		for (int i = 0; i < 3; i++) {
			int k = i;
			ui.stepper(x, y, w, Tr.t("btb.t.pivot") + ax[i], tr.pivot[i], 0.5f, -32, 32, " px", v -> {
				tr.pivot[k] = v;
				changed();
			});
			y += 15;
		}
		y += 3;
		ui.button(x, y, half, 16, Tr.t("btb.t.am_punkt_ausrichten"), Icon.LAYERS, Ui.Style.SECONDARY, true, this::alignToAnchor,
				Tr.t("btb.t.setzt_das_modell_auf_den_befestigungspun"));
		ui.button(x + half + 4, y, half, 16, Tr.t("btb.t.zuruecksetzen"), Icon.REFRESH, Ui.Style.GHOST, true, () -> {
			tr.copyFrom(new CosmeticDefinition.Transform());
			changed();
		}, null);
	}

	private void drawColor(int x, int y, int w) {
		Theme t = ui.t;
		ui.toggle(x, y, w, Tr.t("btb.t.einfaerbbar"), def.tint.enabled, () -> {
			def.tint.enabled = !def.tint.enabled;
			changed();
		});
		y += 18;
		ui.text(def.tintMask != null ? Tr.t("btb.t.nur_markierte_bereiche_faerbemaske_werde") : Tr.t("btb.t.die_ganze_textur_wird_gefaerbt"), x + 2, y, t.textFaint);
		y += 12;
		int half = (w - 4) / 2;
		ui.button(x, y, half, 16, Tr.t("btb.t.faerbemaske_laden"), Icon.PALETTE, Ui.Style.SECONDARY, true, () -> pick(Tr.t("btb.t.faerbemaske_png"), "png", p -> loadMask(p)),
				Tr.t("btb.t.png_deckende_pixel_werden_eingefaerbt_tr"));
		ui.button(x + half + 4, y, half, 16, Tr.t("btb.t.maske_entfernen"), Icon.TRASH, Ui.Style.GHOST, def.tintMask != null, () -> {
			def.tintMask = null;
			changed();
		}, null);
		y += 24;
		int color = previewColor != 0 ? previewColor : def.tint.defaultColor;
		ui.text(Tr.t("btb.t.farbe"), x + 2, y, t.textMuted);
		ui.rounded(x + w - 40, y - 3, 40, 14, 6, color | 0xFF000000);
		ui.text(Colors.format(color), x + w - 92, y, t.textFaint);
		y += 16;
		float[] hsv = Colors.toHsv(color);
		ui.slider(x, y, w, Tr.t("btb.t.farbton"), hsv[0] / 360f, Colors.hsv(hsv[0], 0.7f, 1), v -> setPreviewColor(Colors.hsv(v * 360, hsv[1], hsv[2])));
		y += 16;
		ui.slider(x, y, w, Tr.t("btb.t.saettigung"), hsv[1], t.accent, v -> setPreviewColor(Colors.hsv(hsv[0], v, hsv[2])));
		y += 16;
		ui.slider(x, y, w, Tr.t("btb.t.helligkeit"), hsv[2], t.textMuted, v -> setPreviewColor(Colors.hsv(hsv[0], hsv[1], Math.max(0.05f, v))));
		y += 20;
		int bx = x;
		for (int c : QUICK_COLORS) {
			ui.rounded(bx, y, 14, 14, 7, t.glassBorder);
			ui.rounded(bx + 2, y + 2, 10, 10, 5, c);
			ui.region(bx, y, 14, 14, () -> setPreviewColor(c));
			bx += 17;
		}
		y += 22;
		ui.button(x, y, half, 16, Tr.t("btb.t.als_standard"), Icon.CHECK, Ui.Style.SECONDARY, true, () -> {
			def.tint.defaultColor = color;
			previewColor = 0;
			changed();
		}, Tr.t("btb.t.diese_farbe_ist_die_grundfarbe_des_cosme"));
		ui.button(x + half + 4, y, half, 16, Tr.t("btb.t.zur_palette"), Icon.PLUS, Ui.Style.SECONDARY, def.tint.palette.size() < 12, () -> {
			if (!def.tint.palette.contains(color)) {
				def.tint.palette.add(color);
				changed();
			}
		}, Tr.t("btb.t.spieler_koennen_diese_farbe_in_der_garde"));
		y += 22;
		ui.text(Tr.t("btb.t.palette_rechtsklick_entfernt"), x + 2, y, t.textMuted);
		y += 12;
		bx = x;
		for (int i = 0; i < def.tint.palette.size(); i++) {
			int c = def.tint.palette.get(i);
			int idx = i;
			ui.rounded(bx, y, 14, 14, 7, t.glassBorder);
			ui.rounded(bx + 2, y + 2, 10, 10, 5, c);
			ui.region(bx, y, 14, 14, () -> setPreviewColor(c), () -> {
				def.tint.palette.remove(idx);
				changed();
			}, Colors.format(c));
			bx += 17;
		}
		if (def.tint.palette.isEmpty()) {
			ui.text("leer", x + 2, y + 3, t.textFaint);
		}
	}

	private void setPreviewColor(int c) {
		previewColor = c;
		if (!def.tint.enabled) {
			def.tint.enabled = true;
		}
		dirty = true;
	}

	private void drawAnimation(int x, int y, int w) {
		Theme t = ui.t;
		ui.text(Tr.t("btb.t.aktive_animationen"), x + 2, y, t.textMuted);
		y += 12;
		if (animations.isEmpty()) {
			ui.text(Tr.t("btb.t.noch_keine_unten_eine_vorlage_hinzufuege"), x + 2, y + 2, t.textFaint);
			y += 14;
		}
		for (CosmeticAnimation an : new ArrayList<>(animations.values())) {
			AnimationPresets.Preset p = AnimationPresets.presetOf(an.name);
			String label = p != null ? p.label() + " (" + String.join(", ", an.bones.keySet()) + ")" : an.name;
			ui.rounded(x, y, w, 15, 6, t.surface);
			ui.text(ui.ellipsize(label, w - 24), x + 6, y + 4, t.text);
			Icon.CLOSE.draw(ui.g, x + w - 12, y + 4, ui.hover(x + w - 16, y, 16, 15) ? t.danger : t.textMuted);
			ui.region(x + w - 16, y, 16, 15, () -> {
				animations.remove(an.name);
				def.animations.remove(an.name);
				changed();
			}, null, Tr.t("btb.t.entfernen"));
			y += 17;
		}
		y += 6;
		ui.divider(x, y, w);
		y += 8;
		ui.text(Tr.t("btb.t.vorlage_hinzufuegen"), x + 2, y, t.textFaint);
		y += 12;
		AnimationPresets.Preset[] ps = AnimationPresets.Preset.values();
		ui.cycle(x, y, w, Tr.t("btb.t.bewegung"), preset.label(), () -> preset = ps[(preset.ordinal() + 1) % ps.length], () -> preset = ps[(preset.ordinal() + ps.length - 1) % ps.length]);
		y += 14;
		ui.text(preset.description(), x + 2, y + 2, t.textFaint);
		y += 14;
		List<String> bones = model.bones.stream().map(b -> b.name).toList();
		if (!bones.isEmpty()) {
			presetBone = Math.min(presetBone, bones.size() - 1);
			ui.cycle(x, y, w, Tr.t("btb.t.teil_bone"), bones.get(presetBone), () -> presetBone = (presetBone + 1) % bones.size(), () -> presetBone = (presetBone + bones.size() - 1) % bones.size());
			y += 18;
		}
		ui.stepper(x, y, w, Tr.t("btb.t.tempo"), presetSpeed, 0.1f, 0.1f, 6, "/s", v -> presetSpeed = v);
		y += 15;
		ui.stepper(x, y, w, Tr.t("btb.t.staerke"), presetAmp, 0.5f, 0.5f, 60, "", v -> presetAmp = v);
		y += 19;
		int half = (w - 4) / 2;
		ui.button(x, y, half, 18, Tr.t("btb.t.hinzufuegen"), Icon.PLUS, Ui.Style.PRIMARY, !bones.isEmpty(), () -> {
			String bone = bones.get(presetBone);
			String second = preset == AnimationPresets.Preset.FLAP ? mirrorBone(bone, bones) : null;
			CosmeticAnimation an = AnimationPresets.create(preset, bone, second, presetSpeed, presetAmp);
			animations.put(an.name, an);
			def.animations.remove(an.name);
			def.animations.add(an.name);
			changed();
		}, null);
		ui.button(x + half + 4, y, half, 18, "animation.json", Icon.IMPORT, Ui.Style.SECONDARY, true,
				() -> pick(Tr.t("btb.t.bedrock_animation"), "json", this::loadFile), Tr.t("btb.t.animationen_aus_blockbench_bedrock_impor"));
	}

	private static String mirrorBone(String bone, List<String> bones) {
		String[][] pairs = {{"left", "right"}, {"_l", "_r"}, {"links", "rechts"}};
		for (String[] p : pairs) {
			for (int i = 0; i < 2; i++) {
				if (bone.contains(p[i])) {
					String other = bone.replace(p[i], p[1 - i]);
					if (bones.contains(other)) {
						return other;
					}
				}
			}
		}
		return null;
	}

	private void drawEffects(int x, int y, int w) {
		Theme t = ui.t;
		boolean glow = def.glow != null;
		ui.toggle(x, y, w, Tr.t("btb.t.lichtschein_nur_optisch"), glow, () -> {
			def.glow = glow ? null : new CosmeticDefinition.Glow();
			changed();
		});
		y += 18;
		if (glow) {
			CosmeticDefinition.Glow gl = def.glow;
			int bx = x;
			for (int c : QUICK_COLORS) {
				ui.rounded(bx, y, 14, 14, 7, gl.color == c ? t.accent : t.glassBorder);
				ui.rounded(bx + 2, y + 2, 10, 10, 5, c);
				ui.region(bx, y, 14, 14, () -> {
					gl.color = c;
					changed();
				});
				bx += 17;
			}
			y += 18;
			ui.stepper(x, y, w, Tr.t("btb.t.groesse"), gl.size, 0.5f, 1, 32, " px", v -> {
				gl.size = v;
				changed();
			});
			y += 15;
			ui.stepper(x, y, w, Tr.t("btb.t.staerke"), gl.intensity, 0.05f, 0.05f, 1, "", v -> {
				gl.intensity = v;
				changed();
			});
			y += 15;
			ui.stepper(x, y, w, Tr.t("btb.t.hoehe"), gl.offset[1], 0.5f, -32, 32, " px", v -> {
				gl.offset[1] = v;
				changed();
			});
			y += 15;
			ui.toggle(x, y + 1, w, Tr.t("btb.t.flackern"), gl.flicker, () -> {
				gl.flicker = !gl.flicker;
				changed();
			});
			y += 20;
		}
		ui.divider(x, y, w);
		y += 8;
		boolean parts = def.particles != null;
		ui.toggle(x, y, w, Tr.t("btb.t.partikel"), parts, () -> {
			def.particles = parts ? null : new CosmeticDefinition.Particles();
			changed();
		});
		y += 18;
		if (parts) {
			CosmeticDefinition.Particles p = def.particles;
			int idx = java.util.Arrays.asList(PARTICLES).indexOf(p.type);
			ui.cycle(x, y, w, Tr.t("btb.t.art"), p.type.replace("minecraft:", ""), () -> {
				p.type = PARTICLES[(idx + 1) % PARTICLES.length];
				changed();
			}, () -> {
				p.type = PARTICLES[(Math.max(0, idx) + PARTICLES.length - 1) % PARTICLES.length];
				changed();
			});
			y += 18;
			ui.stepper(x, y, w, Tr.t("btb.t.pro_sekunde"), p.rate, 0.1f, 0, 20, "", v -> {
				p.rate = v;
				changed();
			});
			y += 15;
			ui.stepper(x, y, w, Tr.t("btb.t.hoehe"), p.offset[1], 0.5f, -32, 32, " px", v -> {
				p.offset[1] = v;
				changed();
			});
			y += 15;
			ui.stepper(x, y, w, Tr.t("btb.t.streuung"), p.spread, 0.5f, 0, 16, " px", v -> {
				p.spread = v;
				changed();
			});
			y += 18;
			ui.text(Tr.t("btb.t.partikel_sieht_man_in_der_welt_nicht_in_"), x + 2, y, t.textFaint);
		}
	}

	// ------------------------------------------------------------------ actions

	private void alignToAnchor() {
		float[] b = model.bounds();
		AttachmentPoint ap = def.attachment;
		float cx = (b[0] + b[3]) / 2, cz = (b[2] + b[5]) / 2;
		float tx = ap.anchorX - cx, tz = ap.anchorZ - cz, ty;
		switch (ap) {
			case HEAD, LEFT_SHOULDER, RIGHT_SHOULDER -> ty = ap.anchorY - b[1];
			case BACK -> {
				ty = ap.anchorY - (b[1] + b[4]) / 2;
				tz = ap.anchorZ - b[2];
			}
			default -> ty = ap.anchorY - (b[1] + b[4]) / 2;
		}
		def.transform.offset = new float[]{tx, ty, tz};
		changed();
		status = Tr.t("btb.t.modell_auf") + de.behindtheblocks.skinstudio.client.gui.WardrobeScreen.attachmentLabel(ap) + Tr.t("btb.t.ausgerichtet");
	}

	private void pick(String label, String patterns, Consumer<Path> handler) {
		FileDialogs.openFiles(label, patterns, false, paths -> {
			if (paths.isEmpty()) {
				status = Tr.t("btb.t.kein_datei_dialog_verfuegbar_datei_einfa");
				return;
			}
			paths.forEach(handler);
		});
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		paths.forEach(this::loadFile);
	}

	/** Routes a file by type: .png → texture, .json → model or animation. */
	private void loadFile(Path p) {
		String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
		if (n.endsWith(".png")) {
			loadTexture(p, n.contains("emissive") || n.contains("glow") || n.contains("leucht"));
			return;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				return Files.readAllBytes(p);
			} catch (Exception e) {
				throw new FormatException(Tr.t("btb.t.datei_nicht_lesbar") + e.getMessage());
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((bytes, err) -> {
			if (err != null) {
				status = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
				return;
			}
			try {
				String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
				if (text.contains("\"animations\"")) {
					AnimationIO.Result r = AnimationIO.read(bytes);
					animations.putAll(r.animations());
					status = r.animations().size() + Tr.t("btb.t.animation_en_importiert") + (r.warnings().isEmpty() ? "" : " – " + r.warnings().getFirst());
				} else {
					GeometryIO.Result r = GeometryIO.read(bytes);
					model = r.first().copy();
					def.preview = null;
					status = Tr.t("btb.t.modell_2") + model.identifier + Tr.t("btb.t.geladen") + model.cubeCount() + Tr.t("btb.t.wuerfel") + (r.warnings().isEmpty() ? "" : " – " + r.warnings().getFirst());
					if (model.bones.stream().noneMatch(b -> AttachmentPoint.Part.fromBoneName(b.name) != null)) {
						float[] b = model.bounds();
						if (b[1] < 10) {
							alignToAnchor();
							status += Tr.t("btb.t.und_am_befestigungspunkt_ausgerichtet");
						}
					}
				}
				changed();
			} catch (FormatException e) {
				status = n + ": " + e.getMessage();
			}
		}, Minecraft.getInstance());
	}

	private void loadTexture(Path p, boolean emissive) {
		CompletableFuture.supplyAsync(() -> {
			try {
				byte[] b = Files.readAllBytes(p);
				Png.info(b, p.getFileName().toString());
				return b;
			} catch (FormatException e) {
				throw e;
			} catch (Exception e) {
				throw new FormatException(Tr.t("btb.t.datei_nicht_lesbar") + e.getMessage());
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((bytes, err) -> {
			if (err != null) {
				status = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
				return;
			}
			if (emissive) {
				files.put("emissive.png", bytes);
				def.emissiveTexture = "emissive.png";
				status = Tr.t("btb.t.leuchttextur_geladen");
			} else {
				files.put("texture.png", bytes);
				def.texture = "texture.png";
				def.preview = null;
				Png.Info info = Png.info(bytes, "texture.png");
				status = Tr.t("btb.t.textur_geladen") + info.width() + "×" + info.height() + ")";
			}
			changed();
		}, Minecraft.getInstance());
	}

	private void loadMask(Path p) {
		CompletableFuture.supplyAsync(() -> {
			try {
				byte[] b = Files.readAllBytes(p);
				Png.info(b, p.getFileName().toString());
				return b;
			} catch (Exception e) {
				throw new FormatException(e.getMessage());
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((bytes, err) -> {
			if (err != null) {
				status = err.getMessage();
				return;
			}
			files.put("tint_mask.png", bytes);
			def.tintMask = "tint_mask.png";
			def.tint.enabled = true;
			changed();
		}, Minecraft.getInstance());
	}

	private void save(boolean equip) {
		rebuild();
		if (error != null || built == null) {
			return;
		}
		CosmeticAsset asset = built;
		String oldId = originalId;
		CompletableFuture.runAsync(() -> {
			try {
				PackStore store = CosmeticLibrary.get().store;
				if (oldId != null && !oldId.equals(asset.id())) {
					store.deleteCustom(oldId);
				}
				store.saveCustom(asset);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((v, err) -> {
			if (err != null) {
				status = Tr.t("btb.t.speichern_fehlgeschlagen") + err.getMessage();
				return;
			}
			unsaved = false;
			status = Tr.t("btb.t.gespeichert_in_meine_cosmetics");
			CosmeticLibrary.get().reload().thenRun(() -> {
				if (equip) {
					CosmeticAsset saved = CosmeticLibrary.get().find(asset.id());
					if (saved != null) {
						Loadout l = Wardrobe.get().equipped().copy();
						if (oldId != null) {
							l.unequip(oldId);
						}
						l.equip(saved.definition, previewColor, saved.contentHash, CosmeticLibrary.get()::definition);
						Wardrobe.get().markUsed(saved.id());
						Wardrobe.get().setEquipped(l);
						status = Tr.t("btb.t.gespeichert_und_angelegt");
					}
				}
			});
		}, Minecraft.getInstance());
	}

	private void export() {
		rebuild();
		if (built == null || error != null) {
			return;
		}
		CosmeticAsset asset = built;
		CompletableFuture.supplyAsync(() -> {
			try {
				return CosmeticLibrary.get().store.export(PackWriter.folderName(asset.id()) + PackStore.COSMETIC_EXT, PackWriter.singleCosmetic(asset));
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((path, err) -> {
			status = err != null ? Tr.t("btb.t.export_fehlgeschlagen") + err.getMessage() : Tr.t("btb.t.exportiert_nach") + path;
			if (err == null) {
				com.mojang.blaze3d.Blaze3D.openPath(path.getParent());
			}
		}, Minecraft.getInstance());
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (EditBox b : new EditBox[]{nameBox, idBox, descBox}) {
			if (b.visible && b.isMouseOver(event.x(), event.y())) {
				return super.mouseClicked(event, doubleClick);
			}
			b.setFocused(false);
		}
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
		return ui.scroll(x, y, sy) || super.mouseScrolled(x, y, sx, sy);
	}

	/** Used by the development AutoTest only. */
	public void autotest(String[] args) {
		switch (args[0]) {
			case "tab" -> tab = Tab.valueOf(args[1].toUpperCase(Locale.ROOT));
			case "load" -> loadFile(Path.of(args[1]));
			case "offset" -> {
				def.transform.offset[1] = Float.parseFloat(args[1]);
				changed();
			}
			case "preset" -> {
				preset = AnimationPresets.Preset.valueOf(args[1].toUpperCase(Locale.ROOT));
				CosmeticAnimation an = AnimationPresets.create(preset, model.bones.getFirst().name, null, presetSpeed, presetAmp);
				animations.put(an.name, an);
				def.animations.add(an.name);
				changed();
			}
			case "color" -> setPreviewColor(Colors.parse(args[1]));
			case "save" -> save(true);
			default -> {
			}
		}
	}

	@Override
	public void removed() {
		de.behindtheblocks.skinstudio.client.gui.ui.GuiFit.restore();
		super.removed();
	}
}
