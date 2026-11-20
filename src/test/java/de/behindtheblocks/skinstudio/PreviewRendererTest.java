package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.bedrock.Images;
import de.behindtheblocks.skinstudio.geo.PreviewRenderer;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.pack.PackReader;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PreviewRendererTest {
	@Test
	void rendersEveryStarterCosmetic() throws Exception {
		LoadedPack pack = PackReader.readPack(ArchiveFiles.readDirectory(Path.of("src/main/resources/assets/btb_skinstudio/builtin/btb_starter")));
		assertEquals(0, pack.errorCount(), pack.problems.toString());
		assertTrue(pack.cosmetics.size() >= 20);
		String out = System.getProperty("btb.previewdir");
		for (CosmeticAsset a : pack.cosmetics) {
			BufferedImage img = PreviewRenderer.render(a.model, Images.read(a.texture(), "t.png"),
					a.emissive() == null ? null : Images.read(a.emissive(), "e.png"), a.definition.attachment, 128);
			int opaque = 0;
			for (int y = 0; y < 128; y++) {
				for (int x = 0; x < 128; x++) {
					if ((img.getRGB(x, y) >>> 24) > 0) {
						opaque++;
					}
				}
			}
			assertTrue(opaque > 500, a.id() + " renders an icon");
			if (out != null) {
				Files.createDirectories(Path.of(out));
				Files.write(Path.of(out, a.definition.path() + ".png"), Images.png(img));
			}
		}
	}
}
