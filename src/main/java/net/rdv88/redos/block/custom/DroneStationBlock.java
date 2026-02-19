package net.rdv88.redos.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.rdv88.redos.block.ModBlocks;
import net.rdv88.redos.block.entity.DroneStationBlockEntity;
import net.rdv88.redos.block.entity.ModBlockEntities;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class DroneStationBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<DroneStationBlock> CODEC = simpleCodec(DroneStationBlock::new);

    protected static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public DroneStationBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DroneStationBlockEntity hub) {
                // Drop items from slots that are NOT currently locked (launched drones stay drones)
                SimpleContainer dropInv = new SimpleContainer(5);
                for (int i = 0; i < 5; i++) {
                    if (!hub.isSlotLocked(i)) {
                        dropInv.setItem(i, hub.getInventory().getItem(i).copy());
                    }
                }
                Containers.dropContents(level, pos, dropInv);
            }
            // 1. Unregister from Network
            TechNetwork.removeNode(level, pos);
            // 2. Drop the Hub itself
            if (!moved) {
                Block.popResource(level, pos, new ItemStack(ModBlocks.DRONE_STATION));
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DroneStationBlockEntity hub) {
                player.openMenu(hub);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DroneStationBlockEntity(pos, state); }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.DRONE_STATION_BLOCK_ENTITY, DroneStationBlockEntity::tick);
    }
}
