package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ConfigureDevicePayload(BlockPos pos, String newName, String newId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigureDevicePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "configure_device"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureDevicePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDevicePayload::pos,
            ByteBufCodecs.STRING_UTF8, ConfigureDevicePayload::newName,
            ByteBufCodecs.STRING_UTF8, ConfigureDevicePayload::newId,
            ConfigureDevicePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
