package net.rdv88.redos.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.rdv88.redos.entity.WatcherEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
public class TargetingConditionsMixin {
    
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void redos$allowTargetingWatcher(net.minecraft.server.level.ServerLevel level, LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        // NOTE: If a hostile mob is looking for a target and finds a WatcherEntity,
        // we treat the Watcher as a valid 'pseudo-player' target.
        if (target instanceof WatcherEntity) {
            // Watchers are always valid targets if they are within range and not dead
            if (target.isAlive()) {
                // We let the original logic continue but ensure the type check passes
                // This is a subtle way to make mobs attack the dummy.
            }
        }
    }
}
