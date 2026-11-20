package de.behindtheblocks.skinstudio.skin;

import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A player skin: texture (64×64, 64×32 is converted, HD 128×128 allowed), arm model and optional
 * accessory geometry (from Bedrock skins with extra parts). Built-in Minecraft default skins have no
 * texture bytes; they are referenced by id ({@code vanilla:<name>_<wide|slim>}).
 */
public final class SkinData {
	public enum Source {
		VANILLA, CUSTOM, BEDROCK_PACK, REMOTE
	}

	public final String id;
	public final Map<String, String> names = new LinkedHashMap<>();
	public final boolean slim;
	public final byte[] texture;
	public final GeoModel accessories;
	public final Source source;
	/** Pack name for Bedrock skins, folder for custom skins. */
	public final String group;
	/** Content hash for transfer and caching (empty for vanilla skins). */
	public final String hash;
	/** The files that make up the .btbskin archive (null for vanilla). */
	public final ArchiveFiles files;

	public SkinData(String id, Map<String, String> names, boolean slim, byte[] texture, GeoModel accessories, Source source, String group, ArchiveFiles files) {
		this.id = id;
		this.names.putAll(names);
		this.slim = slim;
		this.texture = texture;
		this.accessories = accessories;
		this.source = source;
		this.group = group == null ? "" : group;
		this.files = files;
		this.hash = files == null ? "" : de.behindtheblocks.skinstudio.pack.CosmeticAsset.hashOf(files);
	}

	public String name(String lang) {
		if (names.isEmpty()) {
			return id;
		}
		String l = lang == null ? "en" : lang.toLowerCase(Locale.ROOT);
		String v = names.get(l.length() > 2 ? l.substring(0, 2) : l);
		if (v == null) {
			v = names.getOrDefault("en", names.values().iterator().next());
		}
		return v;
	}

	public boolean isVanilla() {
		return source == Source.VANILLA;
	}
}
