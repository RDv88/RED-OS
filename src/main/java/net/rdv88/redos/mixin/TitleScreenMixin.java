package net.rdv88.redos.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    // Startup block removed. Version checks are now handled via RED-OS Grenscontrole 
    // during server connection (Configuration Phase).
    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        // No GUI overrides, allowing smooth entry to singleplayer.
    }
}