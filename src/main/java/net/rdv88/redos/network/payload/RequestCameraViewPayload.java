package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestCameraViewPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestCameraViewPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "request_camera_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCameraViewPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestCameraViewPayload::pos,
            RequestCameraViewPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}