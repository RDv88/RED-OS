package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record JetpackActionPayload(boolean jumping, boolean forward) implements CustomPacketPayload {
    public static final Type<JetpackActionPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("redos", "jetpack_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.jumping());
                buf.writeBoolean(payload.forward());
            },
            buf -> new JetpackActionPayload(buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
