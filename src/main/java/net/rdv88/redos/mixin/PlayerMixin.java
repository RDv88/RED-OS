package net.rdv88.redos.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.rdv88.redos.util.CameraViewHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "isSpectator", at = @At("HEAD"), cancellable = true)
    private void allowMobSpawningWhileViewingCamera(CallbackInfoReturnable<Boolean> cir) {
        // NOTE: This Mixin "fools" the spawning logic. If the player is viewing a camera,
        // we report isSpectator as FALSE to the spawner, so mobs continue to spawn around the camera location.
        if ((Object) this instanceof ServerPlayer serverPlayer) {
            if (CameraViewHandler.isViewingCamera(serverPlayer)) {
                // We only want to return false for spawning logic. 
                // Minecraft's natural spawner checks isSpectator() to exclude players.
                // By returning false here, the spectator player at the camera location 
                // is counted as a valid survival player for spawning.
                cir.setReturnValue(false);
            }
        }
    }
}
