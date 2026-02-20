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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.rdv88.redos.block.ModBlocks;
import net.rdv88.redos.block.entity.LongRangeTransmitterBlockEntity;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class LongRangeTransmitterBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<LongRangeTransmitterBlock> CODEC = simpleCodec(LongRangeTransmitterBlock::new);

    // Precise VoxelShapes matching the 3D model (Base + Long Antenna)
    protected static final VoxelShape FLOOR_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(2, 0, 2, 14, 4, 14),    // Base
            Block.box(7, 4, 7, 9, 16, 9)      // Antenna (clipped at block edge)
    );
    protected static final VoxelShape CEILING_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(2, 12, 2, 14, 16, 14),  // Base
            Block.box(7, 0, 7, 9, 12, 9)      // Antenna
    );
    protected static final VoxelShape NORTH_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(2, 2, 12, 14, 14, 16),  // Base
            Block.box(7, 7, 0, 9, 9, 12)      // Antenna
    );
    protected static final VoxelShape SOUTH_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(2, 2, 0, 14, 14, 4),    // Base
            Block.box(7, 7, 4, 9, 9, 16)      // Antenna
    );
    protected static final VoxelShape EAST_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(0, 2, 2, 4, 14, 14),    // Base
            Block.box(4, 7, 7, 16, 9, 9)      // Antenna
    );
    protected static final VoxelShape WEST_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(12, 2, 2, 16, 14, 14),  // Base
            Block.box(0, 7, 7, 12, 9, 9)      // Antenna
    );

    @Override
    public MapCodec<? extends LongRangeTransmitterBlock> codec() { return CODEC; }

    public LongRangeTransmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(FACE)) {
            case FLOOR -> switch (facing) {
                case EAST, WEST -> net.minecraft.world.phys.shapes.Shapes.or(Block.box(2, 0, 4, 14, 2, 12), Block.box(7, 2, 7, 9, 16, 9));
                default -> net.minecraft.world.phys.shapes.Shapes.or(Block.box(4, 0, 2, 12, 2, 14), Block.box(7, 2, 7, 9, 16, 9));
            };
            case CEILING -> switch (facing) {
                case EAST, WEST -> net.minecraft.world.phys.shapes.Shapes.or(Block.box(2, 14, 4, 14, 16, 12), Block.box(7, 0, 7, 9, 14, 9));
                default -> net.minecraft.world.phys.shapes.Shapes.or(Block.box(4, 14, 2, 12, 16, 14), Block.box(7, 0, 7, 9, 14, 9));
            };
            case WALL -> switch (facing) {
                case NORTH -> NORTH_SHAPE;
                case SOUTH -> SOUTH_SHAPE;
                case EAST -> EAST_SHAPE;
                case WEST -> WEST_SHAPE;
                default -> NORTH_SHAPE;
            };
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LongRangeTransmitterBlockEntity lr) {
                TechNetwork.registerNode(level, pos, lr.getNetworkId(), lr.getName(), TechNetwork.NodeType.LONG_RANGE, lr.getSerial());
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState blockState;

        // PRIORITIZE THE CLICKED FACE: Ensures it goes where you click, not where you look
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            blockState = this.defaultBlockState()
                    .setValue(FACE, clickedFace == Direction.UP ? AttachFace.FLOOR : AttachFace.CEILING)
                    .setValue(FACING, context.getHorizontalDirection().getOpposite());
        } else {
            blockState = this.defaultBlockState()
                    .setValue(FACE, AttachFace.WALL)
                    .setValue(FACING, clickedFace);
        }

        if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
            return blockState;
        }

        // FALLBACK: Traditional logic
        for (Direction direction : context.getNearestLookingDirections()) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LongRangeTransmitterBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }
}
