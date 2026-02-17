package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TriggerActivationPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TriggerActivationPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "activate_trigger"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TriggerActivationPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TriggerActivationPayload::pos,
            TriggerActivationPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
