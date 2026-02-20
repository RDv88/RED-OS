package net.rdv88.redos.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.Redos;

public record ConfigureSensorSettingsPayload(BlockPos pos, boolean detectPlayers, boolean detectMobs, boolean detectAnimals, boolean detectVillagers, boolean alertsEnabled, int range, int holdTime) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigureSensorSettingsPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Redos.MOD_ID, "configure_sensor_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureSensorSettingsPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureSensorSettingsPayload::pos,
            ByteBufCodecs.BOOL, ConfigureSensorSettingsPayload::detectPlayers,
            ByteBufCodecs.BOOL, ConfigureSensorSettingsPayload::detectMobs,
            ByteBufCodecs.BOOL, ConfigureSensorSettingsPayload::detectAnimals,
            ByteBufCodecs.BOOL, ConfigureSensorSettingsPayload::detectVillagers,
            ByteBufCodecs.BOOL, ConfigureSensorSettingsPayload::alertsEnabled,
            ByteBufCodecs.VAR_INT, ConfigureSensorSettingsPayload::range,
            ByteBufCodecs.VAR_INT, ConfigureSensorSettingsPayload::holdTime,
            ConfigureSensorSettingsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}