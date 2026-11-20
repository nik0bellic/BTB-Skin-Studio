package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.bedrock.Images;
import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.skin.SkinData;
import de.behindtheblocks.skinstudio.skin.SkinIO;
import de.behindtheblocks.skinstudio.skin.SkinLayers3D;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Tr;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkinSystemTest {
	static BufferedImage skin(int w, int h, boolean slim) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < Math.min(h, 16); y++) {
			for (int x = 0; x < 32; x++) {
				img.setRGB(x, y, 0xFF8B5A3C); // head
			}
		}
		for (int y = 16; y < Math.min(h, 32); y++) {
			for (int x = 0; x < (slim ? 54 : 56); x++) {
				img.setRGB(x, y, 0xFF3A6EA5); // body/legs/arm (slim leaves x 54–55 empty)
			}
		}
		for (int x = 40; x < 48; x++) { // a few hat pixels on the front of the head layer
			img.setRGB(x, 10, 0xFFFFD700);
		}
		return img;
	}

	@Test
	void pngSkinsAndModelDetection() {
		SkinData wide = SkinIO.fromPng(Images.png(skin(64, 64, false)), "Held.png", null);
		assertFalse(wide.slim);
		assertEquals("skin:held", wide.id);
		SkinData slim = SkinIO.fromPng(Images.png(skin(64, 64, true)), "Alex Style.png", null);
		assertTrue(slim.slim);
		SkinData legacy = SkinIO.fromPng(Images.png(skin(64, 32, false)), "old.png", null);
		BufferedImage converted = Images.read(legacy.texture, "x.png");
		assertEquals(64, converted.getHeight(), "64×32 skins are converted to 64×64");
		assertEquals(converted.getRGB(4, 20), converted.getRGB(20 + 3, 52), "left leg is a mirrored copy of the right leg");
		assertThrows(FormatException.class, () -> SkinIO.fromPng(Images.png(new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)), "bad.png", null));
		// .btbskin round trip keeps everything and the content hash
		SkinData back = SkinIO.read(wide.files, SkinData.Source.REMOTE, "");
		assertEquals(wide.hash, back.hash);
		assertEquals(wide.id, back.id);
	}

	@Test
	void bedrockSkinPackWithAccessories() {
		ArchiveFiles f = ArchiveFiles.empty();
		f.put("skins.json", """
				{ "serialize_name": "Knights", "localization_name": "knights",
				  "skins": [ { "localization_name": "red", "geometry": "geometry.knights.red", "texture": "red.png", "type": "free" },
				             { "localization_name": "slimmy", "geometry": "geometry.humanoid.customSlim", "texture": "slim.png", "type": "free" } ] }"""
				.getBytes(StandardCharsets.UTF_8));
		f.put("geometry.json", """
				{ "format_version": "1.12.0", "minecraft:geometry": [ { "description": { "identifier": "geometry.knights.red", "texture_width": 64, "texture_height": 64 },
				  "bones": [ { "name": "head", "pivot": [0, 24, 0], "cubes": [ { "origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0] },
				                                                             { "origin": [-1, 32, -1], "size": [2, 3, 2], "uv": [56, 0] } ] } ] } ] }"""
				.getBytes(StandardCharsets.UTF_8));
		f.put("red.png", Images.png(skin(64, 64, false)));
		f.put("slim.png", Images.png(skin(64, 64, true)));
		f.put("texts/en_US.lang", "skin.knights.red=Red Knight\nskinpack.knights=Knights\n".getBytes(StandardCharsets.UTF_8));
		List<PackProblem> problems = new ArrayList<>();
		List<SkinData> skins = SkinIO.readBedrockPack(f, "knights", problems);
		assertEquals(2, skins.size(), problems.toString());
		SkinData red = skins.getFirst();
		assertEquals("Red Knight", red.name("en"));
		assertEquals("Knights", red.group);
		assertNotNull(red.accessories, "the plume cube becomes accessory geometry");
		assertEquals(1, red.accessories.cubeCount());
		assertTrue(skins.get(1).slim, "customSlim geometry means slim arms");
	}

	@Test
	void layers3dOnlyForVisiblePixels() {
		GeoModel m = SkinLayers3D.build(skin(64, 64, false), false);
		assertEquals(8, m.cubeCount(), "8 hat pixels → 8 voxels");
		BakedModel b = BakedModel.bake(m);
		assertNotNull(b.bone("head"));
		// a lone row of pixels has an outward face, two end faces and top/bottom faces, inner faces are culled
		assertTrue(b.quadCount < 8 * 6, "neighbouring voxels share no inner faces");
	}

	@Test
	void translationsExistInBothLanguages() {
		Tr.setLanguage("de_de");
		assertEquals("Garderobe", Tr.t("btb.ui.wardrobe"));
		assertEquals("Getragen (3)", Tr.t("btb.ui.worn", 3));
		Tr.setLanguage("en_us");
		assertEquals("Wardrobe", Tr.t("btb.ui.wardrobe"));
		assertEquals("missing.key", Tr.t("missing.key"));
	}

	@Test
	void adjustmentsAreClampedAndSaved() {
		Loadout.Adjust a = new Loadout.Adjust(100, -2, 0, 50).clamped();
		assertEquals(Loadout.Adjust.MAX_OFFSET, a.x());
		assertEquals(Loadout.Adjust.MAX_SCALE, a.scale());
		Loadout l = new Loadout(List.of(new Loadout.Entry("btb:halo", 0, "", new Loadout.Adjust(1, 2.5f, 0, 1.2f))));
		Loadout back = Loadout.fromJson(l.toJson());
		assertEquals(2.5f, back.find("btb:halo").adjust().y(), 1e-4);
		assertEquals(1.2f, back.find("btb:halo").adjust().scale(), 1e-4);
	}

	@Test
	void ownSkinPackRoundTrip() throws Exception {
		SkinData a = SkinIO.fromPng(Images.png(skin(64, 64, false)), "Held.png", null);
		SkinData b = SkinIO.fromPng(Images.png(skin(64, 64, true)), "Held.png", null); // same name -> unique key
		ArchiveFiles pack = de.behindtheblocks.skinstudio.skin.SkinPackWriter.write("Meine Skins", List.of(a, b));
		assertTrue(pack.has("manifest.json"));
		assertTrue(pack.has("texts/de_DE.lang"));
		// works as .mcpack zip and is read back by the same loader as Bedrock packs
		ArchiveFiles zipped = ArchiveFiles.readZip(pack.toZip());
		List<PackProblem> problems = new ArrayList<>();
		List<SkinData> back = SkinIO.readBedrockPack(zipped, "x", problems);
		assertEquals(2, back.size(), problems.toString());
		assertEquals("bedrock:meine_skins/held", back.get(0).id);
		assertEquals("bedrock:meine_skins/held_2", back.get(1).id);
		assertFalse(back.get(0).slim);
		assertTrue(back.get(1).slim);
		assertEquals("Meine Skins", back.get(0).group);
	}

	@Test
	void blinkClosesTheEyeRowOnly() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		int skinTone = 0xFFC69C7A;
		for (int y = 8; y < 16; y++) {
			for (int x = 8; x < 16; x++) {
				img.setRGB(x, y, skinTone);
			}
		}
		// Steve-like eyes on row 4 of the face: white outside, blue inside
		img.setRGB(9, 12, 0xFFFFFFFF);
		img.setRGB(10, 12, 0xFF3050C0);
		img.setRGB(13, 12, 0xFF3050C0);
		img.setRGB(14, 12, 0xFFFFFFFF);
		img.setRGB(12, 14, 0xFF6A3030); // mouth stays
		BufferedImage closed = de.behindtheblocks.skinstudio.skin.SkinBlink.close(img);
		assertNotNull(closed);
		for (int x = 8; x < 16; x++) {
			int c = closed.getRGB(x, 12);
			assertTrue(Math.abs(((c >> 16) & 0xFF) - 0xC6) < 50, "eye pixel " + x + " is lid now");
		}
		assertEquals(0xFF6A3030, closed.getRGB(12, 14));
		assertEquals(0xFFFFFFFF, img.getRGB(9, 12), "original untouched");
	}

	@Test
	void layers3dRespectsBodyParts() {
		BufferedImage img = skin(64, 64, false);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				img.setRGB(x, y, 0xFF808080);
			}
		}
		GeoModel all = SkinLayers3D.build(img, false, SkinLayers3D.ALL);
		GeoModel head = SkinLayers3D.build(img, false, SkinLayers3D.HEAD);
		assertEquals(6, all.bones.size());
		assertEquals(1, head.bones.size());
		assertEquals("head", head.bones.getFirst().name);
		assertEquals(List.of("hat", "sleeves"), SkinLayers3D.hiddenLayers(SkinLayers3D.HEAD | SkinLayers3D.ARMS));
		// voxels are thinner than a full pixel
		for (var c : head.bones.getFirst().cubes) {
			float min = Math.min(c.size[0], Math.min(c.size[1], c.size[2]));
			assertTrue(min <= 0.75f + 1e-4, "voxel depth " + min);
		}
	}
}
