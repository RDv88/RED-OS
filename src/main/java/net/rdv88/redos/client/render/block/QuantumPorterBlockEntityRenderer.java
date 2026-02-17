package net.rdv88.redos.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.block.custom.QuantumPorterBlock;
import net.rdv88.redos.block.entity.QuantumPorterBlockEntity;
import net.rdv88.redos.client.render.block.state.QuantumPorterRenderState;
import net.rdv88.redos.util.TechNetwork;

public class QuantumPorterBlockEntityRenderer implements BlockEntityRenderer<QuantumPorterBlockEntity, QuantumPorterRenderState> {
    public QuantumPorterBlockEntityRenderer() {}

    @Override
    public QuantumPorterRenderState createRenderState() {
        return new QuantumPorterRenderState();
    }

    @Override
    public void extractRenderState(QuantumPorterBlockEntity entity, QuantumPorterRenderState state, float delta, Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockPos basePos = entity.getBlockPos();
        DoubleBlockHalf half = DoubleBlockHalf.LOWER;
        if (entity.getBlockState().hasProperty(QuantumPorterBlock.HALF)) {
            half = entity.getBlockState().getValue(QuantumPorterBlock.HALF);
        }
        
        if (half == DoubleBlockHalf.UPPER) {
            basePos = basePos.below();
        }

        var node = TechNetwork.getNodes().get(basePos);
        String displayName = "Quantum Porter";
        if (node != null && node.customName != null && !node.customName.isEmpty()) {
            displayName = node.customName;
        } else if (entity.getName() != null && !entity.getName().isEmpty()) {
            displayName = entity.getName();
        }
        
        state.name = Component.literal(displayName);
        Direction facing = Direction.NORTH;
        if (entity.getBlockState().hasProperty(QuantumPorterBlock.FACING)) {
            facing = entity.getBlockState().getValue(QuantumPorterBlock.FACING);
        }
        state.facing = facing;
        state.isUpper = (half == DoubleBlockHalf.UPPER);
    }

    @Override
    public void submit(QuantumPorterRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState cameraState) {
        // CLEAN STATE - NO RENDERING YET
    }
}
