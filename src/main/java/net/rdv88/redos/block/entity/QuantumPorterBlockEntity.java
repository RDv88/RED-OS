package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.block.custom.QuantumPorterBlock;
import net.rdv88.redos.util.TechNetwork;
import java.util.UUID;

public class QuantumPorterBlockEntity extends BlockEntity {
    private String networkId = "00000";
    private String name = "Quantum Porter";
    private String serial = UUID.randomUUID().toString();
    private int teleportCooldown = 0;
    private int activationTimer = 0;

    public QuantumPorterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_PORTER_ENTITY, pos, state);
    }

    public void setNetworkId(String id) { 
        this.networkId = id; 
        markUpdated(); 
    }
    
    public String getNetworkId() { return networkId; }
    
    public void setName(String name) { 
        this.name = name; 
        markUpdated(); 
    }
    
    public String getName() { return name; }
    public String getSerial() { return serial; }

    private void markUpdated() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void activate() {
        if (this.level == null) return;
        
        if (getBlockState().getValue(QuantumPorterBlock.HALF) == DoubleBlockHalf.UPPER) {
            BlockEntity lower = this.level.getBlockEntity(this.worldPosition.below());
            if (lower instanceof QuantumPorterBlockEntity porter) {
                porter.activate();
                return;
            }
        }

        this.activationTimer = 40; 
        setBlockActive(this.level, this.worldPosition, true);
        setBlockActive(this.level, this.worldPosition.above(), true);
        markUpdated();
    }

    private void setBlockActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.is(getBlockState().getBlock()) && state.hasProperty(QuantumPorterBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(QuantumPorterBlock.ACTIVE, active), 3);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, QuantumPorterBlockEntity blockEntity) {
        if (level.isClientSide()) return;
        
        if (state.getValue(QuantumPorterBlock.HALF) == DoubleBlockHalf.LOWER) {
            if (level.getGameTime() % 400 == 0) {
                TechNetwork.registerNode(level, pos, blockEntity.networkId, blockEntity.name, TechNetwork.NodeType.PORTER, blockEntity.serial);
            }

            if (blockEntity.teleportCooldown > 0) {
                blockEntity.teleportCooldown--;
            }

            if (blockEntity.activationTimer > 0) {
                blockEntity.activationTimer--;
                if (blockEntity.activationTimer == 0) {
                    blockEntity.setBlockActive(level, pos, false);
                    blockEntity.setBlockActive(level, pos.above(), false);
                }
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (state.getValue(QuantumPorterBlock.HALF) == DoubleBlockHalf.LOWER) {
            TechNetwork.removeNode(level, pos);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("networkId", this.networkId);
        output.putString("name", this.name);
        output.putString("serial", this.serial);
        output.putInt("activationTimer", this.activationTimer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.networkId = input.getStringOr("networkId", "00000");
        this.name = input.getStringOr("name", "Quantum Porter");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());
        this.activationTimer = input.getIntOr("activationTimer", 0);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Gebruik de menselijke namen voor Mojmap
        CompoundTag tag = new CompoundTag();
        tag.putString("networkId", this.networkId);
        tag.putString("name", this.name);
        return tag;
    }
}
