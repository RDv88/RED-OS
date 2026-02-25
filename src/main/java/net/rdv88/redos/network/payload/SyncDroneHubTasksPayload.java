package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

import java.util.List;

public record SyncDroneHubTasksPayload(BlockPos hubPos, List<TaskData> tasks, List<Boolean> lockedSlots) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncDroneHubTasksPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "sync_drone_hub_tasks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDroneHubTasksPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SyncDroneHubTasksPayload::hubPos,
        TaskData.CODEC.apply(ByteBufCodecs.list()), SyncDroneHubTasksPayload::tasks,
        ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), SyncDroneHubTasksPayload::lockedSlots,
        SyncDroneHubTasksPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }

    public record TaskData(BlockPos src, BlockPos dst, int prio, boolean assigned, boolean enabled, int srcCount, int srcFree, int dstCount, int dstFree, String droneState, int etaTicks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TaskData> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TaskData::src,
            BlockPos.STREAM_CODEC, TaskData::dst,
            ByteBufCodecs.VAR_INT, TaskData::prio,
            ByteBufCodecs.BOOL, TaskData::assigned,
            ByteBufCodecs.BOOL, TaskData::enabled,
            ByteBufCodecs.VAR_INT, TaskData::srcCount,
            ByteBufCodecs.VAR_INT, TaskData::srcFree,
            ByteBufCodecs.VAR_INT, TaskData::dstCount,
            ByteBufCodecs.VAR_INT, TaskData::dstFree,
            ByteBufCodecs.STRING_UTF8, TaskData::droneState,
            ByteBufCodecs.VAR_INT, TaskData::etaTicks,
            TaskData::new
        );
    }
}