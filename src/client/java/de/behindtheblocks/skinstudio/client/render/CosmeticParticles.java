package de.behindtheblocks.skinstudio.client.render;

import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.client.net.ClientCosmeticSync;
import de.behindtheblocks.skinstudio.cosmetic.AttachmentPoint;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only particle effects of cosmetics (e.g. sparks of the torch). Spawned locally on every
 * client for nearby players, respecting the vanilla particle setting; nothing is sent over the network.
 */
public final class CosmeticParticles {
	private static final double MAX_DISTANCE_SQ = 40 * 40;
	private static final float PX = 0.9375f / 16f;
	private static final Map<String, SimpleParticleType> TYPES = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<Integer, Float> ACCUMULATOR = new HashMap<>();

	private CosmeticParticles() {
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused() || !ClientSettings.get().particles) {
			return;
		}
		ParticleStatus status = mc.options.particles().get();
		if (status == ParticleStatus.MINIMAL) {
			return;
		}
		float factor = status == ParticleStatus.DECREASED ? 0.4f : 1f;
		for (Player p : level.players()) {
			if (p.isInvisible() || p.isSpectator() || p.distanceToSqr(mc.player) > MAX_DISTANCE_SQ) {
				continue;
			}
			for (ActiveCosmetic c : ClientCosmeticSync.activeFor(p.getUUID())) {
				CosmeticDefinition d = c.asset().definition;
				if (d.particles == null) {
					continue;
				}
				SimpleParticleType type = type(d.particles.type);
				if (type == null) {
					continue;
				}
				int key = p.getId() * 31 + d.id.hashCode();
				float acc = ACCUMULATOR.getOrDefault(key, RANDOM.nextFloat()) + d.particles.rate * factor / 20f;
				while (acc >= 1) {
					acc -= 1;
					spawn(level, p, d, type);
				}
				ACCUMULATOR.put(key, acc);
			}
		}
		if (ACCUMULATOR.size() > 4096) {
			ACCUMULATOR.clear();
		}
	}

	private static void spawn(ClientLevel level, Player p, CosmeticDefinition d, SimpleParticleType type) {
		AttachmentPoint ap = d.attachment;
		float spread = d.particles.spread;
		float lx = ap.anchorX + d.transform.offset[0] + d.particles.offset[0] + (RANDOM.nextFloat() - 0.5f) * spread;
		float ly = ap.anchorY + d.transform.offset[1] + d.particles.offset[1] + (RANDOM.nextFloat() - 0.5f) * spread * 0.5f;
		float lz = ap.anchorZ + d.transform.offset[2] + d.particles.offset[2] + (RANDOM.nextFloat() - 0.5f) * spread;
		double yaw;
		float up = ly, fwd = -lz;
		if (ap.part == AttachmentPoint.Part.HEAD) {
			yaw = Math.toRadians(p.yHeadRot);
			float pitch = (float) Math.toRadians(p.getXRot());
			float ry = up - 24, cos = (float) Math.cos(pitch), sin = (float) Math.sin(pitch);
			float ny = ry * cos - fwd * sin;
			fwd = fwd * cos + ry * sin;
			up = ny + 24;
		} else {
			yaw = Math.toRadians(p.yBodyRot);
		}
		if (p.isCrouching()) {
			up -= 3;
		}
		double leftX = Math.cos(yaw), leftZ = Math.sin(yaw);
		double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
		double x = p.getX() + (leftX * lx + fwdX * fwd) * PX;
		double y = p.getY() + up * PX;
		double z = p.getZ() + (leftZ * lx + fwdZ * fwd) * PX;
		level.addParticle(type, x, y, z, 0, 0.01 + RANDOM.nextFloat() * 0.01, 0);
	}

	private static SimpleParticleType type(String id) {
		return TYPES.computeIfAbsent(id, k -> {
			try {
				ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse(k.contains(":") ? k : "minecraft:" + k));
				return t instanceof SimpleParticleType s ? s : null;
			} catch (RuntimeException e) {
				return null;
			}
		});
	}
}
