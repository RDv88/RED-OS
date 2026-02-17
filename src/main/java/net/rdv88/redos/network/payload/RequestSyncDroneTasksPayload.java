package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record RequestSyncDroneTasksPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestSyncDroneTasksPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "request_drone_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSyncDroneTasksPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestSyncDroneTasksPayload::pos,
            RequestSyncDroneTasksPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
