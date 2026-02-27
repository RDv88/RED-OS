package net.rdv88.redos.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.screen.DroneStationScreenHandler;
import net.rdv88.redos.util.TechNetwork;
import net.rdv88.redos.util.LogisticsEngine;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DroneStationBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, ContainerListener {
    private String name = "RED-OS Logistic Hub";
    private String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    private final SimpleContainer droneInventory = new SimpleContainer(3);
    private boolean chunkForced = false;
    private int launchCooldown = 0;
    
    public static class LogisticsTask {
        public BlockPos source, target;
        public int priority;
        public boolean enabled = false;
        public boolean isAssigned = false;
        public boolean isFull = false;
        public LogisticsTask(BlockPos s, BlockPos t, int p) { this.source = s; this.target = t; this.priority = p; }
    }
    
    private final List<LogisticsTask> taskList = new ArrayList<>();

    public DroneStationBlockEntity(BlockPos pos, BlockState state) { 
        super(ModBlockEntities.DRONE_STATION_BLOCK_ENTITY, pos, state); 
        this.droneInventory.addListener(this); 
    }

    @Override public void containerChanged(Container c) { markUpdated(); }
    public void setName(String n) { this.name = n; markUpdated(); }
    public String getName() { return name; }
    public void setNetworkId(String id) { this.networkId = id; markUpdated(); }
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }
    public SimpleContainer getInventory() { return droneInventory; }
    public List<LogisticsTask> getTasks() { return taskList; }

    public void addTask(BlockPos s, BlockPos t, int p) { if (taskList.size() < 9) { taskList.add(new LogisticsTask(s, t, p)); markUpdated(); } }
    public void removeTask(int i) { if (i >= 0 && i < taskList.size()) { taskList.remove(i); markUpdated(); } }
    public void updateTask(int i, int p) { if (i >= 0 && i < taskList.size()) { taskList.get(i).priority = p; markUpdated(); } }
    public void toggleTask(int i) { if (i >= 0 && i < taskList.size()) { taskList.get(i).enabled = !taskList.get(i).enabled; markUpdated(); } }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            registerInNetwork();
            if (level instanceof ServerLevel sl) LogisticsEngine.onHubUpdated(sl, worldPosition);
        }
    }

    private void registerInNetwork() {
        if (level == null || level.isClientSide()) return;
        Map<String, Object> settings = new HashMap<>();
        int drones = 0;
        for (int i = 0; i < 3; i++) if (!droneInventory.getItem(i).isEmpty()) drones++;
        settings.put("drone_count", drones);
        List<Map<String, Object>> serializedTasks = new ArrayList<>();
        for (LogisticsTask t : taskList) {
            Map<String, Object> m = new HashMap<>();
            m.put("source", t.source); m.put("target", t.target); m.put("priority", t.priority); m.put("enabled", t.enabled);
            m.put("is_full", t.isFull);
            serializedTasks.add(m);
        }
        settings.put("task_list", serializedTasks);
        settings.put("task_count", taskList.size());
        TechNetwork.registerNode(level, worldPosition, networkId, name, TechNetwork.NodeType.DRONE_STATION, serial, settings);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DroneStationBlockEntity be) {
        if (level.isClientSide()) return;
        if (be.launchCooldown > 0) be.launchCooldown--;
        if (!be.chunkForced && level instanceof ServerLevel sl) { sl.setChunkForced(new net.minecraft.world.level.ChunkPos(pos).x, new net.minecraft.world.level.ChunkPos(pos).z, true); be.chunkForced = true; }
        if (level.getGameTime() % 40 == 0) be.registerInNetwork();
    }

    public void onDroneReturn(UUID id, int slot) { markUpdated(); }
    public void onDroneCrash(UUID id, int slot) { markUpdated(); }
    public void markTaskFull(int index, boolean full) { if (index >= 0 && index < taskList.size()) { taskList.get(index).isFull = full; markUpdated(); } }
    public void resetFullTasksForTarget(BlockPos targetPos) { for (LogisticsTask t : taskList) { if (t.target.equals(targetPos)) t.isFull = false; } markUpdated(); }
    public boolean canLaunch() { return launchCooldown <= 0; }
    public void startLaunchCooldown() { this.launchCooldown = 50; }
    public void releaseChunk() { if (chunkForced && level instanceof ServerLevel sl) { sl.setChunkForced(new net.minecraft.world.level.ChunkPos(worldPosition).x, new net.minecraft.world.level.ChunkPos(worldPosition).z, false); chunkForced = false; } }

    @Override protected void saveAdditional(ValueOutput o) { super.saveAdditional(o); o.putString("name", name); o.putString("networkId", networkId); o.putString("serial", serial); NonNullList<ItemStack> s = NonNullList.withSize(3, ItemStack.EMPTY); for(int i=0; i<3; i++) s.set(i, droneInventory.getItem(i)); ContainerHelper.saveAllItems(o, s); o.putInt("task_count", taskList.size()); for (int i = 0; i < taskList.size(); i++) { LogisticsTask t = taskList.get(i); o.putLong("t" + i + "s", t.source.asLong()); o.putLong("t" + i + "t", t.target.asLong()); o.putInt("t" + i + "p", t.priority); o.putBoolean("t" + i + "e", t.enabled); } }
    @Override protected void loadAdditional(ValueInput i) { super.loadAdditional(i); name = i.getStringOr("name", "RED-OS Logistic Hub"); networkId = i.getStringOr("networkId", "00000"); serial = i.getStringOr("serial", UUID.randomUUID().toString()); NonNullList<ItemStack> s = NonNullList.withSize(3, ItemStack.EMPTY); ContainerHelper.loadAllItems(i, s); for(int j=0; j<3; j++) droneInventory.setItem(j, s.get(j)); taskList.clear(); int count = i.getIntOr("task_count", 0); for (int k = 0; k < count; k++) { LogisticsTask t = new LogisticsTask(BlockPos.of(i.getLongOr("t" + k + "s", 0)), BlockPos.of(i.getLongOr("t" + k + "t", 0)), i.getIntOr("t" + k + "p", 1)); t.enabled = i.getBooleanOr("t" + k + "e", false); taskList.add(t); } }
    @Override public Component getDisplayName() { return Component.literal(name); }
    @Override public BlockPos getScreenOpeningData(ServerPlayer p) { return worldPosition; }
    @Nullable @Override public AbstractContainerMenu createMenu(int s, Inventory inv, Player p) { return new DroneStationScreenHandler(s, inv, worldPosition); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return this.saveWithoutMetadata(registries); }
    public boolean isSlotLocked(int slot) { return net.rdv88.redos.util.LogisticsEngine.getFleet().stream().anyMatch(a -> a.hubPos.equals(worldPosition) && a.homeSlot == slot); }
}