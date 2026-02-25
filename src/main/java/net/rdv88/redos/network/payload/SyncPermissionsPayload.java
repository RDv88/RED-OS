package net.rdv88.redos.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncPermissionsPayload(boolean hasMainAccess, boolean hasHighTechAccess, boolean isAdmin, String serverVersion) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPermissionsPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "sync_permissions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPermissionsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncPermissionsPayload::hasMainAccess,
            ByteBufCodecs.BOOL, SyncPermissionsPayload::hasHighTechAccess,
            ByteBufCodecs.BOOL, SyncPermissionsPayload::isAdmin,
            ByteBufCodecs.STRING_UTF8, SyncPermissionsPayload::serverVersion,
            SyncPermissionsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}