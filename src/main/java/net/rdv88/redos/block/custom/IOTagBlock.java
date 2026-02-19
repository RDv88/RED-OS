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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.rdv88.redos.block.ModBlocks;
import net.rdv88.redos.block.entity.IOTagBlockEntity;
import net.rdv88.redos.block.entity.ModBlockEntities;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class IOTagBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<IOTagBlock> CODEC = simpleCodec(IOTagBlock::new);

    protected static final VoxelShape NORTH_SHAPE = Block.box(4, 4, 0, 12, 12, 1);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(4, 4, 15, 12, 12, 16);
    protected static final VoxelShape EAST_SHAPE = Block.box(15, 4, 4, 16, 12, 12);
    protected static final VoxelShape WEST_SHAPE = Block.box(0, 4, 4, 1, 12, 12);

    @Override
    public MapCodec<? extends IOTagBlock> codec() { return CODEC; }

    public IOTagBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SOUTH_SHAPE;
            case SOUTH -> NORTH_SHAPE;
            case WEST -> EAST_SHAPE;
            case EAST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IOTagBlockEntity tag) {
                TechNetwork.registerNode(level, pos, tag.getNetworkId(), tag.getName(), TechNetwork.NodeType.IO_TAG, tag.getSerial());
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.isClientSide()) {
            TechNetwork.removeNode(level, pos);
            if (!moved) {
                Block.popResource(level, pos, new ItemStack(ModBlocks.IO_TAG));
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attachedDir = state.getValue(FACING).getOpposite();
        BlockPos supportPos = pos.relative(attachedDir);
        BlockState supportState = level.getBlockState(supportPos);
        return supportState.hasBlockEntity();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isVertical()) return null;

        BlockPos targetPos = context.getClickedPos().relative(clickedFace.getOpposite());
        if (context.getLevel().getBlockState(targetPos).hasBlockEntity()) {
            return this.defaultBlockState()
                    .setValue(FACE, AttachFace.WALL)
                    .setValue(FACING, clickedFace);
        }
        return null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IOTagBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (level1, pos1, state1, be) -> {
            if (be instanceof IOTagBlockEntity tag) IOTagBlockEntity.tick(level1, pos1, state1, tag);
        };
    }
}
