package de.behindtheblocks.skinstudio.pack;

import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.geo.GeoModel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully loaded, validated cosmetic: metadata, parsed model and animations plus the raw image bytes.
 * Images are decoded on the client only (texture upload happens on the render thread).
 */
public final class CosmeticAsset {
	public final CosmeticDefinition definition;
	public final GeoModel model;
	public final Map<String, CosmeticAnimation> animations;
	/** Raw files of this cosmetic (cosmetic.json, model, textures …) keyed by relative name. */
	public final ArchiveFiles files;
	/** Id of the pack this cosmetic came from. */
	public final String packId;
	/** SHA-256 over all files: identifies exact content for caching and multiplayer transfer. */
	public final String contentHash;

	public CosmeticAsset(CosmeticDefinition definition, GeoModel model, Map<String, CosmeticAnimation> animations, ArchiveFiles files, String packId) {
		this.definition = definition;
		this.model = model;
		this.animations = animations == null ? new LinkedHashMap<>() : animations;
		this.files = files;
		this.packId = packId;
		this.contentHash = hash(files);
	}

	public String id() {
		return definition.id;
	}

	public byte[] texture() {
		return files.get(definition.texture);
	}

	public byte[] emissive() {
		return definition.emissiveTexture == null ? null : files.get(definition.emissiveTexture);
	}

	public byte[] tintMask() {
		return definition.tintMask == null ? null : files.get(definition.tintMask);
	}

	public byte[] preview() {
		return definition.preview == null ? null : files.get(definition.preview);
	}

	public static String hashOf(ArchiveFiles files) {
		return hash(files);
	}

	static String hash(ArchiveFiles files) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			for (String p : files.paths()) {
				md.update(p.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				md.update((byte) 0);
				md.update(files.get(p));
			}
			return HexFormat.of().formatHex(md.digest()).substring(0, 32);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
