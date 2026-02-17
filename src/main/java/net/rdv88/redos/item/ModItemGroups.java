package net.rdv88.redos.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.rdv88.redos.Redos;

public class ModItemGroups {
    // THE ULTIMATE SEARCH FIX: Use the MOD_ID as the group ID itself
    public static final ResourceKey<CreativeModeTab> TECHNOLOGY_CRAFT_GROUP_KEY = ResourceKey.create(
            BuiltInRegistries.CREATIVE_MODE_TAB.key(), 
            Identifier.fromNamespaceAndPath(Redos.MOD_ID, "redos")
    );
    
    public static final CreativeModeTab TECHNOLOGY_CRAFT_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(ModItems.HANDHELD_DEVICE))
            .title(Component.translatable("itemGroup.redos.group"))
            .build();

    public static void registerItemGroups() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TECHNOLOGY_CRAFT_GROUP_KEY, TECHNOLOGY_CRAFT_GROUP);
    }
}
