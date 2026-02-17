package net.rdv88.redos.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.InteractionHand;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void handleTechRendering(LivingEntity entity, ItemStack stack, ItemDisplayContext renderType, com.mojang.blaze3d.vertex.PoseStack matrices, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof HandheldScreen) {
            ci.cancel();
            return;
        }

        if (renderType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || renderType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            if (stack.is(ModItems.PLASMA_PULSE_DISINTEGRATOR) && entity.isUsingItem() && entity.getUseItem() == stack) {
                float side = renderType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ? -1.0f : 1.0f;
                // Z lowered from 0.45 to 0.35 to move it slightly more forward
                matrices.translate(side * 0.45f, 0.05f, 0.35f);
            }
        }
    }
}
