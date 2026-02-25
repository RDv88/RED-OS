package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;
import java.util.List;

public record SyncFleetStatusPayload(List<DroneTaskStatus> activeMissions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncFleetStatusPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "sync_fleet_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFleetStatusPayload> CODEC = StreamCodec.composite(
        DroneTaskStatus.CODEC.apply(ByteBufCodecs.list()), SyncFleetStatusPayload::activeMissions,
        SyncFleetStatusPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }

    public record DroneTaskStatus(BlockPos hubPos, int taskIndex, String sourceName, String destName, int priority, boolean enabled, String droneState, int etaTicks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, DroneTaskStatus> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DroneTaskStatus::hubPos,
            ByteBufCodecs.VAR_INT, DroneTaskStatus::taskIndex,
            ByteBufCodecs.STRING_UTF8, DroneTaskStatus::sourceName,
            ByteBufCodecs.STRING_UTF8, DroneTaskStatus::destName,
            ByteBufCodecs.VAR_INT, DroneTaskStatus::priority,
            ByteBufCodecs.BOOL, DroneTaskStatus::enabled,
            ByteBufCodecs.STRING_UTF8, DroneTaskStatus::droneState,
            ByteBufCodecs.VAR_INT, DroneTaskStatus::etaTicks,
            DroneTaskStatus::new
        );
    }
}