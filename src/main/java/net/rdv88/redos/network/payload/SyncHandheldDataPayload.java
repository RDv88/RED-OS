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
                              boolean alertsEnabled, int range, int holdTime, int itemCount, int freeSpace) {
        
        // Split into two sub-codecs to stay under the 12-field limit of StreamCodec.composite
        public static final StreamCodec<RegistryFriendlyByteBuf, DeviceEntry> CODEC = StreamCodec.of(
            (buf, entry) -> {
                BlockPos.STREAM_CODEC.encode(buf, entry.pos);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.id);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.name);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.type);
                ByteBufCodecs.VAR_INT.encode(buf, entry.signalStrength);
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.connectionMode);
                ByteBufCodecs.BOOL.encode(buf, entry.detectPlayers);
                ByteBufCodecs.BOOL.encode(buf, entry.detectMobs);
                ByteBufCodecs.BOOL.encode(buf, entry.detectAnimals);
                ByteBufCodecs.BOOL.encode(buf, entry.detectVillagers);
                ByteBufCodecs.BOOL.encode(buf, entry.alertsEnabled);
                ByteBufCodecs.VAR_INT.encode(buf, entry.range);
                ByteBufCodecs.VAR_INT.encode(buf, entry.holdTime);
                ByteBufCodecs.VAR_INT.encode(buf, entry.itemCount);
                ByteBufCodecs.VAR_INT.encode(buf, entry.freeSpace);
            },
            buf -> new DeviceEntry(
                BlockPos.STREAM_CODEC.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf)
            )
        );
    }
}