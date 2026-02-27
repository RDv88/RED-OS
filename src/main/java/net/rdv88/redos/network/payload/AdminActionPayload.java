package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

/**
 * Encrypted-ready packet for administrative actions.
 * MUST be validated on server-side using player.hasPermissions(4).
 */
public record AdminActionPayload(ActionType action, String data1, String data2) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AdminActionPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "admin_action"));

    public enum ActionType {
        SET_DISCORD_TOKEN,
        SET_DISCORD_CHANNEL,
        KICK_PLAYER,
        BAN_PLAYER,
        KILL_PLAYER,
        OP_PLAYER,
        DEOP_PLAYER,
        RELOAD_CONFIG,
        RELOAD_NETWORK,
        SET_TIME,
        SET_WEATHER,
        BROADCAST,
        REQUEST_SYSTEM_INFO,
        RESET_DISCORD,
        TP_TO_PLAYER,
        SET_DIFFICULTY,
        SET_SPAWN,
        TP_SPAWN
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> CODEC = new StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload>() {
        @Override
        public AdminActionPayload decode(RegistryFriendlyByteBuf buf) {
            ActionType action = ActionType.values()[buf.readVarInt()];
            String data1 = buf.readUtf();
            String data2 = buf.readUtf();
            return new AdminActionPayload(action, data1, data2);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, AdminActionPayload payload) {
            buf.writeVarInt(payload.action().ordinal());
            buf.writeUtf(payload.data1());
            buf.writeUtf(payload.data2());
        }
    };

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
