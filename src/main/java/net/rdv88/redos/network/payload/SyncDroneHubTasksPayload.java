package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

import java.util.List;

public record SyncDroneHubTasksPayload(BlockPos pos, List<TaskData> tasks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncDroneHubTasksPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "sync_drone_hub_tasks"));

    public record TaskData(BlockPos src, BlockPos dst, int prio, boolean assigned, boolean enabled) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TaskData> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TaskData::src,
                BlockPos.STREAM_CODEC, TaskData::dst,
                ByteBufCodecs.VAR_INT, TaskData::prio,
                ByteBufCodecs.BOOL, TaskData::assigned,
                ByteBufCodecs.BOOL, TaskData::enabled,
                TaskData::new
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDroneHubTasksPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SyncDroneHubTasksPayload::pos,
            TaskData.CODEC.apply(ByteBufCodecs.list()), SyncDroneHubTasksPayload::tasks,
            SyncDroneHubTasksPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}
