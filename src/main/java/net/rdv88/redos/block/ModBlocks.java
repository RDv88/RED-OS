package net.rdv88.redos.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.rdv88.redos.Redos;
import net.rdv88.redos.block.custom.*;

import java.util.function.Function;

public class ModBlocks {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("redos");

    public static final Block WIRELESS_IP_CAMERA = registerBlock("wireless_ip_camera",
            settings -> new WirelessCameraBlock(settings.strength(1.5f).noOcclusion()));
    public static final Block SMART_MOTION_SENSOR = registerBlock("smart_motion_sensor",
            settings -> new SmartMotionSensorBlock(settings.strength(1.5f).noOcclusion()));
    public static final Block SHORT_RANGE_TRANSMITTER = registerBlock("short_range_transmitter",
            settings -> new ShortRangeTransmitterBlock(settings.strength(2.0f).noOcclusion()));
    public static final Block LONG_RANGE_TRANSMITTER = registerBlock("long_range_transmitter",
            settings -> new LongRangeTransmitterBlock(settings.strength(2.0f).noOcclusion()));
    public static final Block REMOTE_REDSTONE_TRIGGER = registerBlock("remote_redstone_trigger",
            settings -> new RemoteRedstoneTriggerBlock(settings.strength(1.5f).noOcclusion()));
    public static final Block QUANTUM_PORTER = registerBlock("quantum_porter",
            settings -> new QuantumPorterBlock(settings.strength(15.0f).requiresCorrectToolForDrops().noOcclusion()));
    public static final Block IO_TAG = registerBlock("io_tag",
            settings -> new IOTagBlock(settings.strength(1.0f).noOcclusion()));
    public static final Block DRONE_STATION = registerBlock("drone_station",
            settings -> new DroneStationBlock(settings.strength(3.0f).noOcclusion()));

    private static Block registerBlock(String name, Function<BlockBehaviour.Properties, Block> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(Redos.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = factory.apply(BlockBehaviour.Properties.of().setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        registerBlockItem(name, block);
        return block;
    }

    private static void registerBlockItem(String name, Block block) {
        Identifier id = Identifier.fromNamespaceAndPath(Redos.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = new BlockItem(block, new Item.Properties().setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void registerModBlocks() {
        LOGGER.info("Registering Mod Blocks for " + Redos.MOD_ID);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(WIRELESS_IP_CAMERA);
            entries.accept(SMART_MOTION_SENSOR);
            entries.accept(SHORT_RANGE_TRANSMITTER);
            entries.accept(LONG_RANGE_TRANSMITTER);
            entries.accept(REMOTE_REDSTONE_TRIGGER);
            entries.accept(QUANTUM_PORTER);
            entries.accept(IO_TAG);
            entries.accept(DRONE_STATION);
        });
    }
}
