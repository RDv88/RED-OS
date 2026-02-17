package net.rdv88.redos.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.rdv88.redos.item.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
    public PlayerModelMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void redos$applyCustomPoses(AvatarRenderState state, CallbackInfo ci) {
        // 1. WATCHER POSE (Already existed)
        if (state.nameTag != null && state.nameTag.getString().contains("(Remote Link)")) {
            this.head.xRot = 0.6f;
            this.hat.xRot = 0.6f;
            this.rightArm.xRot = -0.9f;
            this.rightArm.yRot = -0.4f;
            this.leftArm.xRot = -0.9f;
            this.leftArm.yRot = 0.4f;
        }

        // 2. DYNAMIC DISINTEGRATOR POSE
        // Only raise arms when actually using (charging) the item
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().is(ModItems.PLASMA_PULSE_DISINTEGRATOR) && player.isUsingItem()) {
            // Rifle Aiming Pose
            this.rightArm.xRot = -1.5f; 
            this.rightArm.yRot = -0.1f;
            
            this.leftArm.xRot = -1.2f;
            this.leftArm.yRot = 0.4f;
            
            this.head.xRot += 0.1f;
        }
    }
}