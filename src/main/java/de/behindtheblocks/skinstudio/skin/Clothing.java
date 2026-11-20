package de.behindtheblocks.skinstudio.skin;

import de.behindtheblocks.skinstudio.bedrock.Images;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Character-creator style clothing: single pieces (top, bottom, shoes, jacket) are drawn onto a base skin.
 * A piece is a grey template that takes any colour plus a layer of fixed details (soles, buttons, zips).
 */
public final class Clothing {
	/** Body sections (like Bedrock's Character Creator "Body") and clothing ("Style"). */
	public enum Slot {
		SKIN, HAIR, EYES, MOUTH, BEARD, TOP, BOTTOM, SHOES, JACKET;

		public boolean isBody() {
			return ordinal() <= BEARD.ordinal();
		}
	}

	/** One worn piece: tint template, fixed details (may be null), colour and paint order. */
	public record Layer(byte[] tint, byte[] fixed, int color, int order, Slot slot) {
	}

	private static final int GREY = 226;
	// outer-layer regions of the base that a piece replaces (x, y, w, h)
	private static final int[][] JACKET = {{16, 32, 24, 16}}, SLEEVES = {{40, 32, 16, 16}, {48, 48, 16, 16}};
	private static final int[][] PANTS = {{0, 32, 16, 16}, {0, 48, 16, 16}};

	private Clothing() {
	}

	public static byte[] compose(byte[] basePng, List<Layer> layers) {
		BufferedImage base = Images.read(basePng, "base.png");
		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			// a body layer (own body in a skin tone) replaces the base skin completely
			if (layers.stream().noneMatch(l -> l.slot() == Slot.SKIN)) {
				g.drawImage(base, 0, 0, 64, 64, null);
			}
			g.setComposite(AlphaComposite.Clear);
			for (Layer l : layers) {
				for (int[] r : switch (l.slot()) {
					case TOP -> concat(JACKET, SLEEVES);
					case BOTTOM, SHOES -> PANTS;
					default -> new int[0][];
				}) {
					g.fillRect(r[0], r[1], r[2], r[3]);
				}
			}
			g.setComposite(AlphaComposite.SrcOver);
			List<Layer> sorted = new ArrayList<>(layers);
			sorted.sort(Comparator.comparingInt(Layer::order));
			for (Layer l : sorted) {
				g.drawImage(tint(Images.read(l.tint(), "piece.png"), l.color()), 0, 0, 64, 64, null);
				if (l.fixed() != null) {
					g.drawImage(Images.read(l.fixed(), "piece_fixed.png"), 0, 0, 64, 64, null);
				}
			}
		} finally {
			g.dispose();
		}
		return Images.png(out);
	}

	/** Multiplies the grey template with the colour (grey 226 becomes exactly the colour, shading is kept). */
	static BufferedImage tint(BufferedImage src, int argb) {
		int cr = (argb >> 16) & 0xFF, cg = (argb >> 8) & 0xFF, cb = argb & 0xFF;
		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < Math.min(64, src.getHeight()); y++) {
			for (int x = 0; x < Math.min(64, src.getWidth()); x++) {
				int p = src.getRGB(x, y);
				int a = p >>> 24;
				if (a == 0) {
					continue;
				}
				int v = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
				int r = Math.min(255, cr * v / GREY), gg = Math.min(255, cg * v / GREY), b = Math.min(255, cb * v / GREY);
				out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
			}
		}
		return out;
	}

	private static int[][] concat(int[][] a, int[][] b) {
		int[][] out = new int[a.length + b.length][];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
