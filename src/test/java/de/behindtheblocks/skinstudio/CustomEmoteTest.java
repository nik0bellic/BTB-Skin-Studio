package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.emote.CustomEmote;
import de.behindtheblocks.skinstudio.emote.Emotes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomEmoteTest {
	private static CustomEmote sample() {
		CustomEmote e = new CustomEmote();
		e.name = "Test";
		e.duration = 2f;
		e.item = "minecraft:diamond";
		e.setKey(2, 0f, 0, 0, 0);
		e.setKey(2, 1f, -160, 0, -25);
		e.setKey(7, 0.5f, 0, -3, 0);
		return e;
	}

	@Test
	void roundTripKeepsDataAndId() {
		CustomEmote e = sample();
		String json = e.toJson();
		CustomEmote back = CustomEmote.parse(json);
		assertEquals(json, back.toJson());
		assertEquals(e.networkId(), back.networkId());
		assertTrue(CustomEmote.isNetworkId(e.networkId()));
		e.setKey(2, 1f, -150, 0, -25);
		assertNotEquals(back.networkId(), e.networkId());
	}

	@Test
	void easesBetweenKeyframes() {
		CustomEmote e = sample();
		float[] mid = e.valueAt(2, 0.5f);
		assertEquals(-80, mid[0], 0.01f);
		assertEquals(-160, e.valueAt(2, 1.8f)[0], 0.01f);
		assertNull(e.valueAt(0, 0.5f));
	}

	@Test
	void rejectsAndClampsBadData() {
		assertThrows(RuntimeException.class, () -> CustomEmote.parse("[]"));
		assertThrows(RuntimeException.class, () -> CustomEmote.parse("x".repeat(CustomEmote.MAX_DATA + 1)));
		CustomEmote c = CustomEmote.parse("{\"name\":\"§cBad\\u0000\",\"duration\":999,\"item\":\"Not An Item\",\"keys\":[[\"right_arm\",0,9999,0,0],[\"tail\",0,1,1,1],[\"move\",1,0,-99,0]]}");
		assertEquals("cBad", c.name);
		assertEquals(CustomEmote.MAX_DURATION, c.duration);
		assertNull(c.item);
		assertEquals(2, c.keys.size());
		assertEquals(180, c.keys.get(0).x());
		assertEquals(-16, c.keys.get(1).y());
	}

	@Test
	void registeredEmotesPlay() {
		CustomEmote e = sample();
		String id = e.networkId();
		Emotes.registerCustom(id, e);
		assertTrue(Emotes.exists(id));
		float[] pose = Emotes.sample(id, 1f);
		assertEquals(-160, pose[Emotes.RIGHT_ARM], 0.01f);
		assertEquals(-3, pose[Emotes.MOVE + 1], 0.01f);
		Emotes.unregisterCustom(id);
		assertTrue(!Emotes.exists(id));
	}
}
