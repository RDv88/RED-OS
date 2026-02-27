package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.rdv88.redos.util.TechNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IOTagBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-iotag");
    private String name = "IO Tag";
    private String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    private ItemStack filterItem = ItemStack.EMPTY;
    private int lastItemCount = -1;
    private int lastFreeSpace = -1;

    public IOTagBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.IO_TAG_BLOCK_ENTITY, pos, state); }

    public void setName(String name) { this.name = name; markUpdated(); }
    public void setNetworkId(String id) { this.networkId = id; markUpdated(); }
    public String getSerial() { return serial; }
    public String getName() { return name; }
    public String getNetworkId() { return networkId; }

    public void updateInventoryStats() {
        if (level == null || level.isClientSide()) return;
        int count = 0, free = 0;
        boolean found = false;
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(worldPosition.relative(dir));
            if (be instanceof Container c) {
                Container inv = getFullInventory(c, worldPosition.relative(dir));
                if (inv != null) {
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty()) free += 64; 
                        else { 
                            count += stack.getCount(); 
                            int spaceInSlot = stack.getMaxStackSize() - stack.getCount();
                            if (spaceInSlot > 0) free += spaceInSlot;
                        }
                    }
                }
                found = true;
            }
        }
        if (!found) { count = 0; free = 0; }
        if (count != lastItemCount || free != lastFreeSpace) { 
            lastItemCount = count; 
            lastFreeSpace = free; 
            registerInNetwork(); 
            // RESTORED: Notify Master of the change
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                net.rdv88.redos.util.LogisticsEngine.onNetworkUpdate(sl, worldPosition);
            }
        }
    }

    private Container getFullInventory(Container c, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest) {
            return net.minecraft.world.level.block.ChestBlock.getContainer(chest, s, level, pos, true);
        }
        return c;
    }

    public ItemStack secureRemoteProcess(boolean isPickup, ItemStack stackToDrop) {
        if (level == null || level.isClientSide() || !(level instanceof ServerLevel sl)) return isPickup ? ItemStack.EMPTY : stackToDrop;
        int centerCX = worldPosition.getX() >> 4, centerCZ = worldPosition.getZ() >> 4;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) sl.setChunkForced(centerCX + x, centerCZ + z, true);
        ItemStack result = isPickup ? ItemStack.EMPTY : stackToDrop;
        try {
            for (Direction dir : Direction.values()) {
                BlockEntity be = level.getBlockEntity(worldPosition.relative(dir));
                if (be instanceof Container c) {
                    Container inv = getFullInventory(c, worldPosition.relative(dir));
                    if (inv != null) { if (isPickup) result = executePickup(inv); else result = executeDrop(inv, stackToDrop); }
                }
                if (isPickup && !result.isEmpty()) break;
                if (!isPickup && result.isEmpty()) break;
            }
            updateInventoryStats();
        } finally {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) sl.setChunkForced(centerCX + x, centerCZ + z, false);
        }
        return result;
    }

    private ItemStack executePickup(Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) { ItemStack stack = inv.getItem(i).split(64); inv.setChanged(); return stack; }
        return ItemStack.EMPTY;
    }

    private ItemStack executeDrop(Container inv, ItemStack stack) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) { inv.setItem(i, stack); inv.setChanged(); return ItemStack.EMPTY; }
            else if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int canAdd = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(canAdd); stack.shrink(canAdd);
                if (stack.isEmpty()) { inv.setChanged(); return ItemStack.EMPTY; }
            }
        }
        return stack;
    }

    public void setSettings(ItemStack f) { this.filterItem = f; markUpdated(); }
    private void markUpdated() { setChanged(); if (level != null) { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); updateInventoryStats(); } }
    private void registerInNetwork() { if (level == null) return; Map<String, Object> s = new HashMap<>(); s.put("item_count", lastItemCount); s.put("free_space", lastFreeSpace); if (!filterItem.isEmpty()) s.put("filter_item", filterItem.getItem().toString()); TechNetwork.registerNode(level, worldPosition, networkId, name, TechNetwork.NodeType.IO_TAG, serial, s); }
    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput o) { super.saveAdditional(o); o.putString("name", name); o.putString("networkId", networkId); o.putString("serial", serial); if (!filterItem.isEmpty()) o.store("filter", ItemStack.CODEC, filterItem); }
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput i) { super.loadAdditional(i); name = i.getStringOr("name", "IO Tag"); networkId = i.getStringOr("networkId", "00000"); serial = i.getStringOr("serial", UUID.randomUUID().toString()); i.read("filter", ItemStack.CODEC).ifPresent(f -> filterItem = f); }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
}