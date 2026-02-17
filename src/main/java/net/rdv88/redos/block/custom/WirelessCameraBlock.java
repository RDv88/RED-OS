package net.rdv88.redos.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.rdv88.redos.block.entity.WirelessCameraBlockEntity;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class WirelessCameraBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<WirelessCameraBlock> CODEC = simpleCodec(WirelessCameraBlock::new);

    @Override
    public MapCodec<? extends WirelessCameraBlock> codec() { return CODEC; }

    public WirelessCameraBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WirelessCameraBlockEntity cam) {
                // AUTO-ORIENTATION: Calculate the look angle from camera to player's eyes
                if (placer != null) {
                    double dx = placer.getX() - (pos.getX() + 0.5);
                    double dy = (placer.getY() + placer.getEyeHeight()) - (pos.getY() + 0.5);
                    double dz = placer.getZ() - (pos.getZ() + 0.5);
                    
                    // Minecraft rotation math
                    float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
                    float pitch = (float) (-(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI)));
                    
                    cam.setRotation(yaw, pitch);
                }
                
                TechNetwork.registerNode(level, pos, cam.getNetworkId(), cam.getCameraName(), TechNetwork.NodeType.CAMERA, cam.getSerial());
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide()) {
            TechNetwork.removeNode(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessCameraBlockEntity(pos, state);
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
        builder.add(FACING, FACE);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (tType, pos, tState, blockEntity) -> {
            if (blockEntity instanceof WirelessCameraBlockEntity be) WirelessCameraBlockEntity.tick(level, pos, tState, be);
        };
    }
}
