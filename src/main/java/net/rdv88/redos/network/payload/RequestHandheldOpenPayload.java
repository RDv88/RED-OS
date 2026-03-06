package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestHandheldOpenPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestHandheldOpenPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "request_handheld_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHandheldOpenPayload> CODEC = StreamCodec.unit(new RequestHandheldOpenPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}