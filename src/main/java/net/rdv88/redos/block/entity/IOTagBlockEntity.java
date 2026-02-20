package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.util.TechNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IOTagBlockEntity extends BlockEntity {
    private String name = "IO Tag";
    private String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    private ItemStack filterItem = ItemStack.EMPTY;
    
    private int lastItemCount = -1;
    private int lastFreeSpace = -1;

    public IOTagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_TAG_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IOTagBlockEntity blockEntity) {
        // No periodic ticking needed
    }

    public void updateInventoryStats() {
        if (level == null || level.isClientSide()) return;
        
        int currentCount = 0;
        int currentFreeSpace = 0;
        boolean foundContainer = false;
        
        // Scan adjacent blocks for a container
        for (Direction dir : Direction.values()) {
            BlockPos sidePos = worldPosition.relative(dir);
            BlockEntity be = level.getBlockEntity(sidePos);
            
            if (be instanceof Container container) {
                // IMPROVED SCAN: Handle Double Chests correctly in 1.21.11
                Container fullInventory = container;
                BlockState sideState = level.getBlockState(sidePos);
                if (sideState.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest) {
                    fullInventory = net.minecraft.world.level.block.ChestBlock.getContainer(chest, sideState, level, sidePos, true);
                }

                if (fullInventory != null) {
                    for (int i = 0; i < fullInventory.getContainerSize(); i++) {
                        ItemStack stack = fullInventory.getItem(i);
                        if (stack.isEmpty()) {
                            currentFreeSpace += 64;
                        } else {
                            currentCount += stack.getCount();
                            currentFreeSpace += (stack.getMaxStackSize() - stack.getCount());
                        }
                    }
                }
                foundContainer = true;
                // REMOVED BREAK: Continue scanning to find all parts of complex container setups
            }
        }
        
        if (!foundContainer) {
            currentCount = 0;
            currentFreeSpace = 0;
        }

        // Only register in network if data actually changed to save bandwidth
        if (currentCount != lastItemCount || currentFreeSpace != lastFreeSpace) {
            lastItemCount = currentCount;
            lastFreeSpace = currentFreeSpace;
            registerInNetwork();
        }
    }

    public void setSettings(ItemStack filter) {
        this.filterItem = filter;
        markUpdated();
    }

    public ItemStack getFilterItem() { return filterItem; }
    public void setName(String name) { this.name = name; markUpdated(); }
    public String getName() { return name; }
    public void setNetworkId(String id) { this.networkId = id; markUpdated(); }
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }

    private void markUpdated() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            updateInventoryStats();
        }
    }

    private void registerInNetwork() {
        if (level == null) return;
        Map<String, Object> settings = new HashMap<>();
        settings.put("item_count", lastItemCount);
        settings.put("free_space", lastFreeSpace);
        if (!filterItem.isEmpty()) {
            settings.put("filter_item", filterItem.getItem().toString());
        }
        TechNetwork.registerNode(level, worldPosition, this.networkId, this.name, TechNetwork.NodeType.IO_TAG, this.serial, settings);
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

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", this.name);
        tag.putString("networkId", this.networkId);
        return tag;
    }
}
