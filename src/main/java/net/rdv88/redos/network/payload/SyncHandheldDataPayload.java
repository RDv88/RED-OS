package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record SyncHandheldDataPayload(List<DeviceEntry> devices) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncHandheldDataPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("redos", "sync_handheld_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHandheldDataPayload> CODEC = StreamCodec.composite(
            DeviceEntry.CODEC.apply(ByteBufCodecs.list()), SyncHandheldDataPayload::devices,
            SyncHandheldDataPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public record DeviceEntry(BlockPos pos, String id, String name, String type, int signalStrength, String connectionMode, 
                              boolean detectPlayers, boolean detectMobs, boolean detectAnimals, boolean detectVillagers,
                              int range, int holdTime) {
        public static final StreamCodec<RegistryFriendlyByteBuf, DeviceEntry> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, DeviceEntry::pos,
                ByteBufCodecs.STRING_UTF8, DeviceEntry::id,
                ByteBufCodecs.STRING_UTF8, DeviceEntry::name,
                ByteBufCodecs.STRING_UTF8, DeviceEntry::type,
                ByteBufCodecs.VAR_INT, DeviceEntry::signalStrength,
                ByteBufCodecs.STRING_UTF8, DeviceEntry::connectionMode,
                ByteBufCodecs.BOOL, DeviceEntry::detectPlayers,
                ByteBufCodecs.BOOL, DeviceEntry::detectMobs,
                ByteBufCodecs.BOOL, DeviceEntry::detectAnimals,
                ByteBufCodecs.BOOL, DeviceEntry::detectVillagers,
                ByteBufCodecs.VAR_INT, DeviceEntry::range,
                ByteBufCodecs.VAR_INT, DeviceEntry::holdTime,
                DeviceEntry::new
        );
    }
}