package de.behindtheblocks.skinstudio.network;

import de.behindtheblocks.skinstudio.BtbSkinStudio;
import de.behindtheblocks.skinstudio.loadout.Loadout;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * All payloads of the cosmetic sync protocol. Loadouts carry only ids, colors and content hashes
 * (a few hundred bytes). Model data travels only on request, once per content hash, in chunks
 * (see {@code docs/ARCHITECTURE.md}, "Multiplayer").
 */
public final class SkinStudioPayloads {
	/** Bump when the wire format changes incompatibly. */
	public static final int PROTOCOL = 2;
	public static final int CHUNK_SIZE = 30_000;
	public static final int MAX_TRANSFER_BYTES = 512 * 1024;
	public static final int MAX_CHUNKS = MAX_TRANSFER_BYTES / CHUNK_SIZE + 1;

	private SkinStudioPayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BtbSkinStudio.MOD_ID, path));
	}

	public static final StreamCodec<ByteBuf, Loadout.Adjust> ADJUST_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Loadout.Adjust::x,
			ByteBufCodecs.FLOAT, Loadout.Adjust::y,
			ByteBufCodecs.FLOAT, Loadout.Adjust::z,
			ByteBufCodecs.FLOAT, Loadout.Adjust::scale,
			Loadout.Adjust::new);

	public static final StreamCodec<ByteBuf, Loadout.Entry> ENTRY_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(160), Loadout.Entry::id,
			ByteBufCodecs.INT, Loadout.Entry::color,
			ByteBufCodecs.stringUtf8(64), Loadout.Entry::hash,
			ADJUST_CODEC, Loadout.Entry::adjust,
			Loadout.Entry::new);

	public static final StreamCodec<ByteBuf, List<Loadout.Entry>> ENTRIES_CODEC = ENTRY_CODEC.apply(ByteBufCodecs.list(Loadout.MAX_ENTRIES));

	// ------------------------------------------------------------------ handshake

	/** Both directions: protocol version and mod version. Server → client also says whether transfer is allowed. */
	public record Hello(int protocol, String modVersion, boolean transferAllowed) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = SkinStudioPayloads.payloadType("hello");
		public static final StreamCodec<ByteBuf, Hello> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Hello::protocol,
				ByteBufCodecs.stringUtf8(32), Hello::modVersion,
				ByteBufCodecs.BOOL, Hello::transferAllowed,
				Hello::new);

		@Override
		public Type<Hello> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ loadouts

	/** Client → server: my current loadout. */
	public record SetLoadout(List<Loadout.Entry> entries) implements CustomPacketPayload {
		public static final Type<SetLoadout> TYPE = SkinStudioPayloads.payloadType("set_loadout");
		public static final StreamCodec<ByteBuf, SetLoadout> CODEC = StreamCodec.composite(ENTRIES_CODEC, SetLoadout::entries, SetLoadout::new);

		@Override
		public Type<SetLoadout> type() {
			return TYPE;
		}
	}

	public record PlayerLoadout(UUID player, List<Loadout.Entry> entries) {
		public static final StreamCodec<ByteBuf, PlayerLoadout> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, PlayerLoadout::player,
				ENTRIES_CODEC, PlayerLoadout::entries,
				PlayerLoadout::new);
	}

	/** Server → client: loadouts of one or more players (all of them on join, single ones on change). */
	public record SyncLoadouts(List<PlayerLoadout> loadouts) implements CustomPacketPayload {
		public static final Type<SyncLoadouts> TYPE = SkinStudioPayloads.payloadType("sync_loadouts");
		public static final StreamCodec<ByteBuf, SyncLoadouts> CODEC = StreamCodec.composite(
				PlayerLoadout.CODEC.apply(ByteBufCodecs.list(1024)), SyncLoadouts::loadouts, SyncLoadouts::new);

		@Override
		public Type<SyncLoadouts> type() {
			return TYPE;
		}
	}

	/** Server → client: a player left; forget the loadout. */
	public record RemovePlayer(UUID player) implements CustomPacketPayload {
		public static final Type<RemovePlayer> TYPE = SkinStudioPayloads.payloadType("remove_player");
		public static final StreamCodec<ByteBuf, RemovePlayer> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, RemovePlayer::player, RemovePlayer::new);

		@Override
		public Type<RemovePlayer> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ skins

	/** A worn skin: id, content hash (empty for Minecraft default skins) and arm model. */
	public record SkinInfo(String id, String hash, boolean slim) {
		public static final StreamCodec<ByteBuf, SkinInfo> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(200), SkinInfo::id,
				ByteBufCodecs.stringUtf8(64), SkinInfo::hash,
				ByteBufCodecs.BOOL, SkinInfo::slim,
				SkinInfo::new);
	}

	/** Client → server: my skin (empty id = account skin). */
	public record SetSkin(SkinInfo skin) implements CustomPacketPayload {
		public static final Type<SetSkin> TYPE = SkinStudioPayloads.payloadType("set_skin");
		public static final StreamCodec<ByteBuf, SetSkin> CODEC = StreamCodec.composite(SkinInfo.CODEC, SetSkin::skin, SetSkin::new);

		@Override
		public Type<SetSkin> type() {
			return TYPE;
		}
	}

	public record PlayerSkinInfo(UUID player, SkinInfo skin) {
		public static final StreamCodec<ByteBuf, PlayerSkinInfo> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, PlayerSkinInfo::player, SkinInfo.CODEC, PlayerSkinInfo::skin, PlayerSkinInfo::new);
	}

	/** Server → client: skins of one or more players. */
	public record SyncSkins(List<PlayerSkinInfo> skins) implements CustomPacketPayload {
		public static final Type<SyncSkins> TYPE = SkinStudioPayloads.payloadType("sync_skins");
		public static final StreamCodec<ByteBuf, SyncSkins> CODEC = StreamCodec.composite(
				PlayerSkinInfo.CODEC.apply(ByteBufCodecs.list(1024)), SyncSkins::skins, SyncSkins::new);

		@Override
		public Type<SyncSkins> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ on-demand transfer

	/** Client → server: "I don't have the cosmetic with this content hash" (server → owner: "please upload it"). */
	public record RequestCosmetic(String hash) implements CustomPacketPayload {
		public static final Type<RequestCosmetic> TYPE = SkinStudioPayloads.payloadType("request_cosmetic");
		public static final StreamCodec<ByteBuf, RequestCosmetic> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), RequestCosmetic::hash, RequestCosmetic::new);

		@Override
		public Type<RequestCosmetic> type() {
			return TYPE;
		}
	}

	/** Both directions: one chunk of a {@code .btbcosmetic} archive. */
	public record CosmeticChunk(String hash, int index, int total, byte[] data) implements CustomPacketPayload {
		public static final Type<CosmeticChunk> TYPE = SkinStudioPayloads.payloadType("cosmetic_chunk");
		public static final StreamCodec<ByteBuf, CosmeticChunk> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), CosmeticChunk::hash,
				ByteBufCodecs.VAR_INT, CosmeticChunk::index,
				ByteBufCodecs.VAR_INT, CosmeticChunk::total,
				ByteBufCodecs.byteArray(CHUNK_SIZE), CosmeticChunk::data,
				CosmeticChunk::new);

		@Override
		public Type<CosmeticChunk> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Hello.TYPE, Hello.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetLoadout.TYPE, SetLoadout.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncLoadouts.TYPE, SyncLoadouts.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RemovePlayer.TYPE, RemovePlayer.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetSkin.TYPE, SetSkin.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncSkins.TYPE, SyncSkins.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestCosmetic.TYPE, RequestCosmetic.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RequestCosmetic.TYPE, RequestCosmetic.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CosmeticChunk.TYPE, CosmeticChunk.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CosmeticChunk.TYPE, CosmeticChunk.CODEC);
	}

	/** Splits an archive into chunks. */
	public static List<CosmeticChunk> split(String hash, byte[] data) {
		int total = Math.max(1, (data.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
		java.util.ArrayList<CosmeticChunk> out = new java.util.ArrayList<>(total);
		for (int i = 0; i < total; i++) {
			int from = i * CHUNK_SIZE;
			out.add(new CosmeticChunk(hash, i, total, java.util.Arrays.copyOfRange(data, from, Math.min(data.length, from + CHUNK_SIZE))));
		}
		return out;
	}

	/** Collects chunks of one transfer; rejects inconsistent or oversized transfers. */
	public static final class Assembly {
		private final byte[][] parts;
		private int received;
		private int bytes;
		public final long startedAt = System.currentTimeMillis();

		public Assembly(int total) {
			if (total <= 0 || total > MAX_CHUNKS) {
				throw new IllegalArgumentException("ungültige Chunk-Anzahl " + total);
			}
			this.parts = new byte[total][];
		}

		/** @return the complete data, or null while incomplete */
		public byte[] add(CosmeticChunk c) {
			if (c.total() != parts.length || c.index() < 0 || c.index() >= parts.length || parts[c.index()] != null) {
				throw new IllegalArgumentException("ungültiger Chunk");
			}
			bytes += c.data().length;
			if (bytes > MAX_TRANSFER_BYTES) {
				throw new IllegalArgumentException("Übertragung zu groß");
			}
			parts[c.index()] = c.data();
			if (++received < parts.length) {
				return null;
			}
			byte[] out = new byte[bytes];
			int o = 0;
			for (byte[] p : parts) {
				System.arraycopy(p, 0, out, o, p.length);
				o += p.length;
			}
			return out;
		}
	}
}
