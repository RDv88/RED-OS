package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.rdv88.redos.Redos;

public record CameraViewResponsePayload(boolean success, String message, String cameraName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CameraViewResponsePayload> ID = new CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(Redos.MOD_ID, "camera_view_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraViewResponsePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CameraViewResponsePayload::success,
            ByteBufCodecs.STRING_UTF8, CameraViewResponsePayload::message,
            ByteBufCodecs.STRING_UTF8, CameraViewResponsePayload::cameraName,
            CameraViewResponsePayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}