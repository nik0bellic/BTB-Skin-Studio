package de.behindtheblocks.skinstudio.client.gui;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.behindtheblocks.skinstudio.client.data.Wardrobe;
import de.behindtheblocks.skinstudio.client.library.CosmeticLibrary;
import de.behindtheblocks.skinstudio.cosmetic.CosmeticDefinition;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import de.behindtheblocks.skinstudio.pack.CosmeticAsset;
import de.behindtheblocks.skinstudio.util.Colors;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Client commands ({@code /skinstudio …}) for quick access and scripted tests:
 * equip, unequip, clear, list, reload, and opening the wardrobe/editor.
 */
public final class SkinStudioCommands {
	private SkinStudioCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("skinstudio")
				.executes(c -> {
					openWardrobe(c.getSource().getClient());
					return 1;
				})
				.then(literal("wardrobe").executes(c -> {
					openWardrobe(c.getSource().getClient());
					return 1;
				}))
				.then(literal("editor").executes(c -> {
					openEditor(c.getSource().getClient(), null);
					return 1;
				}).then(argument("id", StringArgumentType.greedyString()).suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b)).executes(c -> {
					openEditor(c.getSource().getClient(), StringArgumentType.getString(c, "id"));
					return 1;
				})))
				.then(literal("packs").executes(c -> {
					openPacks(c.getSource().getClient());
					return 1;
				}))
				.then(literal("equip").then(argument("id", StringArgumentType.string()).suggests((c, b) -> SharedSuggestionProvider.suggest(ids(), b))
						.executes(c -> equip(c, StringArgumentType.getString(c, "id"), 0))
						.then(argument("color", StringArgumentType.word()).executes(c -> {
							int color;
							try {
								color = Colors.parse(StringArgumentType.getString(c, "color"));
							} catch (RuntimeException e) {
								c.getSource().sendError(Component.literal(e.getMessage()));
								return 0;
							}
							return equip(c, StringArgumentType.getString(c, "id"), color);
						}))))
				.then(literal("unequip").then(argument("id", StringArgumentType.string())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Wardrobe.get().equipped().entries().stream().map(Loadout.Entry::id), b))
						.executes(c -> {
							Loadout l = Wardrobe.get().equipped().copy();
							if (l.unequip(StringArgumentType.getString(c, "id"))) {
								Wardrobe.get().setEquipped(l);
								c.getSource().sendFeedback(Component.literal("Abgelegt."));
								return 1;
							}
							c.getSource().sendError(Component.literal("Nicht ausgerüstet."));
							return 0;
						})))
				.then(literal("clear").executes(c -> {
					Wardrobe.get().setEquipped(new Loadout());
					c.getSource().sendFeedback(Component.literal("Alle Cosmetics abgelegt."));
					return 1;
				}))
				.then(literal("list").executes(c -> {
					var all = CosmeticLibrary.get().all();
					c.getSource().sendFeedback(Component.literal(all.size() + " Cosmetics verfügbar:"));
					for (CosmeticAsset a : all) {
						boolean on = Wardrobe.get().equipped().contains(a.id());
						c.getSource().sendFeedback(Component.literal((on ? "● " : "○ ") + a.id() + "  " + a.definition.name(CosmeticLibrary.language())));
					}
					return all.size();
				}))
				.then(literal("reload").executes(c -> {
					CosmeticLibrary.get().reload().thenRun(() -> c.getSource().sendFeedback(
							Component.literal("Neu geladen: " + CosmeticLibrary.get().all().size() + " Cosmetics.")));
					return 1;
				}))));
	}

	private static java.util.stream.Stream<String> ids() {
		return CosmeticLibrary.get().all().stream().map(CosmeticAsset::id);
	}

	private static int equip(CommandContext<FabricClientCommandSource> c, String id, int color) {
		CosmeticDefinition def = CosmeticLibrary.get().definition(id);
		if (def == null) {
			c.getSource().sendError(Component.literal("Unbekanntes Cosmetic: " + id));
			return 0;
		}
		Loadout l = Wardrobe.get().equipped().copy();
		Loadout.EquipResult r = l.equip(def, color, "", CosmeticLibrary.get()::definition);
		if (!r.equipped()) {
			c.getSource().sendError(Component.literal(r.message()));
			return 0;
		}
		Wardrobe.get().markUsed(id);
		Wardrobe.get().setEquipped(l);
		c.getSource().sendFeedback(Component.literal("Ausgerüstet: " + def.name(CosmeticLibrary.language())
				+ (r.removed().isEmpty() ? "" : " (abgelegt: " + String.join(", ", r.removed()) + ")")));
		return 1;
	}

	/** Opens the wardrobe on the next tick (the chat screen closes after a command). */
	public static void openWardrobe(Minecraft mc) {
		mc.execute(() -> mc.gui.setScreen(new WardrobeScreen(null)));
	}

	public static void openEditor(Minecraft mc, String id) {
		mc.execute(() -> mc.gui.setScreen(de.behindtheblocks.skinstudio.client.editor.CosmeticEditorScreen.open(null, id)));
	}

	public static void openPacks(Minecraft mc) {
		mc.execute(() -> mc.gui.setScreen(new PackManagerScreen(null)));
	}
}
