package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.util.TechNetwork;
import java.util.UUID;

public class ShortRangeTransmitterBlockEntity extends BlockEntity {
    public String name = "SR Transmitter";
    public String networkId = "00000";
    private String serial = UUID.randomUUID().toString();

    public ShortRangeTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHORT_RANGE_TRANSMITTER_BLOCK_ENTITY, pos, state);
    }

    public void setName(String name) { this.name = name; setChanged(); }
    public String getName() { return name; }
    public void setNetworkId(String id) { this.networkId = id; setChanged(); }
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }

    public static void tick(Level level, BlockPos pos, BlockState state, ShortRangeTransmitterBlockEntity blockEntity) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 400 == 0) {
            TechNetwork.registerNode(level, pos, blockEntity.networkId, blockEntity.name, TechNetwork.NodeType.SHORT_RANGE, blockEntity.serial);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("name", name);
        output.putString("networkId", networkId);
        output.putString("serial", serial);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.name = input.getStringOr("name", "SR Transmitter");
        this.networkId = input.getStringOr("networkId", "00000");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());
    }
}
