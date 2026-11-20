package de.behindtheblocks.skinstudio.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * Optional support for "Not Enough Animations" (tr7zw). NEA stores the real entity in every render state and
 * animates arms, cape and items from it. For the GUI previews (wardrobe, menu figure, emote wheel) that link is
 * removed, so the still figure is not driven by what the player is doing in the world. In the world NEA keeps
 * working; while an emote plays, the emote pose is applied after NEA (mixin priority). No hard dependency.
 */
public final class NotEnoughAnimationsCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("notenoughanimations");
	private static Method setEntity;
	private static boolean failed;

	private NotEnoughAnimationsCompat() {
	}

	/** Detaches the entity NEA attached to this render state (no-op without NEA). */
	public static void detach(LivingEntityRenderState state) {
		if (!LOADED || failed) {
			return;
		}
		try {
			if (setEntity == null) {
				Class<?> c = Class.forName("dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState");
				setEntity = c.getMethod("setEntity", LivingEntity.class);
			}
			if (setEntity.getDeclaringClass().isInstance(state)) {
				setEntity.invoke(state, (Object) null);
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed = true; // another NEA version: previews just keep NEA's animation
		}
	}
}
