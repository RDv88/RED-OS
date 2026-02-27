package net.rdv88.redos.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.rdv88.redos.Redos;
import net.rdv88.redos.block.ModBlocks;

import java.util.function.Function;

public class ModItems {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("redos");
    
    public static final Item HANDHELD_DEVICE = registerItem("handheld_device", props -> new HandheldDeviceItem(props.stacksTo(1)));
    public static final Item PLASMA_PULSE_DISINTEGRATOR = registerItem("plasma_pulse_disintegrator", props -> new PlasmaPulseDisintegratorItem(props.stacksTo(1).durability(500)));
    public static final Item DRONE_UNIT = registerItem("drone_unit", props -> new Item(props.stacksTo(16)));

    private static Item registerItem(String name, Function<Item.Properties, Item> itemFactory) {
        Identifier id = Identifier.fromNamespaceAndPath(Redos.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        // CRITICAL PROTOCOL: Preserve Identity for model binding
        Item.Properties props = new Item.Properties().setId(key);
        return Registry.register(BuiltInRegistries.ITEM, key, itemFactory.apply(props));
    }

    public static void registerModItems() {
        LOGGER.info("Registering Mod Items for RED-OS 📦");

        // ADD TO CUSTOM MOD TAB
        ItemGroupEvents.modifyEntriesEvent(ModItemGroups.TECHNOLOGY_CRAFT_GROUP_KEY).register(entries -> {
            entries.accept(HANDHELD_DEVICE);
            entries.accept(PLASMA_PULSE_DISINTEGRATOR);
            entries.accept(ModBlocks.WIRELESS_IP_CAMERA);
            entries.accept(ModBlocks.SMART_MOTION_SENSOR);
            entries.accept(ModBlocks.SHORT_RANGE_TRANSMITTER);
            entries.accept(ModBlocks.LONG_RANGE_TRANSMITTER);
            entries.accept(ModBlocks.REMOTE_REDSTONE_TRIGGER);
            entries.accept(ModBlocks.QUANTUM_PORTER);
            entries.accept(ModBlocks.IO_TAG);
            entries.accept(DRONE_UNIT);
        });
    }
}
