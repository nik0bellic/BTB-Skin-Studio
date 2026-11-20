package de.behindtheblocks.skinstudio.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.gui.SkinStudioCommands;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.util.Colors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Development aid: runs a simple script (one action per line) for reproducible in-game tests with
 * screenshots. Only active when the JVM is started with {@code -Dbtb.skinstudio.autotest=<file>};
 * never active in normal play.
 */
final class AutoTest {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/AutoTest");
	private static final Deque<String> LINES = new ArrayDeque<>();
	private static int waitTicks;
	private static int waitPlayers;
	private static boolean started;

	private AutoTest() {
	}

	static void init() {
		String file = System.getProperty("btb.skinstudio.autotest");
		if (file == null) {
			return;
		}
		try {
			List<String> lines = Files.readAllLines(Path.of(file));
			for (String l : lines) {
				if (!l.isBlank() && !l.startsWith("#")) {
					LINES.add(l.trim());
				}
			}
		} catch (Exception e) {
			LOG.error("Testskript {} nicht lesbar", file, e);
			return;
		}
		LOG.info("AutoTest aktiv: {} Schritte", LINES.size());
		ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
	}

	private static void tick(Minecraft mc) {
		if (!started) {
			if (mc.player == null || mc.level == null || CosmeticLibrary.get().revision() == 0) {
				return;
			}
			started = true;
			waitTicks = 40;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return;
		}
		if (waitPlayers > 0) {
			if (mc.level.players().size() < waitPlayers) {
				return;
			}
			waitPlayers = 0;
			waitTicks = 60;
			return;
		}
		while (!LINES.isEmpty() && waitTicks == 0 && waitPlayers == 0) {
			String line = LINES.poll();
			LOG.info("> {}", line);
			try {
				run(mc, line);
			} catch (Exception e) {
				LOG.error("Schritt fehlgeschlagen: {}", line, e);
			}
		}
	}

	private static void run(Minecraft mc, String line) {
		String[] a = line.split("\\s+");
		switch (a[0]) {
			case "wait" -> waitTicks = Integer.parseInt(a[1]);
			case "waitplayers" -> {
				waitPlayers = Integer.parseInt(a[1]);
				waitTicks = 1;
			}
			case "cmd" -> mc.getConnection().sendCommand(line.substring(4));
			case "clear" -> Wardrobe.get().setEquipped(new Loadout());
			case "equip" -> {
				Loadout l = Wardrobe.get().equipped().copy();
				for (int i = 1; i < a.length; i++) {
					String[] parts = a[i].split("@");
					CosmeticDefinition d = CosmeticLibrary.get().definition(parts[0]);
					if (d == null) {
						LOG.error("Unbekanntes Cosmetic {}", parts[0]);
						continue;
					}
					var r = l.equip(d, parts.length > 1 ? Colors.parse(parts[1]) : 0, "", CosmeticLibrary.get()::definition);
					LOG.info("  {} → abgelegt: {}", parts[0], r.removed());
				}
				Wardrobe.get().setEquipped(l);
			}
			case "camera" -> mc.options.setCameraType(switch (a[1]) {
				case "front" -> CameraType.THIRD_PERSON_FRONT;
				case "back" -> CameraType.THIRD_PERSON_BACK;
				default -> CameraType.FIRST_PERSON;
			});
			case "look" -> {
				mc.player.setYRot(Float.parseFloat(a[1]));
				mc.player.setYHeadRot(Float.parseFloat(a[1]));
				mc.player.yBodyRot = Float.parseFloat(a[1]);
				mc.player.setXRot(Float.parseFloat(a[2]));
			}
			case "hud" -> {
				if (mc.gui.hud.isHidden() != a[1].equals("off")) {
					mc.gui.hud.toggle();
				}
			}
			case "screen" -> {
				switch (a[1]) {
					case "wardrobe" -> SkinStudioCommands.openWardrobe(mc);
					case "editor" -> SkinStudioCommands.openEditor(mc, a.length > 2 ? a[2] : null);
					case "packs" -> SkinStudioCommands.openPacks(mc);
					default -> mc.gui.setScreen(null);
				}
			}
			case "key" -> {
				if (mc.gui.screen() != null) {
					mc.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(Integer.parseInt(a[1]), 0, 0));
				}
			}
			case "click" -> {
				if (mc.gui.screen() != null) {
					double sx = Double.parseDouble(a[1]), sy = Double.parseDouble(a[2]);
					var ev = new net.minecraft.client.input.MouseButtonEvent(sx, sy, new net.minecraft.client.input.MouseButtonInfo(0, 0));
					mc.gui.screen().mouseClicked(ev, false);
					mc.gui.screen().mouseReleased(ev);
				}
			}
			case "ui" -> {
				var screen = mc.gui.screen();
				if (screen != null) {
					try {
						screen.getClass().getMethod("autotest", String[].class).invoke(screen, (Object) java.util.Arrays.copyOfRange(a, 1, a.length));
					} catch (ReflectiveOperationException e) {
						LOG.error("UI-Schritt nicht möglich auf {}", screen.getClass().getSimpleName(), e);
					}
				}
			}
			case "import" -> mc.gui.setScreen(new de.behindtheblocks.skinstudio.client.gui.ImportScreen(null,
					java.util.Arrays.stream(a, 1, a.length).map(java.nio.file.Path::of).toList()));
			case "shot" -> {
				Screenshot.grab(mc.gameDirectory, a[1] + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> LOG.info("Screenshot {}", a[1]));
			}
			case "quit" -> mc.stop();
			default -> LOG.warn("Unbekannter Schritt: {}", line);
		}
	}
}
