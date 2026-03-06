package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HandheldOpenResponsePayload(net.minecraft.world.item.ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HandheldOpenResponsePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "handheld_open_response"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, HandheldOpenResponsePayload> CODEC = StreamCodec.composite(
            net.minecraft.world.item.ItemStack.STREAM_CODEC, HandheldOpenResponsePayload::stack,
            HandheldOpenResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}