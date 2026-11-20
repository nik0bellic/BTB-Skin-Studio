package de.behindtheblocks.skinstudio.bedrock;

import de.behindtheblocks.skinstudio.pack.ArchiveFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds a local Minecraft Bedrock installation and the skins/cosmetics inside it:
 * Windows (Microsoft Store/UWP and the newer GDK build, incl. Preview), the Linux launcher
 * (mcpelauncher, also as Flatpak) and any folder chosen by the user. Only reads; never modifies Bedrock files.
 */
public final class BedrockLocator {
	public enum Kind {
		SKIN_PACK, PERSONA, SKIN_PNG, RESOURCE
	}

	/** One thing found: what it is, where, a readable label and whether it is encrypted (Marketplace). */
	public record Found(Kind kind, Path path, String label, boolean encrypted) {
	}

	private BedrockLocator() {
	}

	/** com.mojang folders of installed Bedrock editions on this computer. */
	public static List<Path> installations() {
		Set<Path> out = new LinkedHashSet<>();
		String local = System.getenv("LOCALAPPDATA");
		String roaming = System.getenv("APPDATA");
		String home = System.getProperty("user.home", "");
		if (local != null) {
			for (String pkg : new String[]{"Microsoft.MinecraftUWP_8wekyb3d8bbwe", "Microsoft.MinecraftWindowsBeta_8wekyb3d8bbwe"}) {
				addIfDir(out, Path.of(local, "Packages", pkg, "LocalState", "games", "com.mojang"));
			}
		}
		if (roaming != null) {
			for (String edition : new String[]{"Minecraft Bedrock", "Minecraft Bedrock Preview"}) {
				Path users = Path.of(roaming, edition, "Users");
				for (Path user : list(users)) {
					addIfDir(out, user.resolve("games").resolve("com.mojang"));
				}
			}
		}
		if (!home.isEmpty()) {
			addIfDir(out, Path.of(home, ".local", "share", "mcpelauncher", "games", "com.mojang"));
			addIfDir(out, Path.of(home, ".var", "app", "io.mrarm.mcpelauncher", "data", "mcpelauncher", "games", "com.mojang"));
		}
		return new ArrayList<>(out);
	}

