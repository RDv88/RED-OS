package net.rdv88.redos.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.entity.DroneEntity;
import net.rdv88.redos.entity.ModEntities;
import net.rdv88.redos.item.ModItems;
import net.rdv88.redos.screen.DroneStationScreenHandler;
import net.rdv88.redos.util.TechNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

public class DroneStationBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, ContainerListener {
    private String name = "Drone Hub";
    private String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    
    private final SimpleContainer droneInventory = new SimpleContainer(3);
    
    private final Map<Integer, UUID> lockedSlots = new HashMap<>();
    private final Map<UUID, Integer> activeDroneTasks = new HashMap<>();
    private final Map<UUID, Long> launchTimes = new HashMap<>();
    private int lastLaunchedTaskIndex = -1;
    private boolean chunkForced = false;
    
    public static class LogisticsTask {
        public BlockPos source;
        public BlockPos target;
        public int priority;
        public boolean isAssigned = false;
        public boolean enabled = true;
        
        public LogisticsTask(BlockPos source, BlockPos target, int priority) {
            this.source = source;
            this.target = target;
            this.priority = priority;
        }
    }
    
    private final List<LogisticsTask> taskList = new ArrayList<>();

    public DroneStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_STATION_BLOCK_ENTITY, pos, state);
        this.droneInventory.addListener(this);
    }

    @Override
    public void containerChanged(Container container) {
        this.markUpdated();
    }

    public void setName(String name) { this.name = name; markUpdated(); }
    public String getName() { return name; }
    public void setNetworkId(String id) { this.networkId = id; markUpdated(); registerInNetwork(); }
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }
    
    public SimpleContainer getInventory() { return droneInventory; }
    public List<LogisticsTask> getTasks() { return taskList; }
    public boolean isSlotLocked(int slot) { return lockedSlots.containsKey(slot); }

    public void addTask(BlockPos source, BlockPos target, int priority) {
        if (taskList.size() >= 9) return;
        taskList.add(new LogisticsTask(source, target, priority));
        markUpdated();
        registerInNetwork();
    }

    public void removeTask(int index) {
        if (index >= 0 && index < taskList.size()) {
            final int taskIdx = index;
            activeDroneTasks.forEach((droneUuid, assignedIdx) -> {
                if (assignedIdx == taskIdx) {
                    net.minecraft.world.entity.Entity entity = ((ServerLevel)level).getEntity(droneUuid);
                    if (entity instanceof DroneEntity drone) {
                        drone.abortMission("Task Removed");
                    }
                }
            });

            taskList.remove(index);
            activeDroneTasks.entrySet().removeIf(entry -> entry.getValue() == index);
            markUpdated();
            registerInNetwork();
        }
    }

    public void updateTask(int index, int newPriority) {
        if (index >= 0 && index < taskList.size()) {
            taskList.get(index).priority = newPriority;
            markUpdated();
        }
    }

    public void toggleTask(int index) {
        if (index >= 0 && index < taskList.size()) {
            LogisticsTask task = taskList.get(index);
            task.enabled = !task.enabled;
            
            if (!task.enabled) {
                final int taskIdx = index;
                activeDroneTasks.forEach((droneUuid, assignedIdx) -> {
                    if (assignedIdx == taskIdx) {
                        net.minecraft.world.entity.Entity entity = ((ServerLevel)level).getEntity(droneUuid);
                        if (entity instanceof DroneEntity drone) {
                            drone.abortMission("Task Deactivated");
                        }
                    }
                });
            } else {
                tryLaunchDrone();
            }
            
            markUpdated();
        }
    }

    private void markUpdated() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void registerInNetwork() {
        if (level == null) return;
        Map<String, Object> settings = new HashMap<>();
        settings.put("drone_count", getActiveDroneCount());
        
        // Serialize task list for RAM access
        List<Map<String, Object>> serializedTasks = new ArrayList<>();
        for (LogisticsTask task : taskList) {
            Map<String, Object> tMap = new HashMap<>();
            tMap.put("source", task.source);
            tMap.put("target", task.target);
            tMap.put("priority", task.priority);
            tMap.put("enabled", task.enabled);
            serializedTasks.add(tMap);
        }
        settings.put("task_list", serializedTasks);
        settings.put("task_count", taskList.size());
        
        TechNetwork.registerNode(level, worldPosition, this.networkId, this.name, TechNetwork.NodeType.DRONE_STATION, this.serial, settings);
    }

    private int getActiveDroneCount() {
        int count = 0;
        for (int i = 0; i < droneInventory.getContainerSize(); i++) {
            ItemStack stack = droneInventory.getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.DRONE_UNIT)) count++;
        }
        return count;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DroneStationBlockEntity blockEntity) {
        if (level.isClientSide()) return;
        
        blockEntity.updateChunkLoading();

        long currentTime = level.getGameTime();
        Iterator<Map.Entry<Integer, UUID>> it = blockEntity.lockedSlots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, UUID> entry = it.next();
            UUID droneUuid = entry.getValue();
            long launchTime = blockEntity.launchTimes.getOrDefault(droneUuid, 0L);
            
            if (currentTime - launchTime > 12000) {
                if (((ServerLevel)level).getEntity(droneUuid) == null) {
                    blockEntity.activeDroneTasks.remove(droneUuid);
                    blockEntity.launchTimes.remove(droneUuid);
                    it.remove();
                    blockEntity.markUpdated();
                }
            }
        }

        if (currentTime % 10 == 0) {
            blockEntity.tryLaunchDrone();
            blockEntity.registerInNetwork();
        }
    }

    private void tryLaunchDrone() {
        if (level == null || level.isClientSide() || taskList.isEmpty()) return;

        for (int i = 0; i < droneInventory.getContainerSize(); i++) {
            if (!droneInventory.getItem(i).isEmpty() && 
                droneInventory.getItem(i).is(ModItems.DRONE_UNIT) && 
                !lockedSlots.containsKey(i)) {
                
                LogisticsTask chosenTask = null;
                int chosenIdx = -1;

                for (int p = 1; p <= 5; p++) {
                    for (int j = 0; j < taskList.size(); j++) {
                        LogisticsTask task = taskList.get(j);
                        if (task.enabled && task.priority == p && hasItemsAtSource(task.source) && hasSpaceAtTarget(task.target)) {
                            chosenTask = task;
                            chosenIdx = j;
                            break;
                        }
                    }
                    if (chosenTask != null) break;
                }

                if (chosenTask != null) {
                    List<BlockPos> waypoints = TechNetwork.findMeshPath(level, worldPosition, chosenTask.source, networkId);
                    if (!waypoints.isEmpty()) {
                        launchDroneAtTask(chosenIdx, i, waypoints);
                    }
                }
            }
        }
    }

    private void launchDroneAtTask(int index, int slotIndex, List<BlockPos> waypoints) {
        LogisticsTask task = taskList.get(index);
        DroneEntity drone = new DroneEntity(ModEntities.DRONE, level);
        drone.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5);
        drone.setTask(task.source, task.target, networkId, worldPosition, index);
        drone.setHomeSlot(slotIndex);
        drone.setHighway(waypoints);
        drone.setDeltaMovement(new Vec3(0, 0.3, 0));
        
        if (level.addFreshEntity(drone)) {
            lockedSlots.put(slotIndex, drone.getUUID());
            activeDroneTasks.put(drone.getUUID(), index);
            launchTimes.put(drone.getUUID(), level.getGameTime());
            lastLaunchedTaskIndex = index;
            markUpdated();
        }
    }

    public void handleTagRemoval(BlockPos tagPos) {
        if (level == null || level.isClientSide()) return;
        
        boolean changed = false;
        for (int i = 0; i < taskList.size(); i++) {
            LogisticsTask task = taskList.get(i);
            if (task.source.equals(tagPos) || task.target.equals(tagPos)) {
                task.enabled = false;
                changed = true;
                
                final int taskIdx = i;
                activeDroneTasks.forEach((droneUuid, assignedIdx) -> {
                    if (assignedIdx == taskIdx) {
                        net.minecraft.world.entity.Entity entity = ((ServerLevel)level).getEntity(droneUuid);
                        if (entity instanceof DroneEntity drone) {
                            drone.abortMission("Tag Disconnected");
                        }
                    }
                });
            }
        }
        
        if (changed) {
            markUpdated();
            registerInNetwork();
        }
    }

    public void onDroneReturn(UUID droneId, int slotIndex) {
        lockedSlots.remove(slotIndex);
        activeDroneTasks.remove(droneId);
        launchTimes.remove(droneId);
        markUpdated();
        tryLaunchDrone();
    }

    public void onDroneCrash(UUID droneId, int slotIndex) {
        droneInventory.removeItem(slotIndex, 1);
        lockedSlots.remove(slotIndex);
        activeDroneTasks.remove(droneId);
        launchTimes.remove(droneId);
        markUpdated();
    }

    public @Nullable LogisticsTask requestNextTask(UUID droneId, int oldTaskIndex) {
        activeDroneTasks.remove(droneId);

        for (int p = 1; p <= 5; p++) {
            for (int i = 0; i < taskList.size(); i++) {
                LogisticsTask task = taskList.get(i);
                if (task.enabled && task.priority == p && hasItemsAtSource(task.source) && hasSpaceAtTarget(task.target)) {
                    activeDroneTasks.put(droneId, i);
                    markUpdated();
                    return task;
                }
            }
        }
        return null;
    }

    private void updateChunkLoading() {
        if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;
        if (!chunkForced) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(worldPosition);
            serverLevel.setChunkForced(cp.x, cp.z, true);
            chunkForced = true;
        }
    }

    public void releaseChunk() {
        if (chunkForced && level instanceof ServerLevel serverLevel) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(worldPosition);
            serverLevel.setChunkForced(cp.x, cp.z, false);
            chunkForced = false;
        }
    }

    private boolean hasSpaceAtTarget(BlockPos tagPos) {
        if (tagPos == null) return false;
        TechNetwork.NetworkNode node = TechNetwork.getNodeAt(tagPos);
        if (node != null && node.networkId.equals(this.networkId)) {
            Object spaceObj = node.settings.get("free_space");
            if (spaceObj instanceof Number space) {
                return space.intValue() >= 64;
            }
        }
        return true;
    }

    private boolean hasItemsAtSource(BlockPos tagPos) {
        if (tagPos == null) return false;
        TechNetwork.NetworkNode node = TechNetwork.getNodeAt(tagPos);
        if (node != null && node.networkId.equals(this.networkId)) {
            Object countObj = node.settings.get("item_count");
            if (countObj instanceof Number count) {
                return count.intValue() > 0;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("name", name);
        output.putString("networkId", networkId);
        output.putString("serial", serial);

        NonNullList<ItemStack> stacks = NonNullList.withSize(droneInventory.getContainerSize(), ItemStack.EMPTY);
        for(int i=0; i<droneInventory.getContainerSize(); i++) stacks.set(i, droneInventory.getItem(i));
        ContainerHelper.saveAllItems(output, stacks);

        output.putInt("task_count", taskList.size());
        for (int i = 0; i < taskList.size(); i++) {
            LogisticsTask t = taskList.get(i);
            output.putLong("task_" + i + "_src", t.source.asLong());
            output.putLong("task_" + i + "_dst", t.target.asLong());
            output.putInt("task_" + i + "_prio", t.priority);
            output.putBoolean("task_" + i + "_assigned", t.isAssigned);
            output.putBoolean("task_" + i + "_enabled", t.enabled);
        }

        CompoundTag lockTag = new CompoundTag();
        for (Map.Entry<Integer, UUID> entry : lockedSlots.entrySet()) {
            lockTag.putLong("s_m_" + entry.getKey(), entry.getValue().getMostSignificantBits());
            lockTag.putLong("s_l_" + entry.getKey(), entry.getValue().getLeastSignificantBits());
        }
        output.store("lock_data", CompoundTag.CODEC, lockTag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.name = input.getStringOr("name", "Drone Hub");
        this.networkId = input.getStringOr("networkId", "00000");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());

        NonNullList<ItemStack> stacks = NonNullList.withSize(droneInventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, stacks);
        for(int i=0; i<stacks.size(); i++) droneInventory.setItem(i, stacks.get(i));

        taskList.clear();
        int taskCount = input.getIntOr("task_count", 0);
        for (int i = 0; i < taskCount; i++) {
            long srcLong = input.getLongOr("task_" + i + "_src", 0);
            long dstLong = input.getLongOr("task_" + i + "_dst", 0);
            if (srcLong != 0) {
                LogisticsTask task = new LogisticsTask(BlockPos.of(srcLong), BlockPos.of(dstLong), input.getIntOr("task_" + i + "_prio", 1));
                task.isAssigned = input.getBooleanOr("task_" + i + "_assigned", false);
                task.enabled = input.getBooleanOr("task_" + i + "_enabled", true);
                taskList.add(task);
            }
        }

        lockedSlots.clear();
        input.read("lock_data", CompoundTag.CODEC).ifPresent(lockTag -> {
            for (int i = 0; i < 3; i++) {
                if (lockTag.contains("s_m_" + i)) {
                    long most = lockTag.getLongOr("s_m_" + i, 0L);
                    long least = lockTag.getLongOr("s_l_" + i, 0L);
                    lockedSlots.put(i, new UUID(most, least));
                }
            }
        });
    }

    @Override public Component getDisplayName() { return Component.literal(name); }
    @Override public BlockPos getScreenOpeningData(ServerPlayer player) { return worldPosition; }
    @Nullable @Override public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DroneStationScreenHandler(syncId, inv, this.worldPosition);
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
