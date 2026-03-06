package net.rdv88.redos.mixin;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EquipmentLayerRenderer.class)
public class EquipmentLayerRendererMixin {

    @ModifyVariable(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("STORE"),
            ordinal = 0
    )
    private ArmorTrim removeTrimForWings(ArmorTrim originalTrim, EquipmentClientInfo.LayerType layerType) {
        if (layerType == EquipmentClientInfo.LayerType.WINGS) {
            return null; // Force no trim when rendering elytra wings
        }
        return originalTrim;
    }
}