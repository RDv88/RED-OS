package net.rdv88.redos.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.rdv88.redos.block.entity.DroneStationBlockEntity;
import net.rdv88.redos.item.ModItems;

public class DroneStationScreenHandler extends AbstractContainerMenu {
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPos> PACKET_CODEC = StreamCodec.of(
        (buf, value) -> BlockPos.STREAM_CODEC.encode(buf, value),
        buf -> BlockPos.STREAM_CODEC.decode(buf)
    );

    private final Container inventory;
    private final BlockPos pos;
    private final DroneStationBlockEntity blockEntity;

    public DroneStationScreenHandler(int syncId, Inventory playerInventory, BlockPos pos) {
        super(ModScreenHandlers.DRONE_STATION_SCREEN_HANDLER, syncId);
        this.pos = pos;
        this.blockEntity = (DroneStationBlockEntity) playerInventory.player.level().getBlockEntity(pos);
        this.inventory = (blockEntity != null) ? blockEntity.getInventory() : new SimpleContainer(5);
        
        checkContainerSize(this.inventory, 5);
        this.inventory.startOpen(playerInventory.player);

        // CORRECT HORIZONTAL SLOT POSITIONS (Matching DroneStationScreen.java)
        for (int i = 0; i < 5; i++) {
            final int index = i;
            this.addSlot(new Slot(inventory, i, 44 + (i * 18), 18) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.DRONE_UNIT); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPickup(Player player) {
                    return blockEntity == null || !blockEntity.isSlotLocked(index);
                }
            });
        }

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    public BlockPos getPos() { return pos; }

    @Override
    public ItemStack quickMoveStack(Player player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            newStack = originalStack.copy();
            if (invSlot < 5) {
                if (!this.moveItemStackTo(originalStack, 5, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(originalStack, 0, 5, false)) return ItemStack.EMPTY;

            if (originalStack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return newStack;
    }

    @Override public boolean stillValid(Player player) { return this.inventory.stillValid(player); }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 51 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 109));
        }
    }
}
