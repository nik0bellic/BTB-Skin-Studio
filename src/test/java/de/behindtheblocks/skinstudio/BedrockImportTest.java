package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.bedrock.BedrockImporter;
import de.behindtheblocks.skinstudio.bedrock.Images;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackReader;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BedrockImportTest {
	static byte[] png(int w, int h, int argb) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setRGB(x, y, argb);
			}
		}
		return Images.png(img);
	}

	static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void personaPieceWithTintMap() {
		ArchiveFiles f = ArchiveFiles.empty();
		f.put("persona/wizard_hat/wizard_hat.meta.json", utf8("""
				{ "piece_id": "abc-123", "piece_name": "wizard_hat", "piece_type": "persona_head",
				  "geometry_sources": [
				    { "geometry": "geometry.persona.wizard_hat", "texture": "wizard_hat", "tint_map": "wizard_hat_tint", "body_size": "medium", "arm_size": "wide" },
				    { "geometry": "geometry.persona.wizard_hat.slim", "texture": "wizard_hat", "body_size": "medium", "arm_size": "slim" } ],
				  "tint_color": { "r_color": "#ff3355aa" } }"""));
		f.put("persona/wizard_hat/wizard_hat.geometry.json", utf8("""
				{ "format_version": "1.12.0", "minecraft:geometry": [
				  { "description": { "identifier": "geometry.persona.wizard_hat", "texture_width": 32, "texture_height": 32 },
				    "bones": [ { "name": "head", "pivot": [0, 24, 0], "cubes": [ { "origin": [-4, 32, -4], "size": [8, 2, 8], "uv": [0, 0] } ] },
				               { "name": "tip", "parent": "head", "pivot": [0, 34, 0], "cubes": [ { "origin": [-2, 34, -2], "size": [4, 6, 4], "uv": [0, 10] } ] } ] },
				  { "description": { "identifier": "geometry.persona.wizard_hat.slim", "texture_width": 32, "texture_height": 32 }, "bones": [] } ] }"""));
		f.put("persona/wizard_hat/wizard_hat.png", png(64, 64, 0xFF4433AA));
		f.put("persona/wizard_hat/wizard_hat_tint.png", png(32, 32, 0xFFFFFFFF));
		f.put("persona/wizard_hat/texts/en_US.lang", utf8("wizard_hat=Wizard Hat\n"));
		BedrockImporter.Result r = BedrockImporter.importFiles(f, "test");
		assertEquals(1, r.cosmetics.size(), r.problems.toString());
		CosmeticAsset a = r.cosmetics.getFirst();
		assertEquals(CosmeticType.HAT, a.definition.type);
		assertTrue(a.definition.tint.enabled, "tint map becomes a tint mask");
		assertEquals(0xFF3355AA, a.definition.tint.defaultColor);
		assertEquals("Wizard Hat", a.definition.name("en"));
		assertEquals(2, a.model.bones.size());
		assertEquals(32, a.model.textureWidth, "64px texture resampled to the declared 32px geometry size");
		BakedModel baked = BakedModel.bake(a.model);
		assertNotNull(baked.bone("head"), "bones named like vanilla parts follow that part");
	}

	@Test
	void skinPackAccessoriesAreExtracted() {
		ArchiveFiles f = ArchiveFiles.empty();
		f.put("skins.json", utf8("""
				{ "serialize_name": "Monsters", "localization_name": "monsters",
				  "skins": [ { "localization_name": "horned", "geometry": "geometry.monsters.horned", "texture": "horned.png", "type": "free" },
				             { "localization_name": "plain", "geometry": "geometry.humanoid.custom", "texture": "plain.png", "type": "free" } ] }"""));
		f.put("geometry.json", utf8("""
				{ "format_version": "1.8.0",
				  "geometry.monsters.horned": { "texturewidth": 64, "textureheight": 64, "bones": [
				    { "name": "head", "pivot": [0, 24, 0], "cubes": [ { "origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0] } ] },
				    { "name": "body", "pivot": [0, 24, 0], "cubes": [ { "origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16] } ] },
				    { "name": "horn", "parent": "head", "pivot": [3, 32, 0], "cubes": [ { "origin": [2, 32, -1], "size": [2, 4, 2], "uv": [56, 0] } ] },
				    { "name": "hornTip", "parent": "horn", "pivot": [3, 36, 0], "cubes": [ { "origin": [2.5, 36, -0.5], "size": [1, 2, 1], "uv": [56, 8] } ] } ] } }"""));
		f.put("horned.png", png(64, 64, 0xFF884422));
		f.put("plain.png", png(64, 64, 0xFF884422));
		f.put("texts/en_US.lang", utf8("skin.monsters.horned=Horned One\n"));
		BedrockImporter.Result r = BedrockImporter.importFiles(f, "Monsters");
		assertEquals(1, r.cosmetics.size(), r.problems.toString());
		CosmeticAsset a = r.cosmetics.getFirst();
		assertTrue(a.definition.name("en").startsWith("Horned One"));
		assertEquals(1, r.skippedSkins, "plain skin without extra bones is skipped");
		// wrapper bone "head" (no cubes) + horn + hornTip, the skin's own head/body cubes are gone
		assertEquals(3, a.model.bones.size());
		assertEquals(2, a.model.cubeCount());
		BakedModel baked = BakedModel.bake(a.model);
		assertEquals(1, baked.roots.size());
		assertEquals("head", baked.roots.getFirst().name);
	}

	@Test
	void encryptedMarketplaceContentIsRefused() {
		ArchiveFiles f = ArchiveFiles.empty();
		byte[] garbage = new byte[256];
		for (int i = 0; i < garbage.length; i++) {
			garbage[i] = (byte) (i * 37 + 11);
		}
		f.put("contents.json", garbage);
		f.put("skins.json", garbage);
		f.put("manifest.json", utf8("{ \"format_version\": 1 }"));
		BedrockImporter.Result r = BedrockImporter.importFiles(f, "marketplace");
		assertTrue(r.protectedContent);
		assertTrue(r.cosmetics.isEmpty());
		assertTrue(r.problems.getFirst().message().contains("verschlüsselt"));
	}

	@Test
	void blockbenchModelWithAnimation() {
		ArchiveFiles f = ArchiveFiles.empty();
		f.put("propeller_hat.geo.json", utf8("""
				{ "format_version": "1.12.0", "minecraft:geometry": [ { "description": { "identifier": "geometry.propeller_hat", "texture_width": 16, "texture_height": 16 },
				  "bones": [ { "name": "hat", "pivot": [0, 0, 0], "cubes": [ { "origin": [-3, 0, -3], "size": [6, 2, 6], "uv": [0, 0] } ] },
				             { "name": "propeller", "parent": "hat", "pivot": [0, 3, 0], "cubes": [ { "origin": [-4, 3, -0.5], "size": [8, 0, 1], "uv": [0, 8] } ] } ] } ] }"""));
		f.put("propeller_hat.png", png(16, 16, 0xFFFF0000));
		f.put("propeller_hat.animation.json", utf8("""
				{ "format_version": "1.8.0", "animations": { "animation.propeller_hat.spin": { "loop": true, "bones": { "propeller": { "rotation": [0, "q.anim_time * 720", 0] } } } } }"""));
		BedrockImporter.Result r = BedrockImporter.importFiles(f, "hat");
		assertEquals(1, r.cosmetics.size(), r.problems.toString());
		CosmeticAsset a = r.cosmetics.getFirst();
		assertEquals(1, a.animations.size());
		assertEquals(PackReader.Kind.BEDROCK, PackReader.detect(f));
		assertFalse(r.problems.stream().anyMatch(p -> p.severity() == PackProblem.Severity.ERROR));
	}

	@Test
	void nothingImportable() {
		ArchiveFiles f = ArchiveFiles.empty();
		f.put("readme.txt", utf8("hello"));
		BedrockImporter.Result r = BedrockImporter.importFiles(f, "x");
		assertTrue(r.cosmetics.isEmpty());
		assertEquals(PackProblem.Severity.ERROR, r.problems.getFirst().severity());
	}
}
