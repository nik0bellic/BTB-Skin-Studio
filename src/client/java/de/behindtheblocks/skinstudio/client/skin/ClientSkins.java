package de.behindtheblocks.skinstudio.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.bedrock.Images;
import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackReader;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.pack.PackWriter;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinLayers3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Applies chosen skins: replaces {@code getSkin()} of players (world, first-person hand, player list),
 * and provides the skin's accessory geometry and optional 3D outer layers as extra render geometry.
 */
public final class ClientSkins {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Skins");

	/** What a player wears (synced): skin id, content hash for transfer, arm model. */
	public record SkinRef(String id, String hash, boolean slim) {
	}

	private static final Map<UUID, SkinRef> REMOTE = new HashMap<>();
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Set<String> PENDING = new HashSet<>();
	private static final Map<String, CosmeticAsset> EXTRA_ASSETS = new HashMap<>();
	private static final Map<String, byte[]> VANILLA_BYTES = new HashMap<>();
	private static int revision;

	private ClientSkins() {
	}

	public static int revision() {
		return revision + SkinLibrary.get().revision();
	}

	public static void setRemote(UUID player, SkinRef ref) {
		if (ref == null || ref.id().isEmpty()) {
			REMOTE.remove(player);
		} else {
			REMOTE.put(player, ref);
		}
		revision++;
	}

	public static void clearRemote() {
		REMOTE.clear();
		revision++;
	}

