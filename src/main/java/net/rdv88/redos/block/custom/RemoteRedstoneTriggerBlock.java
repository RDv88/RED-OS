package net.rdv88.redos.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.rdv88.redos.block.ModBlocks;
import net.rdv88.redos.block.entity.ModBlockEntities;
import net.rdv88.redos.block.entity.RemoteRedstoneTriggerBlockEntity;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class RemoteRedstoneTriggerBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RemoteRedstoneTriggerBlock> CODEC = simpleCodec(RemoteRedstoneTriggerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<? extends RemoteRedstoneTriggerBlock> codec() { return CODEC; }

    public RemoteRedstoneTriggerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL)
            .setValue(POWERED, false));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RemoteRedstoneTriggerBlockEntity trigger) {
                TechNetwork.registerNode(level, pos, trigger.getNetworkId(), trigger.getName(), TechNetwork.NodeType.TRIGGER, trigger.getSerial());
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.isClientSide()) {
            TechNetwork.removeNode(level, pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        Direction direction = getConnectedDirection(state).getOpposite();
        BlockPos supportPos = pos.relative(direction);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, direction.getOpposite());
    }

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteRedstoneTriggerBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState blockState;
            if (direction.getAxis() == Direction.Axis.Y) {
                blockState = this.defaultBlockState()
                        .setValue(FACE, direction == Direction.UP ? AttachFace.FLOOR : AttachFace.CEILING)
                        .setValue(FACING, context.getHorizontalDirection().getOpposite());
            } else {
                blockState = this.defaultBlockState()
                        .setValue(FACE, AttachFace.WALL)
                        .setValue(FACING, direction.getOpposite());
            }
            if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockState;
            }
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, POWERED);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos1, state1, be) -> {
            if (be instanceof RemoteRedstoneTriggerBlockEntity trigger) RemoteRedstoneTriggerBlockEntity.tick(level1, pos1, state1, trigger);
        };
    }
}
