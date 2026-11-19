package de.behindtheblocks.skinstudio.bedrock;

import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts Persona texture sources (clothing painted on the body) into the standard 64×64 skin layout
 * and provides outer-layer boxes that carry them.
 *
 * <p>The box mapping for 128×128 Persona body sheets (medium/wide variant) follows the open-source mod
 * Bedrock Skins by BrandonItaly (MIT License, © 2025), see {@code docs/THIRD_PARTY.md}.
 */
final class PersonaLayout {
	private PersonaLayout() {
	}

	static void draw(BufferedImage target, BufferedImage src, boolean face) {
		Graphics2D g = target.createGraphics();
		try {
			if (face) {
				if (src.getWidth() < 32 || src.getHeight() < 32) {
					g.drawImage(src, 0, 0, null);
					return;
				}
				box(g, src, 0, 0, 0, 0, 8, 8, 8);    // head
				box(g, src, 0, 16, 32, 0, 8, 8, 8);  // hat
				return;
			}
			if (src.getWidth() < 128 || src.getHeight() < 128) {
				g.drawImage(Images.resize(src, 64, 64), 0, 0, null);
				return;
			}
			box(g, src, 16, 0, 16, 16, 8, 12, 4);   // body
			box(g, src, 16, 16, 16, 32, 8, 12, 4);  // jacket
			box(g, src, 0, 0, 40, 16, 4, 12, 4);    // right arm
			box(g, src, 0, 16, 40, 32, 4, 12, 4);   // right sleeve
			box(g, src, 40, 0, 32, 48, 4, 12, 4);   // left arm
			box(g, src, 40, 16, 48, 48, 4, 12, 4);  // left sleeve
			box(g, src, 16, 32, 0, 16, 4, 12, 4);   // right leg
			box(g, src, 0, 32, 0, 32, 4, 12, 4);    // right pants
			box(g, src, 32, 32, 16, 48, 4, 12, 4);  // left leg
			box(g, src, 48, 32, 0, 48, 4, 12, 4);   // left pants
		} finally {
			g.dispose();
		}
	}

	private static void box(Graphics2D g, BufferedImage src, int su, int sv, int tu, int tv, int w, int h, int d) {
		int bw = 2 * (w + d), bh = d + h;
		g.drawImage(src, tu, tv, tu + bw, tv + bh, su, sv, su + bw, sv + bh, null);
	}

	/** Outer-layer boxes of the classic (wide) player using the 64×64 layout, slightly inflated over the skin. */
	static List<GeoBone> overlayBones(boolean body, boolean face) {
		List<GeoBone> out = new ArrayList<>();
		if (face) {
			GeoBone head = bone("head", 0, 24, 0);
			head.cubes.add(cube(-4, 24, -4, 8, 8, 8, 0, 0, 0.05f));
			head.cubes.add(cube(-4, 24, -4, 8, 8, 8, 32, 0, 0.55f));
			out.add(head);
		}
		if (body) {
			GeoBone b = bone("body", 0, 24, 0);
			b.cubes.add(cube(-4, 12, -2, 8, 12, 4, 16, 16, 0.05f));
			b.cubes.add(cube(-4, 12, -2, 8, 12, 4, 16, 32, 0.3f));
			out.add(b);
			GeoBone ra = bone("rightArm", -5, 22, 0);
			ra.cubes.add(cube(-8, 12, -2, 4, 12, 4, 40, 16, 0.05f));
			ra.cubes.add(cube(-8, 12, -2, 4, 12, 4, 40, 32, 0.3f));
			out.add(ra);
			GeoBone la = bone("leftArm", 5, 22, 0);
			la.cubes.add(cube(4, 12, -2, 4, 12, 4, 32, 48, 0.05f));
			la.cubes.add(cube(4, 12, -2, 4, 12, 4, 48, 48, 0.3f));
			out.add(la);
			GeoBone rl = bone("rightLeg", -1.9f, 12, 0);
			rl.cubes.add(cube(-3.9f, 0, -2, 4, 12, 4, 0, 16, 0.05f));
			rl.cubes.add(cube(-3.9f, 0, -2, 4, 12, 4, 0, 32, 0.3f));
			out.add(rl);
			GeoBone ll = bone("leftLeg", 1.9f, 12, 0);
			ll.cubes.add(cube(-0.1f, 0, -2, 4, 12, 4, 16, 48, 0.05f));
			ll.cubes.add(cube(-0.1f, 0, -2, 4, 12, 4, 0, 48, 0.3f));
			out.add(ll);
		}
		return out;
	}

	private static GeoBone bone(String name, float x, float y, float z) {
		GeoBone b = new GeoBone(name);
		b.pivot = new float[]{x, y, z};
		return b;
	}

	private static GeoCube cube(float x, float y, float z, float w, float h, float d, float u, float v, float inflate) {
		GeoCube c = GeoCube.box(x, y, z, w, h, d, u, v);
		c.inflate = inflate;
		return c;
	}
}
