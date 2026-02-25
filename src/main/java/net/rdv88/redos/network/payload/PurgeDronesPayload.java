package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record PurgeDronesPayload(BlockPos hubPos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PurgeDronesPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "purge_drones"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PurgeDronesPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PurgeDronesPayload::hubPos,
        PurgeDronesPayload::new
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}