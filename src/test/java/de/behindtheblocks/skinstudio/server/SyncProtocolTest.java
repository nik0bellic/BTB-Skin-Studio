package de.behindtheblocks.skinstudio.server;

import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.network.SkinStudioPayloads;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncProtocolTest {
	@Test
	void serverDropsMalformedLoadoutEntries() {
		List<Loadout.Entry> in = new ArrayList<>();
		in.add(new Loadout.Entry("btb:torch_hat", 0, "0123456789abcdef0123456789abcdef"));
		in.add(new Loadout.Entry("btb:torch_hat", 0, ""));            // duplicate
		in.add(new Loadout.Entry("../../etc/passwd", 0, ""));          // invalid id
		in.add(new Loadout.Entry("custom:hat", 0xFF00FF00, "not-a-hash")); // bad hash → kept without hash
		for (int i = 0; i < 40; i++) {
			in.add(new Loadout.Entry("spam:item" + i, 0, ""));
		}
		List<Loadout.Entry> out = ServerCosmeticSync.sanitize(in);
		assertEquals(Loadout.MAX_ENTRIES, out.size());
		assertEquals("btb:torch_hat", out.get(0).id());
		assertEquals("custom:hat", out.get(1).id());
		assertEquals("", out.get(1).hash());
		assertEquals(0xFF00FF00, out.get(1).color());
	}

	@Test
	void chunkingRoundTripAndLimits() {
		byte[] data = new byte[SkinStudioPayloads.CHUNK_SIZE * 3 + 123];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) i;
		}
		List<SkinStudioPayloads.CosmeticChunk> chunks = SkinStudioPayloads.split("abc", data);
		assertEquals(4, chunks.size());
		SkinStudioPayloads.Assembly a = new SkinStudioPayloads.Assembly(4);
		byte[] out = null;
		for (int i = chunks.size() - 1; i >= 0; i--) { // order does not matter
			out = a.add(chunks.get(i));
		}
		assertArrayEquals(data, out);
		SkinStudioPayloads.Assembly dup = new SkinStudioPayloads.Assembly(4);
		dup.add(chunks.get(0));
		assertThrows(IllegalArgumentException.class, () -> dup.add(chunks.get(0)), "duplicate chunk rejected");
		assertThrows(IllegalArgumentException.class, () -> new SkinStudioPayloads.Assembly(10_000), "absurd chunk count rejected");
	}

	@Test
	void uploadsAreVerifiedAgainstTheirHash() {
		assertFalse(ServerCosmeticSync.verify(new byte[]{1, 2, 3}, "0123456789abcdef0123456789abcdef"));
	}
}
