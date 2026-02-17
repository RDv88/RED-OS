package net.rdv88.redos.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.rdv88.redos.util.PermissionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    @Inject(method = "setGameMode", at = @At("RETURN"))
    private void onSetGameMode(GameType gameMode, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            // When game mode successfully changes, push updated permissions
            PermissionHandler.pushPermissions((ServerPlayer)(Object)this);
        }
    }
}
