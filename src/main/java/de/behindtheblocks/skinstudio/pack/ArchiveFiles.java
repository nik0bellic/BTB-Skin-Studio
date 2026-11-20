package de.behindtheblocks.skinstudio.pack;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.util.FormatException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * An in-memory file tree read from a ZIP archive or a folder, with hard limits.
 * Packs are always inspected this way first, so a preview never writes anything to disk
 * and a hostile archive (zip bomb, path traversal) cannot escape.
 */
public final class ArchiveFiles {
	public static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
	public static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
	public static final int MAX_ENTRIES = 4096;

	/** Normalized relative path ("a/b/c.png") → content. Case preserved, lookups case-insensitive. */
	private final Map<String, byte[]> files = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	public static ArchiveFiles empty() {
		return new ArchiveFiles();
	}

	public static ArchiveFiles read(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			return readDirectory(path);
		}
		try (InputStream in = Files.newInputStream(path)) {
			return readZip(in);
		}
	}

	public static ArchiveFiles readZip(byte[] bytes) throws IOException {
		return readZip(new ByteArrayInputStream(bytes));
	}

	public static ArchiveFiles readZip(InputStream raw) throws IOException {
		ArchiveFiles out = new ArchiveFiles();
		long total = 0;
		int entries = 0;
		try (ZipInputStream zip = new ZipInputStream(raw)) {
			ZipEntry e;
			byte[] buf = new byte[16384];
			while ((e = zip.getNextEntry()) != null) {
				if (++entries > MAX_ENTRIES) {
					throw new FormatException(Tr.t("btb.t.archiv_enthaelt_zu_viele_dateien_max") + MAX_ENTRIES + ")");
				}
				if (e.isDirectory()) {
					continue;
				}
				String name = normalize(e.getName());
				if (name == null) {
					continue;
				}
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				int n;
				long size = 0;
				while ((n = zip.read(buf)) > 0) {
					size += n;
					total += n;
					if (size > MAX_FILE_BYTES) {
						throw new FormatException(Tr.t("btb.t.datei") + name + Tr.t("btb.t.ist_zu_gross_max_16_mb"));
					}
					if (total > MAX_TOTAL_BYTES) {
						throw new FormatException(Tr.t("btb.t.archiv_ist_entpackt_zu_gross_max_64_mb"));
					}
					bos.write(buf, 0, n);
				}
				out.files.put(name, bos.toByteArray());
			}
		} catch (IllegalArgumentException e) {
			throw new FormatException(Tr.t("btb.t.archiv_ist_beschaedigt_oder_kein_zip") + e.getMessage());
		}
		if (out.files.isEmpty()) {
			throw new FormatException(Tr.t("btb.t.archiv_ist_leer_oder_kein_gueltiges_zip"));
		}
		return out;
	}

	public static ArchiveFiles readDirectory(Path dir) throws IOException {
		ArchiveFiles out = new ArchiveFiles();
		long total = 0;
		try (Stream<Path> walk = Files.walk(dir, 12)) {
			List<Path> paths = walk.filter(Files::isRegularFile).toList();
			if (paths.size() > MAX_ENTRIES) {
				throw new FormatException(Tr.t("btb.t.ordner_enthaelt_zu_viele_dateien_max") + MAX_ENTRIES + ")");
			}
			for (Path p : paths) {
				long size = Files.size(p);
				total += size;
				if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
					throw new FormatException(Tr.t("btb.t.ordner_ist_zu_gross") + dir.relativize(p));
				}
				String name = normalize(dir.relativize(p).toString().replace('\\', '/'));
				if (name != null) {
					out.files.put(name, Files.readAllBytes(p));
				}
			}
		}
		return out;
	}

	/** Rejects absolute paths, "..", and OS metadata files. */
	static String normalize(String raw) {
		String n = raw.replace('\\', '/');
		while (n.startsWith("./")) {
			n = n.substring(2);
		}
		if (n.startsWith("/") || n.contains("../") || n.equals("..") || n.isBlank() || n.contains(":")) {
			return null;
		}
		String lower = n.toLowerCase(Locale.ROOT);
		if (lower.startsWith("__macosx/") || lower.endsWith(".ds_store") || lower.endsWith("thumbs.db")) {
			return null;
		}
		return n;
	}

	public byte[] get(String path) {
		return files.get(path);
	}

	public boolean has(String path) {
		return files.containsKey(path);
	}

	public void put(String path, byte[] data) {
		String n = normalize(path);
		if (n == null) {
			throw new FormatException(Tr.t("btb.t.ungueltiger_pfad") + path);
		}
		files.put(n, data);
	}

	public java.util.Set<String> paths() {
		return files.keySet();
	}

	public int size() {
		return files.size();
	}

	public long totalBytes() {
		long t = 0;
		for (byte[] b : files.values()) {
			t += b.length;
		}
		return t;
	}

	/** Files below {@code prefix/} as a new tree with the prefix removed. */
	public ArchiveFiles sub(String prefix) {
		String p = prefix.isEmpty() || prefix.endsWith("/") ? prefix : prefix + "/";
		ArchiveFiles out = new ArchiveFiles();
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			if (e.getKey().regionMatches(true, 0, p, 0, p.length())) {
				out.files.put(e.getKey().substring(p.length()), e.getValue());
			}
		}
		return out;
	}

	/** If everything sits inside a single top-level folder (common for zipped folders), strips it. */
	public ArchiveFiles unwrapSingleFolder() {
		String common = null;
		for (String k : files.keySet()) {
			int i = k.indexOf('/');
			if (i < 0) {
				return this;
			}
			String top = k.substring(0, i);
			if (common == null) {
				common = top;
			} else if (!common.equalsIgnoreCase(top)) {
				return this;
			}
		}
		return common == null ? this : sub(common);
	}

	public byte[] toZip() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bos)) {
			for (Map.Entry<String, byte[]> e : files.entrySet()) {
				ZipEntry entry = new ZipEntry(e.getKey());
				entry.setTime(0L);
				zip.putNextEntry(entry);
				zip.write(e.getValue());
				zip.closeEntry();
			}
		}
		return bos.toByteArray();
	}

	public List<String> find(java.util.function.Predicate<String> filter) {
		List<String> out = new ArrayList<>();
		for (String k : files.keySet()) {
			if (filter.test(k)) {
				out.add(k);
			}
		}
		return out;
	}
}
