package net.rdv88.redos.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.rdv88.redos.block.ModBlocks;
import net.rdv88.redos.block.entity.QuantumPorterBlockEntity;
import net.rdv88.redos.util.TechNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jetbrains.annotations.Nullable;

public class QuantumPorterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<QuantumPorterBlock> CODEC = simpleCodec(QuantumPorterBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    protected static final VoxelShape BASE_SHAPE = Block.box(1, 0, 1, 15, 2.5, 15);
    protected static final VoxelShape SHAPE_EAST = Block.box(3, 5, 2, 5, 11, 14);
    protected static final VoxelShape SHAPE_WEST = Block.box(11, 5, 2, 13, 11, 14);
    protected static final VoxelShape SHAPE_SOUTH = Block.box(2, 5, 3, 14, 11, 5);
    protected static final VoxelShape SHAPE_NORTH = Block.box(2, 5, 11, 14, 11, 13);

    public QuantumPorterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return BASE_SHAPE;
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (!level.isClientSide()) {
            BlockEntity lowerBE = level.getBlockEntity(pos);
            String customName = itemStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? itemStack.getHoverName().getString() : "Quantum Porter";

            if (lowerBE instanceof QuantumPorterBlockEntity porter) {
                porter.setName(customName);
                TechNetwork.registerNode(level, pos, porter.getNetworkId(), porter.getName(), TechNetwork.NodeType.PORTER, porter.getSerial());
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.isClientSide()) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos dbPos = (half == DoubleBlockHalf.LOWER) ? pos : pos.below();
            
            TechNetwork.removeNode(level, dbPos);

            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() < level.getHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            return aboveState.isAir() || aboveState.canBeReplaced();
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, ACTIVE);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER) ? 
            (level1, pos1, state1, be) -> {
                if (be instanceof QuantumPorterBlockEntity p) QuantumPorterBlockEntity.tick(level1, pos1, state1, p);
            } : null;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            Direction facing = state.getValue(FACING);
            double offset = -0.25;
            double px = x + (facing.getStepX() * offset);
            double pz = z + (facing.getStepZ() * offset);
            
            if (random.nextInt(2) == 0) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, 
                    px + (random.nextDouble() - 0.5) * 0.9, y + (random.nextDouble() - 0.5) * 0.8, pz + (random.nextDouble() - 0.5) * 0.9, 0, 0, 0);
            }

            if (state.getValue(ACTIVE)) {
                for (int i = 0; i < 4; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, 
                        px + (random.nextDouble() - 0.5) * 0.5, y + (random.nextDouble() - 0.5) * 0.5, pz + (random.nextDouble() - 0.5) * 0.5, 
                        (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
                }
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            BlockPos targetPos = (state.getValue(HALF) == DoubleBlockHalf.LOWER) ? pos : pos.below();
            ClientPlayNetworking.send(new net.rdv88.redos.network.payload.RequestPorterGuiPayload(targetPos));
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumPorterBlockEntity(pos, state);
    }
}
