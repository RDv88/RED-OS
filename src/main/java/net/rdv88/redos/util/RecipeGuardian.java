package net.rdv88.redos.util;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.rdv88.redos.Redos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeGuardian {
    private static final Map<String, List<String>> GUARDIAN_MAP = new HashMap<>();

    static {
        GUARDIAN_MAP.put("main", List.of(
            "handheld_device", "short_range_transmitter", "long_range_transmitter", 
            "wireless_ip_camera", "smart_motion_sensor", "remote_redstone_trigger"
        ));
        GUARDIAN_MAP.put("hightech", List.of(
            "plasma_pulse_disintegrator", "quantum_porter", 
            "drone_station", "io_tag", "drone_unit"
        ));
    }

    public static void checkAndFix(ServerPlayer player, MinecraftServer server) {
        GUARDIAN_MAP.forEach((advPath, recipes) -> {
            Identifier advId = Identifier.fromNamespaceAndPath(Redos.MOD_ID, advPath);
            AdvancementHolder adv = server.getAdvancements().get(advId);
            
            if (adv != null && player.getAdvancements().getOrStartProgress(adv).isDone()) {
                List<ResourceKey<Recipe<?>>> keysToUnlock = new ArrayList<>();
                for (String path : recipes) {
                    keysToUnlock.add(ResourceKey.create(Registries.RECIPE, 
                        Identifier.fromNamespaceAndPath(Redos.MOD_ID, path)));
                }
                
                // Direct unlocking using keys
                for (ResourceKey<Recipe<?>> key : keysToUnlock) {
                    player.getRecipeBook().add(key);
                }
            }
        });
    }
}
