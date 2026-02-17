package net.rdv88.redos.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.rdv88.redos.block.entity.LongRangeTransmitterBlockEntity;
import net.rdv88.redos.block.entity.ModBlockEntities;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class LongRangeTransmitterBlock extends Block implements EntityBlock {
    public LongRangeTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        // INSTANT BIMP REGISTRATION
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof LongRangeTransmitterBlockEntity lrt) {
                TechNetwork.registerNode(level, pos, lrt.getNetworkId(), lrt.getName(), TechNetwork.NodeType.LONG_RANGE, lrt.getSerial());
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LongRangeTransmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (tType, pos, tState, blockEntity) -> {
            if (blockEntity instanceof LongRangeTransmitterBlockEntity be) LongRangeTransmitterBlockEntity.tick(level, pos, tState, be);
        };
    }
}
