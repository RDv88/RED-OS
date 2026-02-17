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
import net.rdv88.redos.block.entity.ModBlockEntities;
import net.rdv88.redos.block.entity.ShortRangeTransmitterBlockEntity;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

public class ShortRangeTransmitterBlock extends Block implements EntityBlock {
    public ShortRangeTransmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        // INSTANT BIMP REGISTRATION
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShortRangeTransmitterBlockEntity srt) {
                TechNetwork.registerNode(level, pos, srt.getNetworkId(), srt.getName(), TechNetwork.NodeType.SHORT_RANGE, srt.getSerial());
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShortRangeTransmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (tType, pos, tState, blockEntity) -> {
            if (blockEntity instanceof ShortRangeTransmitterBlockEntity be) ShortRangeTransmitterBlockEntity.tick(level, pos, tState, be);
        };
    }
}