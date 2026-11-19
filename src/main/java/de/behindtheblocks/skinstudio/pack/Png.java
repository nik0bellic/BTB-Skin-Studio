package de.behindtheblocks.skinstudio.pack;

import de.behindtheblocks.skinstudio.util.FormatException;

/** Header check for PNG files without decoding (works on the server and in tests). */
public final class Png {
	public static final int MAX_SIZE = 2048;
	private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

	private Png() {
	}

	public record Info(int width, int height) {
	}

	public static boolean isPng(byte[] data) {
		if (data == null || data.length < 24) {
			return false;
		}
		for (int i = 0; i < SIGNATURE.length; i++) {
			if (data[i] != SIGNATURE[i]) {
				return false;
			}
		}
		return true;
	}

	public static Info info(byte[] data, String name) {
		if (!isPng(data)) {
			throw new FormatException("'" + name + "' ist keine PNG-Datei");
		}
		int w = readInt(data, 16), h = readInt(data, 20);
		if (w <= 0 || h <= 0 || w > MAX_SIZE || h > MAX_SIZE) {
			throw new FormatException("'" + name + "' hat eine ungültige Größe " + w + "×" + h + " (max. " + MAX_SIZE + ")");
		}
		return new Info(w, h);
	}

	private static int readInt(byte[] d, int o) {
		return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
	}
}
