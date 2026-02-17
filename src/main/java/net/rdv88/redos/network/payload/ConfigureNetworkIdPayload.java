package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ConfigureNetworkIdPayload(BlockPos pos, String newId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigureNetworkIdPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "configure_network_id"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureNetworkIdPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureNetworkIdPayload::pos,
            ByteBufCodecs.STRING_UTF8, ConfigureNetworkIdPayload::newId,
            ConfigureNetworkIdPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}