package de.behindtheblocks.skinstudio.client.editor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CosmeticEditorScreen extends Screen {
	private CosmeticEditorScreen() {
		super(Component.literal("Editor"));
	}

	public static Screen open(Screen parent, String id) {
		return new CosmeticEditorScreen();
	}
}
