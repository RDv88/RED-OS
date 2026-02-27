package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record SyncSystemInfoPayload(double tps, int activeDrones, int meshNodes, String serverRam, boolean discordOnline, boolean isAdmin) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncSystemInfoPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "sync_system_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSystemInfoPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SyncSystemInfoPayload::tps,
            ByteBufCodecs.VAR_INT, SyncSystemInfoPayload::activeDrones,
            ByteBufCodecs.VAR_INT, SyncSystemInfoPayload::meshNodes,
            ByteBufCodecs.STRING_UTF8, SyncSystemInfoPayload::serverRam,
            ByteBufCodecs.BOOL, SyncSystemInfoPayload::discordOnline,
            ByteBufCodecs.BOOL, SyncSystemInfoPayload::isAdmin,
            SyncSystemInfoPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
