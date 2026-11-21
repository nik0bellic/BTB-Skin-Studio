package de.behindtheblocks.skinstudio.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional support for BTB Cinematics replays (no hard dependency, all by reflection).
 * <ul>
 *   <li>Recording: when a player starts or stops an emote while Cinematics records a take, the take gets a
 *   "command" event {@code btbskin puppetemote <player uuid> <emote>}.</li>
 *   <li>Playback: Cinematics runs that command at the same moment; the server sends a {@code PuppetEmote} and
 *   every mannequin wearing that player's profile (the replay puppet) plays the emote.</li>
 * </ul>
 * Cosmetics, the BTB skin, body proportions and blinking are shown on those puppets on the client side
 * (they wear the recorded player's profile).
 */
public final class CinematicsCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("btb_cinematic");
	private static Method log;
	private static boolean failed;

	private CinematicsCompat() {
	}

	static void logEmote(UUID player, String id) {
		if (!LOADED || failed) {
			return;
		}
		try {
			if (log == null) {
				log = Class.forName("de.behindtheblocks.cinematic.replay.ReplayManager").getMethod("log", String.class, String.class);
			}
			log.invoke(null, "command", "btbskin puppetemote " + player + " " + (id.isEmpty() ? "stop" : id));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed = true;
		}
	}

	/** {@code /btbskin puppetemote <profile uuid> <emote|stop>} – used by replays, needs operator rights. */
	static void registerCommand(ServerCosmeticSync sync) {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dispatcher.register(
				Commands.literal("btbskin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("puppetemote").then(Commands.argument("profile", UuidArgument.uuid())
								.then(Commands.argument("emote", StringArgumentType.word()).executes(c -> {
									String id = StringArgumentType.getString(c, "emote");
									if (!id.equals("stop") && !de.behindtheblocks.skinstudio.emote.Emotes.isBuiltIn(id)
											&& !de.behindtheblocks.skinstudio.emote.CustomEmote.isNetworkId(id)) {
										return 0;
									}
									sync.puppetEmote(c.getSource().getServer(), UuidArgument.getUuid(c, "profile"), id.equals("stop") ? "" : id);
									return 1;
								}))))));
	}
}
