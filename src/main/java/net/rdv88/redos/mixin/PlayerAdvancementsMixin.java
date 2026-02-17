package net.rdv88.redos.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.util.PermissionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void onAward(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && advancement.id().getNamespace().equals("redos")) {
            PermissionHandler.pushPermissions(player);
            
            // FORCE LINK LOGIC: If player gets "Main", grant the hidden logic for "High-Tech"
            if (advancement.id().getPath().equals("main") && player.level().getServer() != null) {
                Identifier techId = Identifier.fromNamespaceAndPath("redos", "hightech");
                AdvancementHolder techAdv = player.level().getServer().getAdvancements().get(techId);
                if (techAdv != null) {
                    player.getAdvancements().award(techAdv, "has_main_logic");
                }
            }
        }
    }

    @Inject(method = "revoke", at = @At("RETURN"))
    private void onRevoke(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && advancement.id().getNamespace().equals("redos")) {
            PermissionHandler.pushPermissions(player);
        }
    }
}