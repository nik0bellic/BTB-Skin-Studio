package de.behindtheblocks.skinstudio.pack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Result of reading a pack: always returned, even when broken, so the UI can explain what is wrong. */
public final class LoadedPack {
	public enum Origin {
		BUILTIN, INSTALLED, CUSTOM, PREVIEW
	}

	public final PackManifest manifest;
	public final List<CosmeticAsset> cosmetics = new ArrayList<>();
	public final List<PackProblem> problems = new ArrayList<>();
	public byte[] icon;
	public Path source;
	public Origin origin = Origin.PREVIEW;
	/** False if the pack itself (not just single cosmetics) is unusable. */
	public boolean usable = true;

	public LoadedPack(PackManifest manifest) {
		this.manifest = manifest;
	}

	public String id() {
		return manifest == null ? "?" : manifest.id;
	}

	public long errorCount() {
		return problems.stream().filter(p -> p.severity() == PackProblem.Severity.ERROR).count();
	}

	public long warningCount() {
		return problems.stream().filter(p -> p.severity() == PackProblem.Severity.WARNING).count();
	}
}
