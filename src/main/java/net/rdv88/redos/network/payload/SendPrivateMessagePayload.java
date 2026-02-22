package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record SendPrivateMessagePayload(String targetName, String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SendPrivateMessagePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "send_private_message"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SendPrivateMessagePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SendPrivateMessagePayload::targetName,
            ByteBufCodecs.STRING_UTF8, SendPrivateMessagePayload::message,
            SendPrivateMessagePayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}