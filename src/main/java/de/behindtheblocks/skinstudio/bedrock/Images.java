package de.behindtheblocks.skinstudio.bedrock;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.util.FormatException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/** PNG/TGA helpers for importers (plain Java, runs off the render thread). */
public final class Images {
	private Images() {
	}

	public static BufferedImage read(byte[] data, String name) {
		if (data == null) {
			return null;
		}
		try {
			if (name.toLowerCase(Locale.ROOT).endsWith(".tga")) {
				return Tga.read(data);
			}
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null) {
				throw new FormatException("'" + name + Tr.t("btb.t.ist_kein_lesbares_bild"));
			}
			return toArgb(img);
		} catch (IOException e) {
			throw new FormatException("'" + name + Tr.t("btb.t.ist_beschaedigt") + e.getMessage());
		}
	}

	public static BufferedImage toArgb(BufferedImage img) {
		if (img.getType() == BufferedImage.TYPE_INT_ARGB) {
			return img;
		}
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(img, 0, 0, null);
		return out;
	}

	public static byte[] png(BufferedImage img) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageIO.write(img, "png", bos);
			return bos.toByteArray();
		} catch (IOException e) {
			throw new FormatException(Tr.t("btb.t.png_konnte_nicht_geschrieben_werden") + e.getMessage());
		}
	}

	/** Nearest-neighbour resize (keeps pixel art crisp). */
	public static BufferedImage resize(BufferedImage src, int w, int h) {
		if (src.getWidth() == w && src.getHeight() == h) {
			return src;
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				out.setRGB(x, y, src.getRGB(x * src.getWidth() / w, y * src.getHeight() / h));
			}
		}
		return out;
	}

	/** Minimal TGA reader: uncompressed and RLE true-color (24/32 bit), as used by Persona pieces. */
	static final class Tga {
		static BufferedImage read(byte[] d) {
			if (d.length < 18) {
				throw new FormatException(Tr.t("btb.t.tga_zu_kurz"));
			}
			int idLen = d[0] & 0xFF, type = d[2] & 0xFF;
			int w = (d[12] & 0xFF) | (d[13] & 0xFF) << 8, h = (d[14] & 0xFF) | (d[15] & 0xFF) << 8;
			int bpp = d[16] & 0xFF, desc = d[17] & 0xFF;
			if ((type != 2 && type != 10) || (bpp != 24 && bpp != 32) || w <= 0 || h <= 0 || w > 4096 || h > 4096) {
				throw new FormatException(Tr.t("btb.t.tga_format_nicht_unterstuetzt_typ") + type + ", " + bpp + Tr.t("btb.t.bit"));
			}
			int bytes = bpp / 8;
			int[] px = new int[w * h];
			int p = 18 + idLen, i = 0;
			try {
				while (i < px.length) {
					if (type == 2) {
						px[i++] = pixel(d, p, bytes);
						p += bytes;
					} else {
						int head = d[p++] & 0xFF, count = (head & 0x7F) + 1;
						if ((head & 0x80) != 0) {
							int c = pixel(d, p, bytes);
							p += bytes;
							for (int k = 0; k < count && i < px.length; k++) {
								px[i++] = c;
							}
						} else {
							for (int k = 0; k < count && i < px.length; k++) {
								px[i++] = pixel(d, p, bytes);
								p += bytes;
							}
						}
					}
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				throw new FormatException(Tr.t("btb.t.tga_ist_unvollstaendig"));
			}
			boolean topDown = (desc & 0x20) != 0;
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					img.setRGB(x, topDown ? y : h - 1 - y, px[y * w + x]);
				}
			}
			return img;
		}

		private static int pixel(byte[] d, int p, int bytes) {
			int b = d[p] & 0xFF, g = d[p + 1] & 0xFF, r = d[p + 2] & 0xFF, a = bytes == 4 ? d[p + 3] & 0xFF : 255;
			return a << 24 | r << 16 | g << 8 | b;
		}
	}
}
