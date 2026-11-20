package de.behindtheblocks.skinstudio.client.gui;

import de.behindtheblocks.skinstudio.client.render.ActiveCosmetic;
import de.behindtheblocks.skinstudio.client.render.CosmeticRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

/**
 * Interactive 3D player preview for GUI screens. It renders through the real entity pipeline,
 * so cosmetics look exactly as in the world (same layer, textures and animations).
 * Works in-game (uses the player's skin and render state) and in the main menu (own profile skin).
 */
public final class PlayerPreview {
	public enum Focus {
		FULL(0.8f, 0.1f), HEAD(1.5f, 0.47f), UPPER(1.5f, 0.2f), LEGS(1.5f, -0.27f);

		final float zoom;
		final float shift;

		Focus(float zoom, float shift) {
			this.zoom = zoom;
			this.shift = shift;
		}
	}

	public float yaw = -25;
	public float pitch = -6;
	/** Extra size factor (e.g. larger figure next to the main menu). */
	public float scale = 1f;
	/** Moves the figure down inside its frame (in blocks), leaving room for tall hats and torches. */
	public float lift = 0f;
	/** Emote shown in this preview (or null). */
	public String emote;
	/** Body proportions to show instead of the own ones (null = own). */
	public de.behindtheblocks.skinstudio.network.SkinStudioPayloads.Body bodyOverride;
	/** Restart the emote when it ends (wardrobe preview). */
	public boolean loopEmote;
	private long emoteStart;

	public void playEmote(String id) {
		emote = id;
		emoteStart = System.nanoTime();
	}
	public Focus focus = Focus.FULL;
	private float zoom = 1;
	private float shift;
	private Supplier<PlayerSkin> skinLookup;
	/** Skin to show instead of the worn one (try-on in the skin list); null = current. */
	public de.behindtheblocks.skinstudio.skin.SkinData skinOverride;

	/** Drag rotates the model. */
	public void drag(double dx, double dy) {
		yaw = (yaw + (float) dx * 1.6f) % 360;
		pitch = Math.max(-35, Math.min(35, pitch - (float) dy * 0.8f));
	}