	/**
	 * Folders holding the skin packs that ship with an installed Bedrock edition ({@code data/skin_packs} of the
	 * game files, e.g. Villains, Story Mode or Battle &amp; Beasts). They are unencrypted game files of the user's
	 * own installation; BTB Skin Studio only reads them and never copies or redistributes them.
	 */
	public static List<Path> builtInSkinPackDirs() {
		Set<Path> out = new LinkedHashSet<>();
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			// GDK build (Microsoft Store / Xbox app), installed into <drive>:\XboxGames by default
			for (java.io.File root : java.io.File.listRoots()) {
				String r = root.getPath().toUpperCase(Locale.ROOT);
				if (r.startsWith("A:") || r.startsWith("B:")) {
					continue;
				}
				for (String edition : new String[]{"Minecraft for Windows", "Minecraft Preview for Windows"}) {
					addIfDir(out, root.toPath().resolve("XboxGames").resolve(edition).resolve("Content").resolve("data").resolve("skin_packs"));
				}
			}
			// older UWP build – WindowsApps is usually not listable, then this simply finds nothing
			String pf = System.getenv("ProgramFiles");
			if (pf != null) {
				for (Path p : list(Path.of(pf, "WindowsApps"))) {
					String n = p.getFileName().toString();
					if (n.startsWith("Microsoft.MinecraftUWP_") || n.startsWith("Microsoft.MinecraftWindowsBeta_")) {
						addIfDir(out, p.resolve("data").resolve("skin_packs"));
					}
				}
			}
		}
		String home = System.getProperty("user.home", "");
		if (!home.isEmpty()) {
			// Linux launcher: every installed version keeps the extracted game assets
			for (Path base : new Path[]{Path.of(home, ".local", "share", "mcpelauncher", "versions"),
					Path.of(home, ".var", "app", "io.mrarm.mcpelauncher", "data", "mcpelauncher", "versions")}) {
				for (Path v : list(base)) {
					addIfDir(out, v.resolve("assets").resolve("skin_packs"));
					addIfDir(out, v.resolve("assets").resolve("assets").resolve("skin_packs"));
				}
			}
		}
		return new ArrayList<>(out);
	}

	/** Pack folders (with skins.json) inside one {@link #builtInSkinPackDirs()} folder. */
	public static List<Path> skinPackFolders(Path dir) {
		List<Path> out = new ArrayList<>();
		for (Path p : list(dir)) {
			if (Files.isDirectory(p) && Files.exists(p.resolve("skins.json"))) {
				out.add(p);
			}
		}
		return out;
	}

	/** Folders inside a {@link #builtInSkinPackDirs()} folder that hold Character Creator pieces (*.meta.json). */
	public static List<Path> personaFolders(Path dir) {
		List<Path> out = new ArrayList<>();
		for (Path p : list(dir)) {
			if (!Files.isDirectory(p) || Files.exists(p.resolve("skins.json"))) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(p, 4)) {
				if (walk.anyMatch(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".meta.json"))) {
					out.add(p);
				}
			} catch (IOException | RuntimeException ignored) {
				// unreadable folders are skipped
			}
		}
		return out;
	}

	/** Scans one com.mojang folder (or any folder) for importable content. */
	public static List<Found> scan(Path root) {
		List<Found> out = new ArrayList<>();
		for (String sub : new String[]{"skin_packs", "premium_cache/skin_packs", "development_skin_packs"}) {
			for (Path p : list(root.resolve(sub))) {
				if (Files.isDirectory(p) && Files.exists(p.resolve("skins.json"))) {
					out.add(new Found(Kind.SKIN_PACK, p, label(p), encrypted(p)));
				}
			}
		}
		if (Files.exists(root.resolve("skins.json"))) {
			out.add(new Found(Kind.SKIN_PACK, root, label(root), encrypted(root)));
		}
		// Character Creator pieces and loose skin PNGs (limited depth, never follows into worlds).
		// Textures inside a skin pack that was already found belong to that pack and are not listed again.
		List<Path> packs = out.stream().filter(f -> f.kind() == Kind.SKIN_PACK).map(Found::path).toList();
		try (Stream<Path> walk = Files.walk(root, 7)) {
			walk.filter(p -> !p.toString().contains("minecraftWorlds"))
					.filter(p -> packs.stream().noneMatch(p::startsWith)).forEach(p -> {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (n.endsWith(".meta.json")) {
					out.add(new Found(Kind.PERSONA, p.getParent(), label(p.getParent()), false));
				} else if (n.endsWith(".png") && (n.contains("skin") || n.equals("custom.png")) && isSkinSized(p)) {
					out.add(new Found(Kind.SKIN_PNG, p, p.getFileName().toString(), false));
				}
			});
		} catch (IOException | RuntimeException ignored) {
			// unreadable folders are skipped
		}
		return dedupe(out);
	}

	private static List<Found> dedupe(List<Found> in) {
		List<Found> out = new ArrayList<>();
		Set<Path> seen = new LinkedHashSet<>();
		for (Found f : in) {
			if (seen.add(f.path())) {
				out.add(f);
			}
		}
		return out;
	}

	private static boolean encrypted(Path dir) {
		try {
			return BedrockImporter.isEncrypted(ArchiveFiles.readDirectory(dir));
		} catch (Exception e) {
			return true;
		}
	}

	private static boolean isSkinSized(Path png) {
		try {
			byte[] head = new byte[24];
			try (var in = Files.newInputStream(png)) {
				if (in.readNBytes(head, 0, 24) < 24) {
					return false;
				}
			}
			int w = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
			int h = ((head[20] & 0xFF) << 24) | ((head[21] & 0xFF) << 16) | ((head[22] & 0xFF) << 8) | (head[23] & 0xFF);
			return w % 64 == 0 && w <= 512 && (w == h || w == h * 2);
		} catch (IOException e) {
			return false;
		}
	}

	private static String label(Path p) {
		return p.getFileName() == null ? p.toString() : p.getFileName().toString();
	}

	private static void addIfDir(Set<Path> out, Path p) {
		if (Files.isDirectory(p)) {
			out.add(p);
		}
	}

	private static List<Path> list(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.list(dir)) {
			return s.sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}
}
