package de.behindtheblocks.skinstudio.pack;

import de.behindtheblocks.skinstudio.anim.AnimationIO;
import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticJson;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/** Produces the file layout documented in {@code docs/FORMAT.md}. */
public final class PackWriter {
	private PackWriter() {
	}

	/**
	 * Builds the files of one cosmetic. {@code extraFiles} (textures, preview …) are copied as given;
	 * model, animation and cosmetic.json are (re)written from the objects.
	 */
	public static ArchiveFiles cosmeticFiles(CosmeticDefinition def, GeoModel model, Map<String, CosmeticAnimation> animations, ArchiveFiles extraFiles) {
		ArchiveFiles out = ArchiveFiles.empty();
		if (extraFiles != null) {
			for (String p : extraFiles.paths()) {
				out.put(p, extraFiles.get(p));
			}
		}
		if (animations != null && !animations.isEmpty()) {
			if (def.animationFile == null) {
				def.animationFile = "animation.json";
			}
			out.put(def.animationFile, bytes(Json.PRETTY.toJson(AnimationIO.write(animations))));
		} else if (def.animationFile != null && (extraFiles == null || !extraFiles.has(def.animationFile))) {
			def.animationFile = null;
			def.animations.clear();
		}
		out.put(def.model, bytes(Json.PRETTY.toJson(GeometryIO.write(model))));
		out.put(PackReader.COSMETIC_JSON, bytes(Json.PRETTY.toJson(CosmeticJson.write(def))));
		return out;
	}

	public static byte[] singleCosmetic(CosmeticAsset asset) throws IOException {
		return asset.files.toZip();
	}

	public static ArchiveFiles pack(PackManifest manifest, byte[] icon, Collection<CosmeticAsset> cosmetics) {
		ArchiveFiles out = ArchiveFiles.empty();
		if (icon != null) {
			manifest.icon = "pack.png";
			out.put("pack.png", icon);
		}
		out.put(PackReader.PACK_JSON, bytes(Json.PRETTY.toJson(manifest.write())));
		for (CosmeticAsset a : cosmetics) {
			String folder = PackReader.COSMETICS_DIR + folderName(a.id());
			for (String p : a.files.paths()) {
				out.put(folder + "/" + p, a.files.get(p));
			}
		}
		return out;
	}

	public static String folderName(String id) {
		return id.replace(':', '_').replace('/', '_');
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
