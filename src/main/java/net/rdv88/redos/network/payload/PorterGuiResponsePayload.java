package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record PorterGuiResponsePayload(BlockPos pos, String name, int status) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PorterGuiResponsePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "porter_gui_response"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PorterGuiResponsePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PorterGuiResponsePayload::pos,
            ByteBufCodecs.STRING_UTF8, PorterGuiResponsePayload::name,
            ByteBufCodecs.VAR_INT, PorterGuiResponsePayload::status,
            PorterGuiResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}