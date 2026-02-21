package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record SendChatMessagePayload(String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SendChatMessagePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "send_chat_message"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SendChatMessagePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SendChatMessagePayload::message,
            SendChatMessagePayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}