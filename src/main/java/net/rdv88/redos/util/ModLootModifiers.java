package net.rdv88.redos.util;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.rdv88.redos.item.ModItems;

public class ModLootModifiers {
    public static void registerLoot() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            // WARDEN DROP: 35% chance for Echo Core
            if (key.identifier().toString().equals("minecraft:entities/warden")) {
                LootPool.Builder pool = LootPool.lootPool()
                    .when(LootItemRandomChanceCondition.randomChance(0.35f))
                    .add(LootItem.lootTableItem(ModItems.WARDEN_CORE));
                tableBuilder.withPool(pool);
            }
        });
    }
}