package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.network.SkinStudioPayloads;
import de.behindtheblocks.skinstudio.server.ServerCosmeticSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: payload types and the server-side sync (also active in singleplayer). */
public final class BtbSkinStudio implements ModInitializer {
	public static final String MOD_ID = "btb_skinstudio";
	public static final Logger LOGGER = LoggerFactory.getLogger("BTB Skin Studio");

	@Override
	public void onInitialize() {
		SkinStudioPayloads.register();
		new ServerCosmeticSync().register();
		LOGGER.info("BTB Skin Studio {} geladen (Sync-Protokoll {})", version(), SkinStudioPayloads.PROTOCOL);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	}
}
