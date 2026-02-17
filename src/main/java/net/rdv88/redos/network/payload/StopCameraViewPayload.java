package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StopCameraViewPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StopCameraViewPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "stop_camera_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StopCameraViewPayload> CODEC = StreamCodec.unit(new StopCameraViewPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}