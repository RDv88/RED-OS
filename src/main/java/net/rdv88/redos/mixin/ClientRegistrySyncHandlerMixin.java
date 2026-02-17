package net.rdv88.redos.mixin;

import net.fabricmc.fabric.impl.client.registry.sync.ClientRegistrySyncHandler;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientRegistrySyncHandler.class, remap = false)
public class ClientRegistrySyncHandlerMixin {

    /**
     * Intercept the registry sync check. 
     * By cancelling HEAD, we prevent Fabric from throwing the RemapException.
     * This allows us to connect to servers with mismatched blocks (like the missing server_rack),
     * giving our own version check system the chance to show the RED-OS Lockdown menu.
     */
    @Inject(method = "checkRemoteRemap", at = @At("HEAD"), cancellable = true)
    private static void onCheckRemoteRemap(RegistrySyncPayload payload, CallbackInfo ci) {
        ci.cancel(); // Silently allow mismatch, let Login Phase handle user warning
    }
}
