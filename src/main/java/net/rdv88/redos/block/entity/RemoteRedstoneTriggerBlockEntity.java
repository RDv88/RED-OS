package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.block.custom.RemoteRedstoneTriggerBlock;
import net.rdv88.redos.util.TechNetwork;
import java.util.UUID;

public class RemoteRedstoneTriggerBlockEntity extends BlockEntity {
    public String name = "Remote Trigger";
    public String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    private int triggerTicks = 0;

    public RemoteRedstoneTriggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REMOTE_REDSTONE_TRIGGER_BLOCK_ENTITY, pos, state);
    }

    public void setName(String name) { this.name = name; setChanged(); }
    public String getName() { return name; }
    public void setNetworkId(String id) { this.networkId = id; setChanged(); }
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }
    
    public void trigger() { 
        this.triggerTicks = 20; 
        setChanged(); 
    }
    
    public boolean isActive() { return triggerTicks > 0; }

    public static void tick(Level level, BlockPos pos, BlockState state, RemoteRedstoneTriggerBlockEntity blockEntity) {
        if (level.isClientSide()) return;
        
        if (level.getGameTime() % 400 == 0) {
            TechNetwork.registerNode(level, pos, blockEntity.networkId, blockEntity.name, TechNetwork.NodeType.TRIGGER, blockEntity.serial);
        }

        boolean shouldBePowered = blockEntity.triggerTicks > 0;
        boolean isCurrentlyPowered = state.getValue(RemoteRedstoneTriggerBlock.POWERED);

        if (shouldBePowered != isCurrentlyPowered) {
            level.setBlock(pos, state.setValue(RemoteRedstoneTriggerBlock.POWERED, shouldBePowered), 3);
            level.updateNeighborsAt(pos, state.getBlock());
        }

        if (blockEntity.triggerTicks > 0) {
            blockEntity.triggerTicks--;
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
        this.name = input.getStringOr("name", "Remote Trigger");
        this.networkId = input.getStringOr("networkId", "00000");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());
    }
}