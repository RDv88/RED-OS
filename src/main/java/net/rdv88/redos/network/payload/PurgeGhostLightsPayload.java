package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PurgeGhostLightsPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PurgeGhostLightsPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "purge_ghost_lights"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PurgeGhostLightsPayload> CODEC = StreamCodec.unit(new PurgeGhostLightsPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
