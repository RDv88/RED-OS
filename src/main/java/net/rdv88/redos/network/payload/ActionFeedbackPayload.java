package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ActionFeedbackPayload(String message, boolean isError) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActionFeedbackPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "action_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionFeedbackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ActionFeedbackPayload::message,
            ByteBufCodecs.BOOL, ActionFeedbackPayload::isError,
            ActionFeedbackPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
