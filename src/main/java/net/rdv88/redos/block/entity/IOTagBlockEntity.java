package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.util.TechNetwork;
import java.util.HashMap;
import java.util.UUID;

public class IOTagBlockEntity extends BlockEntity {
    private String name = "IO Tag";
    private String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    private ItemStack filterItem = ItemStack.EMPTY;

    public IOTagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_TAG_BLOCK_ENTITY, pos, state);
    }

    public void setSettings(ItemStack filter) {
        this.filterItem = filter;
        markUpdated();
    }

    public ItemStack getFilterItem() { return filterItem; }

    public void setName(String name) { 
        this.name = name; 
        markUpdated(); 
    }
    
    public String getName() { return name; }
    
    public void setNetworkId(String id) { 
        this.networkId = id; 
        markUpdated(); 
    }
    
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }

    private void markUpdated() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            TechNetwork.registerNode(level, worldPosition, this.networkId, this.name, TechNetwork.NodeType.IO_TAG, this.serial, new HashMap<>());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("name", name);
        output.putString("networkId", networkId);
        output.putString("serial", serial);
        if (!filterItem.isEmpty()) {
            output.store("filter", ItemStack.CODEC, filterItem);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.name = input.getStringOr("name", "IO Tag");
        this.networkId = input.getStringOr("networkId", "00000");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());
        input.read("filter", ItemStack.CODEC).ifPresent(i -> this.filterItem = i);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", this.name);
        tag.putString("networkId", this.networkId);
        return tag;
    }
}
