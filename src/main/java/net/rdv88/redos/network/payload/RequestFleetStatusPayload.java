package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record RequestFleetStatusPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestFleetStatusPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "request_fleet_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFleetStatusPayload> CODEC = StreamCodec.unit(new RequestFleetStatusPayload());

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}