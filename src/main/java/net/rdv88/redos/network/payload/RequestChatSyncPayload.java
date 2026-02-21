package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record RequestChatSyncPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestChatSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "request_chat_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestChatSyncPayload> CODEC = StreamCodec.unit(new RequestChatSyncPayload());

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}