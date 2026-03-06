package net.rdv88.redos.item;

import net.minecraft.util.Util;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.rdv88.redos.Redos;

import java.util.EnumMap;

public class ModArmorMaterials {
    /**
     * ION_EXO Armor Material
     * Defense: 10 (Chest), 4.0 Toughness, 0.2 Knockback Resistance
     * Enchantability: 20
     */
    public static final ArmorMaterial ION_EXO = new ArmorMaterial(
        45, 
        Util.make(new EnumMap<>(ArmorType.class), map -> {
            map.put(ArmorType.BOOTS, 3);
            map.put(ArmorType.LEGGINGS, 6);
            map.put(ArmorType.CHESTPLATE, 10); 
            map.put(ArmorType.HELMET, 3);
        }),
        20, 
        SoundEvents.ARMOR_EQUIP_NETHERITE,
        4.0F, 
        0.2F, 
        net.minecraft.tags.ItemTags.REPAIRS_NETHERITE_ARMOR, 
        ResourceKey.create(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("minecraft", "equipment_asset")), Identifier.fromNamespaceAndPath("redos", "ion_exo_chestplate"))
    );
}
