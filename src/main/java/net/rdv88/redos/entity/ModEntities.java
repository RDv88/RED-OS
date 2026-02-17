package net.rdv88.redos.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.rdv88.redos.Redos;

public class ModEntities {
    public static final EntityType<WatcherEntity> WATCHER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Redos.MOD_ID, "watcher"),
            EntityType.Builder.of(WatcherEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Redos.MOD_ID, "watcher")))
    );

    public static final EntityType<DroneEntity> DRONE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Redos.MOD_ID, "drone"),
            EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Redos.MOD_ID, "drone")))
    );

    public static void register() {
    }
}
