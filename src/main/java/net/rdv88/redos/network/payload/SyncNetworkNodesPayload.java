package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SyncNetworkNodesPayload(List<NodeData> nodes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncNetworkNodesPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "sync_network_nodes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncNetworkNodesPayload> CODEC = StreamCodec.composite(
            NodeData.CODEC.apply(ByteBufCodecs.list()), SyncNetworkNodesPayload::nodes,
            SyncNetworkNodesPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public record NodeData(BlockPos pos, String id, String name, String type, String dim) {
        public static final StreamCodec<RegistryFriendlyByteBuf, NodeData> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, NodeData::pos,
                ByteBufCodecs.STRING_UTF8, NodeData::id,
                ByteBufCodecs.STRING_UTF8, NodeData::name,
                ByteBufCodecs.STRING_UTF8, NodeData::type,
                ByteBufCodecs.STRING_UTF8, NodeData::dim,
                NodeData::new
        );
    }
}