package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record ConfigureDroneHubPayload(BlockPos pos, String action, int priority, BlockPos sourcePos, BlockPos targetPos, int droneIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigureDroneHubPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "configure_drone_hub"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureDroneHubPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDroneHubPayload::pos,
            ByteBufCodecs.STRING_UTF8, ConfigureDroneHubPayload::action,
            ByteBufCodecs.VAR_INT, ConfigureDroneHubPayload::priority,
            BlockPos.STREAM_CODEC, p -> p.sourcePos() != null ? p.sourcePos() : BlockPos.ZERO,
            BlockPos.STREAM_CODEC, p -> p.targetPos() != null ? p.targetPos() : BlockPos.ZERO,
            ByteBufCodecs.VAR_INT, ConfigureDroneHubPayload::droneIndex,
            ConfigureDroneHubPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
