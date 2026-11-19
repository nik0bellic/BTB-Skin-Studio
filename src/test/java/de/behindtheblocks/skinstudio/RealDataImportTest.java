package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.bedrock.BedrockImporter;
import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Optional check against real, locally available Bedrock data (not shipped with the project):
 * {@code ./gradlew test -Pbtb.realdata=/path/to/skin_pack_or_persona_folder}
 */
class RealDataImportTest {
	@Test
	@EnabledIfSystemProperty(named = "btb.realdata", matches = ".+")
	void importRealData() throws Exception {
		Path p = Path.of(System.getProperty("btb.realdata"));
		BedrockImporter.Result r = BedrockImporter.importFiles(ArchiveFiles.read(p), p.getFileName().toString());
		System.out.println("== " + p + ": " + r.cosmetics.size() + " Cosmetics, " + r.skippedSkins + " Skins ohne Zusatzteile, geschützt=" + r.protectedContent);
		for (CosmeticAsset a : r.cosmetics) {
			BakedModel b = BakedModel.bake(a.model);
			System.out.println("  + " + a.id() + " „" + a.definition.name("de") + "“ " + a.definition.type.id() + ", " + a.model.cubeCount() + " Würfel, Wurzeln "
					+ b.roots.stream().map(x -> x.name).toList());
		}
		for (PackProblem pr : r.problems) {
			System.out.println("  ! [" + pr.severity() + "] " + pr);
		}
		assertFalse(r.cosmetics.isEmpty() && !r.protectedContent);
	}
}