	public void draw(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, List<ActiveCosmetic> cosmetics) {
		zoom += (focus.zoom - zoom) * 0.2f;
		shift += (focus.shift - shift) * 0.2f;
		AvatarRenderState state = createState();
		if (state == null) {
			return;
		}
		var bodyNow = bodyOverride != null ? bodyOverride : de.behindtheblocks.skinstudio.client.data.Wardrobe.get().body();
		if (!bodyNow.isNormal()) {
			state.setData(de.behindtheblocks.skinstudio.client.render.CosmeticRenderData.BODY, new float[]{bodyNow.height(), bodyNow.head(), bodyNow.arms(), bodyNow.legs()});
		}
		if (emote != null) {
			float[] pose = de.behindtheblocks.skinstudio.emote.Emotes.sample(emote, (System.nanoTime() - emoteStart) / 1e9f);
			if (pose == null && loopEmote) {
				emoteStart = System.nanoTime();
				pose = de.behindtheblocks.skinstudio.emote.Emotes.sample(emote, 0);
			} else if (pose == null) {
				emote = null;
			}
			state.setData(de.behindtheblocks.skinstudio.client.render.CosmeticRenderData.EMOTE, pose);
			var held = de.behindtheblocks.skinstudio.client.emote.ClientEmotes.itemOf(emote);
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (held != null && mc.player != null) {
				state.rightHandItemStack = held;
				mc.getItemModelResolver().updateForLiving(state.rightHandItemState, held, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, mc.player);
			}
		}
		de.behindtheblocks.skinstudio.client.compat.NotEnoughAnimationsCompat.detach(state);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		state.scoreText = null;
		state.pose = Pose.STANDING;
		state.isCrouching = false;
		state.walkAnimationPos = 0;
		state.walkAnimationSpeed = 0;
		// the real player's movement must not leak into the still preview: cape at rest, no swimming/gliding/using
		state.capeFlap = 0;
		state.capeLean = 0;
		state.capeLean2 = 0;
		state.swimAmount = 0;
		state.isFallFlying = false;
		state.fallFlyingTimeInTicks = 0;
		state.isVisuallySwimming = false;
		state.isUsingItem = false;
		state.ticksUsingItem = 0;
		state.speedValue = 1;
		state.deathTime = 0;
		state.isUpsideDown = false;
		state.isAutoSpinAttack = false;
		state.hasRedOverlay = false;
		state.swingAnimation = 0;
		state.elytraRotX = 0.2617994f;
		state.elytraRotY = 0;
		state.elytraRotZ = -0.2617994f;
		state.isInvisible = false;
		state.bodyRot = 180 + yaw;
		state.yRot = 0;
		state.xRot = 0;
		state.ageInTicks = (System.nanoTime() / 1_000_000L % 3_600_000L) / 50f;
		state.boundingBoxWidth = 0.6f;
		state.boundingBoxHeight = 1.8f;
		state.scale = 1;
		if (skinOverride != null) {
			state.skin = de.behindtheblocks.skinstudio.client.skin.ClientSkins.toPlayerSkin(skinOverride, state.skin,
					de.behindtheblocks.skinstudio.client.data.ClientSettings.get().blinkSelf ? 0 : -1);
			java.util.List<ActiveCosmetic> all = new java.util.ArrayList<>(de.behindtheblocks.skinstudio.client.skin.ClientSkins.extrasFor(skinOverride));
			for (ActiveCosmetic c : cosmetics) {
				if (!c.asset().id().startsWith("skinpart:")) {
					all.add(c);
				}
			}
			cosmetics = all;
		}
		state.setData(CosmeticRenderData.COSMETICS, cosmetics);
		var shownSkin = skinOverride != null ? skinOverride
				: de.behindtheblocks.skinstudio.client.skin.SkinLibrary.get().find(de.behindtheblocks.skinstudio.client.data.Wardrobe.get().skin());
		state.setData(CosmeticRenderData.FACE_Y, de.behindtheblocks.skinstudio.client.skin.ClientSkins.faceOffset(shownSkin));
		state.setData(CosmeticRenderData.PREVIEW, true);
		state.showHat = state.showJacket = state.showLeftSleeve = state.showRightSleeve = state.showLeftPants = state.showRightPants = true;
		// the same overlay layers as in the world are hidden (3D voxels replace them, cosmetics may hide them);
		// otherwise flat layer and 3D voxels overlap and flicker while turning
		for (ActiveCosmetic c : cosmetics) {
			for (String layer : c.asset().definition.hideLayers) {
				switch (layer) {
					case "hat" -> state.showHat = false;
					case "jacket" -> state.showJacket = false;
					case "sleeves" -> state.showLeftSleeve = state.showRightSleeve = false;
					case "pants" -> state.showLeftPants = state.showRightPants = false;
					default -> {
					}
				}
			}
		}

		int h = y1 - y0;
		float size = h * 0.42f * zoom * scale;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf xRotation = new Quaternionf().rotateX((float) Math.toRadians(pitch));
		rotation.mul(xRotation);
		Vector3f translation = new Vector3f(0, state.boundingBoxHeight / 2 + 0.06f + shift * 1.8f + lift, 0);
		g.entity(state, size, translation, rotation, xRotation, x0, y0, x1, y1);
	}

	/**
	 * A fresh render state for the preview – never the real player's one. That state carries whatever the
	 * player is doing in the world (crouching, swinging, items, cape physics) and other mods hook into it
	 * (e.g. Not Enough Animations), which made the still figure crouch or jitter.
	 */
	private AvatarRenderState createState() {
		Minecraft mc = Minecraft.getInstance();
		AvatarRenderState s = new AvatarRenderState();
		s.entityType = net.minecraft.world.entity.EntityTypes.PLAYER;
		PlayerSkin skin = null;
		if (mc.player != null) {
			skin = mc.player.getSkin(); // includes the BTB skin and the account cape
		} else {
			if (skinLookup == null) {
				try {
					skinLookup = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
				} catch (RuntimeException e) {
					skinLookup = DefaultPlayerSkin::getDefaultSkin;
				}
			}
			skin = skinLookup.get();
			var chosen = de.behindtheblocks.skinstudio.client.skin.SkinLibrary.get().find(de.behindtheblocks.skinstudio.client.data.Wardrobe.get().skin());
			if (chosen != null) {
				skin = de.behindtheblocks.skinstudio.client.skin.ClientSkins.toPlayerSkin(chosen, skin == null ? DefaultPlayerSkin.getDefaultSkin() : skin);
			}
		}
		s.skin = skin == null ? DefaultPlayerSkin.getDefaultSkin() : skin;
		s.showCape = de.behindtheblocks.skinstudio.client.data.ClientSettings.get().showCape;
		return s;
	}
}
