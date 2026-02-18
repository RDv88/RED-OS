package net.rdv88.redos.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.rdv88.redos.block.entity.IOTagBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class ContainerChangeMixin {

    @Shadow public abstract BlockPos getBlockPos();
    @Shadow public abstract Level getLevel();

    @Inject(method = "setChanged", at = @At("TAIL"))
    private void onSetChanged(CallbackInfo ci) {
        Level level = getLevel();
        BlockPos pos = getBlockPos();

        // 1. Safety check
        if (level == null || level.isClientSide()) return;
        
        // 2. Prevent infinite loops (don't react to the IO Tag's own changes)
        if ((Object)this instanceof IOTagBlockEntity) return;

        // 3. Notify adjacent IO Tags
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor instanceof IOTagBlockEntity ioTag) {
                ioTag.updateInventoryStats();
            }
        }
    }
}
