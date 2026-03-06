package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ConfigureHandheldPayload(String newId, String serial) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigureHandheldPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "configure_handheld"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureHandheldPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigureHandheldPayload::newId,
            ByteBufCodecs.STRING_UTF8, ConfigureHandheldPayload::serial,
            ConfigureHandheldPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}