	public static boolean isSelf(UUID player) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getUUID().equals(player) || mc.getUser() != null && player.equals(mc.getUser().getProfileId());
	}

	/** The skin a player wears through BTB Skin Studio, or null for the normal account skin. */
	public static SkinData skinOf(UUID player) {
		if (isSelf(player)) {
			return SkinLibrary.get().find(Wardrobe.get().skin());
		}
		if (!ClientSettings.get().showOtherPlayers) {
			return null;
		}
		SkinRef ref = REMOTE.get(player);
		if (ref == null) {
			return null;
		}
		SkinData s = SkinLibrary.get().resolve(ref.id(), ref.hash());
		if (s == null) {
			de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync.requestMissingHash(ref.hash());
		}
		return s;
	}

	/** Replacement for {@code AbstractClientPlayer#getSkin()}; keeps cape and elytra of the original. */
	public static PlayerSkin override(UUID player, PlayerSkin original) {
		SkinData s = skinOf(player);
		if (s == null) {
			return original;
		}
		return toPlayerSkin(s, original, player.hashCode());
	}

	public static PlayerSkin toPlayerSkin(SkinData s, PlayerSkin original) {
		return toPlayerSkin(s, original, 0);
	}

	/** True for a short moment every few seconds; the seed spreads players so they do not blink together. */
	public static boolean forceBlink;

	public static boolean blinking(int seed) {
		if (forceBlink) {
			return true;
		}
		if (!ClientSettings.get().blinking) {
			return false;
		}
		long period = 4200 + Math.floorMod(seed, 1700);
		long t = Math.floorMod(net.minecraft.util.Util.getMillis() + (long) seed * 7919L, period);
		return t < 140;
	}

	public static PlayerSkin toPlayerSkin(SkinData s, PlayerSkin original, int seed) {
		PlayerModelType model = s.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		ClientAsset.Texture body;
		Identifier closed = blinking(seed) ? blinkTexture(s) : null;
		if (closed != null) {
			body = new ClientAsset.ResourceTexture(closed, closed);
		} else if (s.isVanilla()) {
			body = new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(vanillaPath(s)));
		} else {
			Identifier tex = texture(s);
			if (tex == null) {
				return original;
			}
			body = new ClientAsset.ResourceTexture(tex, tex);
		}
		return PlayerSkin.insecure(body, original == null ? null : original.cape(), original == null ? null : original.elytra(), model);
	}

	static String vanillaPath(SkinData s) {
		String rest = s.id.substring("vanilla:".length());
		int us = rest.lastIndexOf('_');
		return "entity/player/" + rest.substring(us + 1) + "/" + rest.substring(0, us);
	}

	/** Uploaded texture of a custom/Bedrock skin, loading it in the background on first use. */
	public static Identifier texture(SkinData s) {
		Identifier id = TEXTURES.get(s.hash);
		if (id != null || s.texture == null || !PENDING.add(s.hash)) {
			return id;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				return NativeImage.read(s.texture);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((img, err) -> {
			PENDING.remove(s.hash);
			if (err != null) {
				LOG.warn("Skin {} nicht lesbar: {}", s.id, err.getMessage());
				return;
			}
			Identifier tex = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "skin/" + s.hash);
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "skin " + s.id, img));
			TEXTURES.put(s.hash, tex);
			revision++;
		}, Minecraft.getInstance());
		return null;
	}

	private static final Map<String, Identifier> BLINK = new HashMap<>();
	private static final Set<String> BLINK_DONE = new HashSet<>();

	/** Texture of the skin with closed eyes ({@link SkinBlink}), made in the background on first use; null if none. */
	public static Identifier blinkTexture(SkinData s) {
		String key = s.id + "_" + s.hash;
		Identifier id = BLINK.get(key);
		if (id != null || !BLINK_DONE.add(key)) {
			return id;
		}
		byte[] png = iconSource(s);
		if (png == null) {
			BLINK_DONE.remove(key);
			return null;
		}
		CompletableFuture.supplyAsync(() -> {
			BufferedImage closed = de.behindtheblocks.skinstudio.skin.SkinBlink.close(Images.read(png, "skin.png"));
			if (closed == null) {
				return null;
			}
			try {
				java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
				javax.imageio.ImageIO.write(closed, "png", out);
				return NativeImage.read(out.toByteArray());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((img, err) -> {
			if (err != null || img == null) {
				return;
			}
			Identifier tex = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "skin_blink/" + Integer.toHexString(key.hashCode()));
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "skin blink " + s.id, img));
			BLINK.put(key, tex);
		}, Minecraft.getInstance());
		return null;
	}

	/** Accessory geometry of the skin and, if enabled, the 3D outer layers – drawn like cosmetics. */
	public static List<ActiveCosmetic> extras(UUID player) {
		return extrasFor(skinOf(player));
	}

	public static List<ActiveCosmetic> extrasFor(SkinData s) {
		List<ActiveCosmetic> out = new ArrayList<>(2);
		if (s != null && s.accessories != null && s.texture != null) {
			CosmeticAsset a = extraAsset(s, "acc", s.accessories, s.texture, List.of());
			if (a != null) {
				out.add(ActiveCosmetic.of(a, 0));
			}
		}
		if (ClientSettings.get().skinLayers3d && s != null) {
			byte[] png = s.texture != null ? s.texture : vanillaBytes(s);
			if (png != null) {
				int parts = ClientSettings.get().layers3dParts;
				String kind = "layers3d" + parts;
				CosmeticAsset a = EXTRA_ASSETS.get(kind + "_" + s.id + "_" + s.hash);
				if (a == null && parts != 0) {
					try {
						BufferedImage img = Images.read(png, "skin.png");
						a = extraAsset(s, kind, SkinLayers3D.build(img, s.slim, parts), png, SkinLayers3D.hiddenLayers(parts));
					} catch (RuntimeException e) {
						LOG.warn("3D-Ebenen für {} nicht möglich: {}", s.id, e.getMessage());
					}
				}
				if (a != null) {
					out.add(ActiveCosmetic.of(a, 0));
				}
			}
		}
		return out;
	}

	private static CosmeticAsset extraAsset(SkinData s, String kind, GeoModel model, byte[] png, List<String> hideLayers) {
		String key = kind + "_" + s.id + "_" + s.hash;
		CosmeticAsset cached = EXTRA_ASSETS.get(key);
		if (cached != null || EXTRA_ASSETS.containsKey(key)) {
			return cached;
		}
		try {
			CosmeticDefinition d = new CosmeticDefinition();
			d.id = "skinpart:" + kind + "_" + de.behindtheblocks.skinstudio.bedrock.BedrockImporter.sanitize(s.id);
			d.names.put("en", s.name("en"));
			d.type = CosmeticType.ARMOR_DECORATION;
			d.attachment = AttachmentPoint.BODY;
			d.hideLayers.addAll(hideLayers);
			ArchiveFiles extra = ArchiveFiles.empty();
			extra.put("texture.png", png);
			ArchiveFiles files = PackWriter.cosmeticFiles(d, model, null, extra);
			CosmeticAsset a = PackReader.readCosmetic(files, PackStore.CUSTOM_PACK_ID, kind, new ArrayList<>());
			EXTRA_ASSETS.put(key, a);
			return a;
		} catch (RuntimeException e) {
			LOG.warn("Skin-Zusatzteile von {} nicht nutzbar: {}", s.id, e.getMessage());
			EXTRA_ASSETS.put(key, null);
			return null;
		}
	}

	/** PNG bytes of a Minecraft default skin (for 3D layers), read once from the game resources. */
	private static byte[] vanillaBytes(SkinData s) {
		if (!s.isVanilla()) {
			return null;
		}
		return VANILLA_BYTES.computeIfAbsent(s.id, k -> {
			Identifier path = Identifier.withDefaultNamespace("textures/" + vanillaPath(s) + ".png");
			try (var in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(path).open()) {
				return in.readAllBytes();
			} catch (Exception e) {
				return null;
			}
		});
	}

	private static final Map<String, Identifier> ICONS = new HashMap<>();
	private static final Set<String> ICON_PENDING = new HashSet<>();

	/** Front-view icon texture (16×32 figure at 4×) for the skin list. */
	public static Identifier icon(SkinData s) {
		String key = s.id + "_" + s.hash;
		Identifier id = ICONS.get(key);
		if (id != null || !ICON_PENDING.add(key)) {
			return id;
		}
		byte[] src = iconSource(s);
		if (src == null) {
			return null;
		}
		CompletableFuture.supplyAsync(() -> {
			try {
				BufferedImage img = de.behindtheblocks.skinstudio.skin.SkinIcon.render(Images.read(src, "skin.png"), s.slim, 4);
				return NativeImage.read(Images.png(img));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, CosmeticLibrary.IO).whenCompleteAsync((img, err) -> {
			if (err != null) {
				return;
			}
			Identifier tex = Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, "skin_icon/" + Integer.toHexString(key.hashCode()));
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "skin icon " + s.id, img));
			ICONS.put(key, tex);
		}, Minecraft.getInstance());
		return null;
	}

	/** Icon image (front view) for the skin list; reads vanilla skins from the game resources. */
	public static byte[] iconSource(SkinData s) {
		return s.texture != null ? s.texture : vanillaBytes(s);
	}
}
