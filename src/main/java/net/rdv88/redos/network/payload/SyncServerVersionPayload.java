package net.rdv88.redos.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.rdv88.redos.Redos;

public record SyncServerVersionPayload(String serverVersion) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncServerVersionPayload> ID = new CustomPacketPayload.Type<>(Redos.id("sync_server_version"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncServerVersionPayload> CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, SyncServerVersionPayload::serverVersion,
            SyncServerVersionPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}