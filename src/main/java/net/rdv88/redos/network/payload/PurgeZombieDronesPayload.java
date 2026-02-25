package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record PurgeZombieDronesPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PurgeZombieDronesPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "purge_zombie_drones"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurgeZombieDronesPayload> CODEC = StreamCodec.unit(new PurgeZombieDronesPayload());

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return ID; }
}