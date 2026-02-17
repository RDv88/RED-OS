package net.rdv88.redos.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.rdv88.redos.block.ModBlocks;

public class PorterScreenHelperBlock extends Block {
    // Full block hitbox for easy clicking, but NO collision (see ModBlocks registration)
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public PorterScreenHelperBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos porterPos = pos.below();
        BlockState porterState = level.getBlockState(porterPos);
        if (porterState.is(ModBlocks.QUANTUM_PORTER)) {
            // Direct call to the actual block logic
            return porterState.useWithoutItem(level, player, hit.withPosition(porterPos));
        }
        return InteractionResult.PASS;
    }
}
