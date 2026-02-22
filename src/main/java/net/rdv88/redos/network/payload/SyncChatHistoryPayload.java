package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

import java.util.List;

public record SyncChatHistoryPayload(List<ChatEntry> history, List<PrivateEntry> privateHistory) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncChatHistoryPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "sync_chat_history"));

    public record ChatEntry(String sender, String message, long timestamp) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ChatEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ChatEntry::sender,
                ByteBufCodecs.STRING_UTF8, ChatEntry::message,
                ByteBufCodecs.VAR_LONG, ChatEntry::timestamp,
                ChatEntry::new
        );
    }

    public record PrivateEntry(String from, String to, String message, long timestamp) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PrivateEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PrivateEntry::from,
                ByteBufCodecs.STRING_UTF8, PrivateEntry::to,
                ByteBufCodecs.STRING_UTF8, PrivateEntry::message,
                ByteBufCodecs.VAR_LONG, PrivateEntry::timestamp,
                PrivateEntry::new
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChatHistoryPayload> CODEC = StreamCodec.composite(
            ChatEntry.CODEC.apply(ByteBufCodecs.list()), SyncChatHistoryPayload::history,
            PrivateEntry.CODEC.apply(ByteBufCodecs.list()), SyncChatHistoryPayload::privateHistory,
            SyncChatHistoryPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}