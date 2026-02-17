package net.rdv88.redos.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.rdv88.redos.Redos;
import net.rdv88.redos.block.ModBlocks;

public class ModBlockEntities {
    public static BlockEntityType<WirelessCameraBlockEntity> WIRELESS_CAMERA_BLOCK_ENTITY;
    public static BlockEntityType<SmartMotionSensorBlockEntity> SMART_MOTION_SENSOR_BLOCK_ENTITY;
    public static BlockEntityType<ShortRangeTransmitterBlockEntity> SHORT_RANGE_TRANSMITTER_BLOCK_ENTITY;
    public static BlockEntityType<LongRangeTransmitterBlockEntity> LONG_RANGE_TRANSMITTER_BLOCK_ENTITY;
    public static BlockEntityType<RemoteRedstoneTriggerBlockEntity> REMOTE_REDSTONE_TRIGGER_BLOCK_ENTITY;
    public static BlockEntityType<QuantumPorterBlockEntity> QUANTUM_PORTER_ENTITY;
    public static BlockEntityType<IOTagBlockEntity> IO_TAG_BLOCK_ENTITY;
    public static BlockEntityType<DroneStationBlockEntity> DRONE_STATION_BLOCK_ENTITY;

    public static void registerBlockEntities() {
        WIRELESS_CAMERA_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "wireless_camera"),
                FabricBlockEntityTypeBuilder.create(WirelessCameraBlockEntity::new, ModBlocks.WIRELESS_IP_CAMERA).build());

        SMART_MOTION_SENSOR_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "smart_motion_sensor"),
                FabricBlockEntityTypeBuilder.create(SmartMotionSensorBlockEntity::new, ModBlocks.SMART_MOTION_SENSOR).build());

        SHORT_RANGE_TRANSMITTER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "short_range_transmitter"),
                FabricBlockEntityTypeBuilder.create(ShortRangeTransmitterBlockEntity::new, ModBlocks.SHORT_RANGE_TRANSMITTER).build());

        LONG_RANGE_TRANSMITTER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "long_range_transmitter"),
                FabricBlockEntityTypeBuilder.create(LongRangeTransmitterBlockEntity::new, ModBlocks.LONG_RANGE_TRANSMITTER).build());

        REMOTE_REDSTONE_TRIGGER_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "remote_redstone_trigger"),
                FabricBlockEntityTypeBuilder.create(RemoteRedstoneTriggerBlockEntity::new, ModBlocks.REMOTE_REDSTONE_TRIGGER).build());

        QUANTUM_PORTER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "quantum_porter"),
                FabricBlockEntityTypeBuilder.create(QuantumPorterBlockEntity::new, ModBlocks.QUANTUM_PORTER).build());

        IO_TAG_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "io_tag"),
                FabricBlockEntityTypeBuilder.create(IOTagBlockEntity::new, ModBlocks.IO_TAG).build());

        DRONE_STATION_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Redos.MOD_ID, "drone_station"),
                FabricBlockEntityTypeBuilder.create(DroneStationBlockEntity::new, ModBlocks.DRONE_STATION).build());
    }
}
