package net.rdv88.redos.mixin;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.rdv88.redos.block.ModBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public abstract class ItemBlockRenderTypesMixin {
    
    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
    private static void redos$forceTranslucency(BlockState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (state.is(ModBlocks.QUANTUM_PORTER) || 
            state.is(ModBlocks.REMOTE_REDSTONE_TRIGGER) || 
            state.is(ModBlocks.SMART_MOTION_SENSOR)) {
            cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
        }
    }
}