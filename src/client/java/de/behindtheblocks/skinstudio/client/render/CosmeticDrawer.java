package de.behindtheblocks.skinstudio.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.molang.MolangContext;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Draws baked cosmetics onto a player model. All per-frame work is matrix math over
 * precomputed quads; animation poses are sampled once per submit and captured by the
 * deferred geometry callback.
 */
public final class CosmeticDrawer {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final MolangContext CTX = new MolangContext();

	private CosmeticDrawer() {
	}

	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, PlayerModel model,
							  AvatarRenderState state, ActiveCosmetic active) {
		Identifier tex = CosmeticTextures.get(active.asset(), active.color(), CosmeticTextures.Layer.BASE);
		if (tex == null) {
			return;
		}
		CosmeticDefinition def = active.asset().definition;
		BakedModel baked = active.baked();
		float[] poses = samplePoses(active, state);
		Identifier emissive = def.emissiveTexture == null ? null : CosmeticTextures.get(active.asset(), 0, CosmeticTextures.Layer.EMISSIVE);
		RenderType type = RenderTypes.entityTranslucent(tex);
		RenderType glowType = emissive == null ? null : RenderTypes.eyes(emissive);
		// glass-like cosmetics: opaque faces first (cutout), then the see-through faces sorted back to front
		Translucency.Mask mask = Translucency.of(active.asset(), baked);
		RenderType opaqueType = mask == null ? type : RenderTypes.entityCutout(tex);

		for (BakedModel.Bone root : baked.roots) {
			AttachmentPoint.Part part = AttachmentPoint.Part.fromBoneName(root.name);
			if (part == null) {
				part = def.attachment.part;
			}
			poseStack.pushPose();
			try {
				partOf(model, part).translateAndRotate(poseStack);
				applyAttachment(poseStack, def, part);
				applyAdjust(poseStack, active.adjust(), def, part);
				float faceY = faceOffset(def, state);
				if (faceY != 0) {
					poseStack.translate(0, faceY / 16f, 0); // Java space: +y is down
				}
				float bx = (root.pivotX - part.pivotX) / 16f;
				float by = (root.pivotY - (24f - part.pivotY)) / 16f;
				float bz = (root.pivotZ - part.pivotZ) / 16f;
				collector.submitCustomGeometry(poseStack, opaqueType, (pose, buffer) -> de.behindtheblocks.skinstudio.client.Guard.run("cosmetic draw", () -> drawBone(pose.copy(), buffer, root, bx, by, bz, poses, light, overlay, mask, false, null)));
				if (mask != null) {
					collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
						List<float[]> glass = new java.util.ArrayList<>();
						drawBone(pose.copy(), buffer, root, bx, by, bz, poses, light, overlay, mask, true, glass);
						// far faces first, so every glass face shows what lies behind it
						glass.sort((a, b) -> Float.compare(b[0], a[0]));
						for (float[] g : glass) {
							for (int v = 0; v < 4; v++) {
								int k = 4 + v * 5;
								buffer.addVertex(g[k], g[k + 1], g[k + 2]).setColor(-1).setUv(g[k + 3], g[k + 4]).setOverlay(overlay).setLight(light)
										.setNormal(g[1], g[2], g[3]);
							}
						}
					});
				}
				if (glowType != null) {
					collector.submitCustomGeometry(poseStack, glowType, (pose, buffer) -> de.behindtheblocks.skinstudio.client.Guard.run("cosmetic glow", () -> drawBone(pose.copy(), buffer, root, bx, by, bz, poses, FULL_BRIGHT, overlay, null, false, null)));
				}
			} finally {
				poseStack.popPose(); // always balanced, even if something above fails
			}
		}

		if (def.glow != null && ClientSettings.get().glowEffects) {
			submitGlow(poseStack, collector, model, def, active.adjust(), state.ageInTicks);
		}
	}

	/** Glasses, eyes and mouths follow the eye height of the skin (see {@link CosmeticRenderData#FACE_Y}). */
	private static float faceOffset(CosmeticDefinition def, AvatarRenderState state) {
		if (def.attachment.part != AttachmentPoint.Part.HEAD || def.type == null) {
			return 0;
		}
		switch (def.type) {
			case GLASSES, FACE_ACCESSORY, MUSTACHE -> {
				Float f = state.getData(CosmeticRenderData.FACE_Y);
				return f == null ? 0 : f;
			}
			default -> {
				return 0;
			}
		}
	}

	static ModelPart partOf(PlayerModel model, AttachmentPoint.Part part) {
		return switch (part) {
			case HEAD -> model.head;
			case BODY -> model.body;
			case LEFT_ARM -> model.leftArm;
			case RIGHT_ARM -> model.rightArm;
			case LEFT_LEG -> model.leftLeg;
			case RIGHT_LEG -> model.rightLeg;
		};
	}

	/** Cosmetic offset/rotation/scale around the attachment anchor (Bedrock values → Java Y-down space). */
	private static void applyAttachment(PoseStack ps, CosmeticDefinition def, AttachmentPoint.Part part) {
		CosmeticDefinition.Transform t = def.transform;
		if (t.isIdentity()) {
			return;
		}
		AttachmentPoint ap = def.attachment;
		float ax, ay, az;
		if (ap.part == part) {
			ax = (ap.anchorX + t.pivot[0] - part.pivotX) / 16f;
			ay = -(ap.anchorY + t.pivot[1] - part.pivotY) / 16f;
			az = (ap.anchorZ + t.pivot[2] - part.pivotZ) / 16f;
		} else {
			ax = t.pivot[0] / 16f;
			ay = -t.pivot[1] / 16f;
			az = t.pivot[2] / 16f;
		}
		ps.translate(ax + t.offset[0] / 16f, ay - t.offset[1] / 16f, az + t.offset[2] / 16f);
		if (t.rotation[0] != 0 || t.rotation[1] != 0 || t.rotation[2] != 0) {
			ps.rotate(new Quaternionf().rotationZYX((float) Math.toRadians(t.rotation[2]), (float) Math.toRadians(t.rotation[1]), (float) Math.toRadians(t.rotation[0])));
		}
		ps.scale(t.scale[0], t.scale[1], t.scale[2]);
		ps.translate(-ax, -ay, -az);
	}

	/** The player's personal offset/scale (from the wardrobe), around the attachment anchor. */
	private static void applyAdjust(PoseStack ps, de.behindtheblocks.skinstudio.loadout.Loadout.Adjust a, CosmeticDefinition def, AttachmentPoint.Part part) {
		if (a.isNone()) {
			return;
		}
		AttachmentPoint ap = def.attachment;
		float ax = ap.part == part ? (ap.anchorX - part.pivotX) / 16f : 0;
		float ay = ap.part == part ? -(ap.anchorY - part.pivotY) / 16f : 0;
		float az = ap.part == part ? (ap.anchorZ - part.pivotZ) / 16f : 0;
		ps.translate(ax + a.x() / 16f, ay - a.y() / 16f, az + a.z() / 16f);
		ps.scale(a.scale(), a.scale(), a.scale());
		ps.translate(-ax, -ay, -az);
	}

	/** Per bone 9 floats: rotation (deg), position (px, Bedrock), scale. */
	static float[] samplePoses(ActiveCosmetic active, AvatarRenderState state) {
		BakedModel baked = active.baked();
		Map<String, CosmeticAnimation> anims = active.asset().animations;
		int n = baked.byName.size();
		float[] out = new float[n * 9];
		for (int i = 0; i < n; i++) {
			out[i * 9 + 6] = out[i * 9 + 7] = out[i * 9 + 8] = 1;
		}
		if (anims.isEmpty() || !ClientSettings.get().animations) {
			return out;
		}
		List<String> play = active.asset().definition.animations;
		float seconds = state.ageInTicks / 20f;
		CTX.lifeTime = seconds;
		CTX.moveSpeed = state.walkAnimationSpeed;
		CTX.walkDistance = state.walkAnimationPos;
		CTX.sneaking = state.isCrouching;
		CTX.headPitch = state.xRot;
		CTX.headYaw = state.yRot;
		float[] v = new float[3];
		for (CosmeticAnimation a : anims.values()) {
			if (!play.isEmpty() && !play.contains(a.name)) {
				continue;
			}
			float t = a.localTime(seconds);
			CTX.animTime = t;
			for (Map.Entry<String, CosmeticAnimation.BoneTrack> e : a.bones.entrySet()) {
				BakedModel.Bone bone = baked.bone(e.getKey());
				if (bone == null) {
					continue;
				}
				int o = bone.index * 9;
				CosmeticAnimation.BoneTrack track = e.getValue();
				if (track.rotation != null) {
					track.rotation.sample(t, CTX, v);
					out[o] += v[0];
					out[o + 1] += v[1];
					out[o + 2] += v[2];
				}
				if (track.position != null) {
					track.position.sample(t, CTX, v);
					out[o + 3] += v[0];
					out[o + 4] += v[1];
					out[o + 5] += v[2];
				}
				if (track.scale != null) {
					track.scale.sample(t, CTX, v);
					out[o + 6] *= v[0];
					out[o + 7] *= v[1];
					out[o + 8] *= v[2];
				}
			}
		}
		return out;
	}

	private static final ThreadLocal<Vector3f> TMP = ThreadLocal.withInitial(Vector3f::new);

	/**
	 * mask == null: all quads. Otherwise only opaque quads (translucent = false) or only translucent ones, which
	 * are then not emitted but collected (already transformed) into {@code glass} for sorting:
	 * {distance², nx, ny, nz, then 4 × (x, y, z, u, v)}.
	 */
	private static void drawBone(PoseStack.Pose pose, VertexConsumer buffer, BakedModel.Bone bone, float tx, float ty, float tz,
								 float[] poses, int light, int overlay, Translucency.Mask mask, boolean translucent, List<float[]> glass) {
		int o = bone.index * 9;
		// Animation position is in Bedrock space (Y up) → Java (Y down)
		pose.translate(tx + poses[o + 3] / 16f, ty - poses[o + 4] / 16f, tz + poses[o + 5] / 16f);
		float rx = bone.rotX + (float) Math.toRadians(poses[o]);
		float ry = bone.rotY + (float) Math.toRadians(poses[o + 1]);
		float rz = bone.rotZ + (float) Math.toRadians(poses[o + 2]);
		if (rx != 0 || ry != 0 || rz != 0) {
			pose.rotate(new Quaternionf().rotationZYX(rz, ry, rx));
		}
		if (poses[o + 6] != 1 || poses[o + 7] != 1 || poses[o + 8] != 1) {
			pose.scale(poses[o + 6], poses[o + 7], poses[o + 8]);
		}
		float[] q = bone.quads;
		Vector3f n = TMP.get();
		boolean[] tq = mask == null || bone.index >= mask.perBone().length ? null : mask.perBone()[bone.index];
		Vector3f p = new Vector3f();
		for (int i = 0, qi = 0; i < q.length; i += BakedModel.QUAD_STRIDE, qi++) {
			boolean isGlass = tq != null && qi < tq.length && tq[qi];
			if (mask != null && isGlass != translucent) {
				continue;
			}
			pose.transformNormal(q[i + 20], q[i + 21], q[i + 22], n);
			if (glass != null) {
				float[] g = new float[24];
				g[1] = n.x;
				g[2] = n.y;
				g[3] = n.z;
				float cx = 0, cy = 0, cz = 0;
				for (int v = 0; v < 4; v++) {
					int k = i + v * BakedModel.VERTEX_STRIDE;
					pose.pose().transformPosition(q[k], q[k + 1], q[k + 2], p);
					int go = 4 + v * 5;
					g[go] = p.x;
					g[go + 1] = p.y;
					g[go + 2] = p.z;
					g[go + 3] = q[k + 3];
					g[go + 4] = q[k + 4];
					cx += p.x;
					cy += p.y;
					cz += p.z;
				}
				g[0] = (cx * cx + cy * cy + cz * cz) / 16f;
				glass.add(g);
				continue;
			}
			for (int v = 0; v < 4; v++) {
				int k = i + v * BakedModel.VERTEX_STRIDE;
				buffer.addVertex(pose, q[k], q[k + 1], q[k + 2]).setColor(-1).setUv(q[k + 3], q[k + 4]).setOverlay(overlay).setLight(light).setNormal(n.x, n.y, n.z);
			}
		}
		for (BakedModel.Bone child : bone.children) {
			drawBone(pose.copy(), buffer, child, (child.pivotX - bone.pivotX) / 16f, (child.pivotY - bone.pivotY) / 16f, (child.pivotZ - bone.pivotZ) / 16f,
					poses, light, overlay, mask, translucent, glass);
		}
	}

	// ----------------------------------------------------------------- visual light halo

	private static void submitGlow(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, CosmeticDefinition def,
								   de.behindtheblocks.skinstudio.loadout.Loadout.Adjust adjust, float ageInTicks) {
		CosmeticDefinition.Glow g = def.glow;
		AttachmentPoint ap = def.attachment;
		poseStack.pushPose();
		partOf(model, ap.part).translateAndRotate(poseStack);
		applyAttachment(poseStack, def, ap.part);
		applyAdjust(poseStack, adjust, def, ap.part);
		float cx = (ap.anchorX - ap.part.pivotX + g.offset[0]) / 16f;
		float cy = -(ap.anchorY - ap.part.pivotY + g.offset[1]) / 16f;
		float cz = (ap.anchorZ - ap.part.pivotZ + g.offset[2]) / 16f;
		float flicker = g.flicker ? 0.85f + 0.15f * (float) (Math.sin(ageInTicks * 0.9) * 0.6 + Math.sin(ageInTicks * 2.3) * 0.4) : 1;
		float r = g.size / 16f * (g.flicker ? 0.95f + 0.05f * flicker : 1);
		int alpha = Math.round(255 * g.intensity * flicker);
		int color = (Math.max(0, Math.min(255, alpha)) << 24) | (g.color & 0xFFFFFF);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(GlowTexture.id()), (pose, buffer) -> {
			// three crossed planes: looks round from every angle without needing the camera orientation
			quad(pose, buffer, color, cx - r, cy - r, cz, cx + r, cy - r, cz, cx + r, cy + r, cz, cx - r, cy + r, cz);
			quad(pose, buffer, color, cx, cy - r, cz - r, cx, cy - r, cz + r, cx, cy + r, cz + r, cx, cy + r, cz - r);
			quad(pose, buffer, color, cx - r, cy, cz - r, cx + r, cy, cz - r, cx + r, cy, cz + r, cx - r, cy, cz + r);
		});
		poseStack.popPose();
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer b, int color, float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3) {
		float[][] v = {{x0, y0, z0, 0, 0}, {x1, y1, z1, 1, 0}, {x2, y2, z2, 1, 1}, {x3, y3, z3, 0, 1}};
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < 4; i++) {
				float[] p = v[pass == 0 ? i : 3 - i];
				b.addVertex(pose, p[0], p[1], p[2]).setColor(color).setUv(p[3], p[4])
						.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0, 1, 0);
			}
		}
	}
}
