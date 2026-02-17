package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record RequestPorterGuiPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestPorterGuiPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "request_porter_gui"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPorterGuiPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestPorterGuiPayload::pos,
            RequestPorterGuiPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}