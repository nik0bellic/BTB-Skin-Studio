package de.behindtheblocks.skinstudio.client.emote;

import com.mojang.blaze3d.platform.InputConstants;
import de.behindtheblocks.skinstudio.client.data.ClientSettings;
import de.behindtheblocks.skinstudio.util.Tr;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Direct keys for the five wheel places (unbound by default, set them under Controls). */
public final class EmoteKeys {
	private static final KeyMapping[] SLOTS = new KeyMapping[5];

	private EmoteKeys() {
	}

	public static void register(KeyMapping.Category category) {
		for (int i = 0; i < SLOTS.length; i++) {
			SLOTS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.btb_skinstudio.emote_slot_" + (i + 1), InputConstants.UNKNOWN.getValue(), category));
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			for (int i = 0; i < SLOTS.length; i++) {
				while (SLOTS[i].consumeClick()) {
					if (mc.gui.screen() == null) {
						ClientEmotes.play(slot(i));
					}
				}
			}
		});
	}

	public static String slot(int i) {
		var slots = ClientSettings.get().emoteSlots;
		return i < slots.size() ? slots.get(i) : null;
	}

	/** Name of the key bound to a place, or "not set". */
	public static String keyName(int i) {
		KeyMapping k = SLOTS[i];
		if (k == null || k.isUnbound()) {
			return Tr.t("btb.ui.emotes.unbound");
		}
		return k.getTranslatedKeyMessage().getString();
	}
}
