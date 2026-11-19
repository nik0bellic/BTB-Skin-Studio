package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticJson;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticType;
import de.behindtheblocks.skinstudio.geo.GeoBone;
import de.behindtheblocks.skinstudio.geo.GeoCube;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.ArchiveFiles;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.pack.LoadedPack;
import de.behindtheblocks.skinstudio.pack.PackManifest;
import de.behindtheblocks.skinstudio.pack.PackProblem;
import de.behindtheblocks.skinstudio.pack.PackReader;
import de.behindtheblocks.skinstudio.pack.PackStore;
import de.behindtheblocks.skinstudio.pack.PackWriter;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackSystemTest {
	/** Smallest valid PNG header (IHDR with the given size); enough for header validation. */
	static byte[] png(int w, int h) {
		byte[] b = new byte[33];
		byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
		System.arraycopy(sig, 0, b, 0, sig.length);
		b[16] = (byte) (w >> 24); b[17] = (byte) (w >> 16); b[18] = (byte) (w >> 8); b[19] = (byte) w;
		b[20] = (byte) (h >> 24); b[21] = (byte) (h >> 16); b[22] = (byte) (h >> 8); b[23] = (byte) h;
		return b;
	}

	static CosmeticAsset cosmetic(String id, CosmeticType type) {
		CosmeticDefinition d = new CosmeticDefinition();
		d.id = id;
		d.type = type;
		d.attachment = type.defaultAttachment;
		d.names.put("de", "Test " + id);
		GeoModel m = new GeoModel("geometry.test", 16, 16);
		GeoBone b = new GeoBone("root");
		b.pivot = new float[]{0, 24, 0};
		b.cubes.add(GeoCube.box(-2, 32, -2, 4, 2, 4, 0, 0));
		m.bones.add(b);
		ArchiveFiles extra = ArchiveFiles.empty();
		extra.put("texture.png", png(16, 16));
		ArchiveFiles files = PackWriter.cosmeticFiles(d, m, null, extra);
		return PackReader.readSingle(files, "test", new ArrayList<>());
	}

	@Test
	void writeAndReadPackRoundTrip() throws Exception {
		PackManifest m = new PackManifest();
		m.id = "my_pack";
		m.version = "1.2.0";
		m.names.put("de", "Mein Pack");
		ArchiveFiles files = PackWriter.pack(m, png(32, 32), List.of(cosmetic("my:hat", CosmeticType.HAT), cosmetic("my:wings", CosmeticType.WINGS)));
		byte[] zip = files.toZip();
		LoadedPack pack = PackReader.readPack(ArchiveFiles.readZip(zip));
		assertTrue(pack.usable);
		assertEquals("my_pack", pack.id());
		assertEquals(2, pack.cosmetics.size(), pack.problems.toString());
		assertEquals(0, pack.errorCount(), pack.problems.toString());
		assertNotNull(pack.icon);
		assertEquals(PackReader.Kind.BTB_PACK, PackReader.detect(ArchiveFiles.readZip(zip)));
		// content hash is stable
		assertEquals(cosmetic("my:hat", CosmeticType.HAT).contentHash, cosmetic("my:hat", CosmeticType.HAT).contentHash);
	}

	@Test
	void brokenCosmeticIsReportedNotFatal() throws Exception {
		PackManifest m = new PackManifest();
		m.id = "half_broken";
		m.names.put("de", "x");
		ArchiveFiles files = PackWriter.pack(m, null, List.of(cosmetic("a:ok", CosmeticType.HAT)));
		files.put("cosmetics/broken/cosmetic.json", "{ \"id\": \"a:broken\", \"model\": \"missing.geo.json\" }".getBytes());
		files.put("cosmetics/badid/cosmetic.json", "{ \"id\": \"Bad ID!\" }".getBytes());
		LoadedPack pack = PackReader.readPack(files);
		assertTrue(pack.usable);
		assertEquals(1, pack.cosmetics.size());
		assertEquals(2, pack.errorCount(), pack.problems.toString());
		assertTrue(pack.problems.stream().anyMatch(p -> p.message().contains("missing.geo.json")));
	}

	@Test
	void notAPack() {
		LoadedPack pack = PackReader.readPack(ArchiveFiles.empty());
		assertFalse(pack.usable);
		assertEquals(PackProblem.Severity.ERROR, pack.problems.getFirst().severity());
	}

	@Test
	void zipSlipAndBombLimits() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(bos)) {
			z.putNextEntry(new ZipEntry("../../evil.txt"));
			z.write(1);
			z.putNextEntry(new ZipEntry("pack/pack.json"));
			z.write("{}".getBytes());
		}
		ArchiveFiles f = ArchiveFiles.readZip(bos.toByteArray());
		assertEquals(1, f.size(), "path traversal entry dropped");
		assertTrue(f.unwrapSingleFolder().has("pack.json"), "single top folder unwrapped");
		assertThrows(FormatException.class, () -> ArchiveFiles.readZip(new byte[]{1, 2, 3}));
	}

	@Test
	void duplicateIdsAcrossPacksAreRejected() {
		PackManifest m = new PackManifest();
		m.id = "second";
		m.names.put("de", "x");
		LoadedPack incoming = PackReader.readPack(PackWriter.pack(m, null, List.of(cosmetic("shared:crown", CosmeticType.CROWN))));
		Map<String, String> owners = new HashMap<>(Map.of("shared:crown", "first"));
		PackStore.InstallCheck check = PackStore.checkInstall(incoming, owners, Map.of());
		assertFalse(check.allowed());
		// the same pack id may update itself
		owners.put("shared:crown", "second");
		PackStore.InstallCheck update = PackStore.checkInstall(incoming, owners, Map.of("second", "0.9.0"));
		assertTrue(update.allowed());
		assertTrue(update.isUpdate());
		assertTrue(PackManifest.compareVersions("1.10.0", "1.9.9") > 0);
	}

	@Test
	void installAndUninstall(@TempDir Path dir) throws Exception {
		PackStore store = new PackStore(dir);
		PackManifest m = new PackManifest();
		m.id = "disk_pack";
		m.names.put("de", "x");
		ArchiveFiles files = PackWriter.pack(m, null, List.of(cosmetic("disk:hat", CosmeticType.HAT)));
		LoadedPack pack = PackReader.readPack(files);
		store.install(pack, files, null);
		List<LoadedPack> installed = store.loadInstalled();
		assertEquals(1, installed.size());
		assertEquals(1, installed.getFirst().cosmetics.size());
		store.uninstall(installed.getFirst());
		assertTrue(store.loadInstalled().isEmpty());

		CosmeticAsset own = cosmetic("custom:my_hat", CosmeticType.HAT);
		store.saveCustom(own);
		LoadedPack custom = store.loadCustom();
		assertEquals(1, custom.cosmetics.size(), custom.problems.toString());
		store.deleteCustom("custom:my_hat");
		assertTrue(store.loadCustom().cosmetics.isEmpty());
	}

	@Test
	void cosmeticJsonValidation() {
		assertThrows(FormatException.class, () -> CosmeticJson.read(Json.parseObject("{ \"id\": \"a:b\", \"type\": \"spaceship\" }".getBytes())));
		assertThrows(FormatException.class, () -> CosmeticJson.read(Json.parseObject("{ \"id\": \"a:b\", \"texture\": \"../../x.png\" }".getBytes())));
		assertThrows(FormatException.class, () -> CosmeticJson.read(Json.parseObject("{ \"id\": \"a:b\", \"format\": 99 }".getBytes())));
		CosmeticDefinition d = CosmeticJson.read(Json.parseObject("{ \"id\": \"a:b\", \"name\": {\"de\": \"Krone\", \"en\": \"Crown\"}, \"type\": \"crown\", \"tint\": {\"default\": \"#FFD700\"} }".getBytes()));
		assertEquals("Krone", d.name("de_de"));
		assertEquals("Crown", d.name("en_us"));
		assertEquals(0xFFFFD700, d.tint.defaultColor);
		assertEquals(d.id, d.copy().id);
	}

	@Test
	void loadoutConflicts() {
		Map<String, CosmeticDefinition> defs = new HashMap<>();
		for (var a : List.of(cosmetic("t:hat", CosmeticType.HAT), cosmetic("t:helmet", CosmeticType.HELMET),
				cosmetic("t:glasses", CosmeticType.GLASSES), cosmetic("t:backpack", CosmeticType.BACKPACK), cosmetic("t:wings", CosmeticType.WINGS))) {
			defs.put(a.id(), a.definition);
		}
		Loadout l = new Loadout();
		l.equip(defs.get("t:hat"), 0, "", defs::get);
		l.equip(defs.get("t:glasses"), 0, "", defs::get);
		l.equip(defs.get("t:backpack"), 0, "", defs::get);
		assertEquals(3, l.entries().size(), "hat, glasses and backpack fit together");
		Loadout.EquipResult r = l.equip(defs.get("t:helmet"), 0, "", defs::get);
		assertEquals(List.of("t:hat"), r.removed(), "helmet replaces the hat");
		assertTrue(l.contains("t:glasses"));
		r = l.equip(defs.get("t:wings"), 0xFF00FF00, "", defs::get);
		assertEquals(List.of("t:backpack"), r.removed());
		// unknown (not installed) ids survive and do not crash
		Loadout withUnknown = new Loadout(List.of(new Loadout.Entry("other:thing", 0, "")));
		withUnknown.equip(defs.get("t:hat"), 0, "", defs::get);
		assertTrue(withUnknown.contains("other:thing"));
		// JSON round trip incl. color
		Loadout back = Loadout.fromJson(l.toJson());
		assertEquals(l, new Loadout(back.entries().stream().map(e -> new Loadout.Entry(e.id(), e.color(), "")).toList()));
		assertEquals(0xFF00FF00, back.find("t:wings").color());
	}
}
