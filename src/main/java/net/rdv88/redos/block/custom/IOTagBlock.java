package net.rdv88.redos.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.rdv88.redos.block.entity.IOTagBlockEntity;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class IOTagBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<IOTagBlock> CODEC = simpleCodec(IOTagBlock::new);

    protected static final VoxelShape NORTH_SHAPE = Block.box(4, 4, 0, 12, 12, 1);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(4, 4, 15, 12, 12, 16);
    protected static final VoxelShape EAST_SHAPE = Block.box(15, 4, 4, 16, 12, 12);
    protected static final VoxelShape WEST_SHAPE = Block.box(0, 4, 4, 1, 12, 12);

    @Override public MapCodec<? extends IOTagBlock> codec() { return CODEC; }

    public IOTagBlock(Properties props) {
        super(props);
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
            if (be instanceof IOTagBlockEntity tag) tag.updateInventoryStats();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IOTagBlockEntity tag) {
                ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
                if (player.isShiftKeyDown()) {
                    if (stack.isEmpty()) {
                        tag.setSettings(ItemStack.EMPTY);
                        player.displayClientMessage(Component.literal("§eIO Tag filter cleared."), true);
                    } else {
                        ItemStack filter = stack.copy(); filter.setCount(1);
                        tag.setSettings(filter);
                        player.displayClientMessage(Component.literal("§aIO Tag filter set to: §f" + filter.getHoverName().getString()), true);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.isClientSide()) TechNetwork.removeNode(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attachedDir = state.getValue(FACING).getOpposite();
        BlockPos supportPos = pos.relative(attachedDir);
        return level.getBlockState(supportPos).hasBlockEntity();
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IOTagBlockEntity(pos, state); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, FACE); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) return null;
        BlockPos supportPos = ctx.getClickedPos().relative(face.getOpposite());
        if (ctx.getLevel().getBlockState(supportPos).hasBlockEntity()) {
            return this.defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, face);
        }
        return null;
    }
}