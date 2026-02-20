package net.rdv88.redos.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.block.entity.DroneStationBlockEntity;
import net.rdv88.redos.block.entity.IOTagBlockEntity;
import net.rdv88.redos.item.ModItems;
import net.rdv88.redos.util.TechNetwork;

import java.util.ArrayList;
import java.util.List;

public class DroneEntity extends Mob {
    private static final EntityDataAccessor<ItemStack> CARRIED_ITEM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    
    private BlockPos sourcePos;
    private BlockPos targetPos;
    private String networkId = "00000";
    private BlockPos hubPos;
    private int taskIndex = -1;
    private int homeSlotIndex = -1;
    
    private List<BlockPos> highwayWaypoints = new ArrayList<>();
    private int currentWaypointIdx = 0;
    
    private State state = State.GOING_TO_SOURCE;
    private int interactionTimer = 0;
    private int panicTimer = 0;
    private ChunkPos lastForcedChunk = null;
    private BlockPos lastLightPos = null;

    public enum State {
        GOING_TO_SOURCE, LOADING, GOING_TO_TARGET, UNLOADING, RETURNING_HOME, PANIC, CRASHING
    }

    public DroneEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CARRIED_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void travel(Vec3 movementInput) {
        if (this.isEffectiveAi() || this.level().isClientSide()) {
            this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        }
    }

    public void setTask(BlockPos source, BlockPos target, String netId, BlockPos hub, int index) {
        this.sourcePos = source;
        this.targetPos = target;
        this.networkId = netId;
        this.hubPos = hub;
        this.taskIndex = index;
    }

    public void setHomeSlot(int slot) { this.homeSlotIndex = slot; }

    public void setHighway(List<BlockPos> waypoints) {
        this.highwayWaypoints = new ArrayList<>(waypoints);
        this.currentWaypointIdx = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.6D);
    }

    public ItemStack getCarriedItem() { return this.entityData.get(CARRIED_ITEM); }
    public void setCarriedItem(ItemStack stack) { this.entityData.set(CARRIED_ITEM, stack); }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean checkDespawn() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = (state != State.CRASHING);
        this.setNoGravity(state != State.CRASHING);

        if (level().isClientSide()) {
            spawnPurpleGlowParticles();
            return;
        }

        updateChunkLoading();
        updateDynamicLight();

        if (interactionTimer > 0) {
            interactionTimer--;
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (state == State.CRASHING) { handleCrash(); return; }
        if (state == State.PANIC) { handlePanic(); return; }

        if (highwayWaypoints.isEmpty() && state != State.CRASHING) recalculateRoute();

        if (level().getGameTime() % 40 == 0) validateNetworkIntact();

        switch (state) {
            case GOING_TO_SOURCE -> navigateHighway(sourcePos, State.LOADING);
            case LOADING -> {
                pickupItem();
                List<BlockPos> toTarget = TechNetwork.findMeshPath(level(), this.blockPosition(), targetPos, networkId);
                if (toTarget.isEmpty()) startPanic("Lost Link");
                else { setHighway(toTarget); state = State.GOING_TO_TARGET; }
            }
            case GOING_TO_TARGET -> navigateHighway(targetPos, State.UNLOADING);
            case UNLOADING -> {
                dropItem();
                
                // RE-EVALUATE: After every delivery, ask the Hub for the MOST IMPORTANT task
                BlockEntity be = level().getBlockEntity(hubPos);
                if (be instanceof DroneStationBlockEntity hub) {
                    DroneStationBlockEntity.LogisticsTask nextTask = hub.requestNextTask(getUUID(), taskIndex);
                    if (nextTask != null) {
                        this.sourcePos = nextTask.source;
                        this.targetPos = nextTask.target;
                        this.taskIndex = hub.getTasks().indexOf(nextTask);
                        
                        List<BlockPos> toSource = TechNetwork.findMeshPath(level(), this.blockPosition(), sourcePos, networkId);
                        if (!toSource.isEmpty()) { 
                            setHighway(toSource); 
                            state = State.GOING_TO_SOURCE; 
                            return; 
                        }
                    }
                }
                
                // No work found (source empty or target full) -> Return Home
                List<BlockPos> toHub = TechNetwork.findMeshPath(level(), this.blockPosition(), hubPos, networkId);
                if (toHub.isEmpty()) startPanic("Lost Hub Link");
                else { setHighway(toHub); state = State.RETURNING_HOME; }
            }
            case RETURNING_HOME -> navigateHighway(hubPos, null);
        }
    }

    public void abortMission(String reason) {
        if (state == State.RETURNING_HOME || state == State.CRASHING) return;
        
        state = State.RETURNING_HOME;
        TechNetwork.broadcastToNetwork(level(), networkId, "§cMission Aborted: " + reason);
        
        List<BlockPos> toHub = TechNetwork.findMeshPath(level(), this.blockPosition(), hubPos, networkId);
        if (toHub.isEmpty()) startPanic("Hub Link Lost during Abort");
        else setHighway(toHub);
    }

    private boolean hasMoreWork() {
        if (sourcePos == null) return false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockEntity be = level().getBlockEntity(sourcePos.offset(x, y, z));
                    if (be instanceof Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (!container.getItem(i).isEmpty()) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void handlePanic() {
        panicTimer++;
        this.setDeltaMovement(0, Math.sin(level().getGameTime() * 0.2) * 0.05, 0);
        if (level().getGameTime() % 20 == 0) {
            if (TechNetwork.isConnected(level(), this.blockPosition(), networkId)) {
                // Determine the correct state to return to
                State recoveryState = (sourcePos != null) ? State.GOING_TO_SOURCE : State.RETURNING_HOME;
                if (!getCarriedItem().isEmpty()) recoveryState = State.GOING_TO_TARGET;
                
                recalculateRoute();
                if (!highwayWaypoints.isEmpty()) { 
                    state = recoveryState; 
                    panicTimer = 0; 
                    return; 
                }
            }
        }
        if (panicTimer > 600) startCrashing("Timeout");
    }

    private void startPanic(String reason) {
        if (state == State.PANIC) return;
        state = State.PANIC;
        panicTimer = 0;
        TechNetwork.broadcastToNetwork(level(), networkId, "§eWARN: Drone Signal Lost. Re-routing...");
    }

    private void recalculateRoute() {
        BlockPos destination = switch (state) {
            case GOING_TO_SOURCE -> sourcePos;
            case GOING_TO_TARGET -> targetPos;
            case RETURNING_HOME, PANIC -> hubPos;
            default -> null;
        };
        if (destination != null) {
            List<BlockPos> newPath = TechNetwork.findMeshPath(level(), this.blockPosition(), destination, networkId);
            if (!newPath.isEmpty()) { setHighway(newPath); if (state == State.PANIC) state = State.RETURNING_HOME; }
        }
    }

    private void spawnPurpleGlowParticles() {
        if (random.nextFloat() < 0.3f) {
            double ox = (random.nextDouble() - 0.5) * 0.6;
            double oz = (random.nextDouble() - 0.5) * 0.6;
            level().addParticle(ParticleTypes.WITCH, getX() + ox, getY() + 0.3, getZ() + oz, 0, -0.05, 0);
        }
    }

    private void updateDynamicLight() {
        BlockPos currentPos = this.blockPosition();
        if (lastLightPos == null || !lastLightPos.equals(currentPos)) {
            if (lastLightPos != null && level().getBlockState(lastLightPos).is(Blocks.LIGHT)) {
                level().setBlockAndUpdate(lastLightPos, Blocks.AIR.defaultBlockState());
            }
            if (level().getBlockState(currentPos).isAir()) {
                level().setBlockAndUpdate(currentPos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12));
                lastLightPos = currentPos;
            }
        }
    }

    private void updateChunkLoading() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        ChunkPos currentChunk = new ChunkPos(this.blockPosition());
        if (lastForcedChunk == null || !lastForcedChunk.equals(currentChunk)) {
            if (lastForcedChunk != null) serverLevel.setChunkForced(lastForcedChunk.x, lastForcedChunk.z, false);
            serverLevel.setChunkForced(currentChunk.x, currentChunk.z, true);
            lastForcedChunk = currentChunk;
        }
    }

    private void validateNetworkIntact() {
        if (hubPos == null || state == State.PANIC || state == State.CRASHING) return;
        if (!TechNetwork.isConnected(level(), this.blockPosition(), networkId)) startPanic("Signal Lost");
    }

    private void navigateHighway(BlockPos finalDest, State nextState) {
        if (highwayWaypoints.isEmpty() || currentWaypointIdx >= highwayWaypoints.size()) return;
        BlockPos waypoint = highwayWaypoints.get(currentWaypointIdx);
        double targetX = waypoint.getX() + 0.5;
        double targetY = waypoint.getY() + 2.5; 
        double targetZ = waypoint.getZ() + 0.5;
        moveTo(targetX, targetY, targetZ);
        if (distanceToSqr(targetX, targetY, targetZ) < 1.0) {
            // DRONE HEARTBEAT: Refresh network link data while the chunk is loaded
            if (!level().isClientSide()) {
                TechNetwork.refreshNodeConnections(level(), waypoint);
            }

            currentWaypointIdx++;
            if (currentWaypointIdx >= highwayWaypoints.size()) {
                if (nextState != null) { state = nextState; interactionTimer = 20; }
                else returnToHub();
            }
        }
    }

    private void startCrashing(String reason) {
        state = State.CRASHING;
        this.noPhysics = false;
        this.setNoGravity(false);
    }

    private void handleCrash() {
        this.setYRot(this.getYRot() + 20.0f);
        if (this.onGround()) {
            if (level() instanceof ServerLevel serverLevel) {
                BlockEntity be = level().getBlockEntity(hubPos);
                if (be instanceof DroneStationBlockEntity hub) hub.onDroneCrash(getUUID(), homeSlotIndex);
                this.spawnAtLocation(serverLevel, new ItemStack(ModItems.DRONE_UNIT));
                if (!getCarriedItem().isEmpty()) this.spawnAtLocation(serverLevel, getCarriedItem());
                serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 10, 0.5, 0.5, 0.5, 0.1);
            }
            this.discard();
        }
    }

    private void moveTo(double x, double y, double z) {
        Vec3 target = new Vec3(x, y, z);
        Vec3 motion = target.subtract(position()).normalize().scale(0.4); 
        this.setDeltaMovement(motion);
        this.setYRot((float) Math.toDegrees(Math.atan2(-motion.x, motion.z)));
    }

    private void notifyNearbyTags(BlockPos center) {
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level().getBlockEntity(center.relative(dir));
            if (neighbor instanceof IOTagBlockEntity tag) {
                tag.updateInventoryStats();
            }
        }
    }

    private void pickupItem() {
        if (sourcePos == null) return;
        
        // SYNC BEFORE ACTION: Force the Tag to update RAM data
        BlockEntity tagBE = level().getBlockEntity(sourcePos);
        if (tagBE instanceof IOTagBlockEntity tag) tag.updateInventoryStats();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = sourcePos.offset(x, y, z);
                    BlockEntity be = level().getBlockEntity(p);
                    if (be instanceof Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            ItemStack stack = container.getItem(i);
                            if (!stack.isEmpty()) {
                                setCarriedItem(stack.split(stack.getMaxStackSize()));
                                container.setChanged();
                                notifyNearbyTags(p); // FORCE LIVE UPDATE
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void dropItem() {
        if (targetPos == null || getCarriedItem().isEmpty()) return;

        // SYNC BEFORE ACTION: Force the Tag to update RAM data at target
        BlockEntity tagBE = level().getBlockEntity(targetPos);
        if (tagBE instanceof IOTagBlockEntity tag) tag.updateInventoryStats();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = targetPos.offset(x, y, z);
                    BlockEntity be = level().getBlockEntity(p);
                    if (be instanceof Container container) {
                        ItemStack carried = getCarriedItem();
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            ItemStack slotStack = container.getItem(i);
                            if (slotStack.isEmpty()) {
                                container.setItem(i, carried);
                                setCarriedItem(ItemStack.EMPTY);
                                container.setChanged();
                                notifyNearbyTags(p); // FORCE LIVE UPDATE
                                return;
                            } else if (ItemStack.isSameItemSameComponents(slotStack, carried) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                                int canAdd = Math.min(carried.getCount(), slotStack.getMaxStackSize() - slotStack.getCount());
                                slotStack.grow(canAdd);
                                carried.shrink(canAdd);
                                if (carried.isEmpty()) { 
                                    setCarriedItem(ItemStack.EMPTY); 
                                    container.setChanged(); 
                                    notifyNearbyTags(p); // FORCE LIVE UPDATE
                                    return; 
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void returnToHub() {
        if (level() instanceof ServerLevel serverLevel) {
            BlockEntity be = level().getBlockEntity(hubPos);
            ItemStack carried = getCarriedItem();
            if (be instanceof DroneStationBlockEntity hub) {
                hub.onDroneReturn(getUUID(), homeSlotIndex);
                if (!carried.isEmpty()) {
                    Container inv = hub.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (inv.getItem(i).isEmpty()) { inv.setItem(i, carried); carried = ItemStack.EMPTY; break; }
                    }
                    if (!carried.isEmpty()) this.spawnAtLocation(serverLevel, carried);
                }
            } else {
                this.spawnAtLocation(serverLevel, new ItemStack(ModItems.DRONE_UNIT));
                if (!carried.isEmpty()) this.spawnAtLocation(serverLevel, carried);
            }
        }
        cleanup();
        this.discard();
    }

    private void cleanup() {
        if (lastForcedChunk != null && level() instanceof ServerLevel serverLevel) serverLevel.setChunkForced(lastForcedChunk.x, lastForcedChunk.z, false);
        if (lastLightPos != null && level().getBlockState(lastLightPos).is(Blocks.LIGHT)) level().setBlockAndUpdate(lastLightPos, Blocks.AIR.defaultBlockState());
    }

    @Override public void remove(RemovalReason reason) { cleanup(); super.remove(reason); }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.sourcePos = BlockPos.of(input.getLongOr("source", 0));
        this.targetPos = BlockPos.of(input.getLongOr("target", 0));
        this.hubPos = BlockPos.of(input.getLongOr("hub", 0));
        this.networkId = input.getStringOr("networkId", "00000");
        this.taskIndex = input.getIntOr("taskIndex", -1);
        this.homeSlotIndex = input.getIntOr("homeSlot", -1);
        this.state = State.valueOf(input.getStringOr("state", "GOING_TO_SOURCE"));
        this.panicTimer = input.getIntOr("panicTimer", 0);
        input.read("carried", ItemStack.CODEC).ifPresent(this::setCarriedItem);
        input.read("highway", BlockPos.CODEC.listOf()).ifPresent(h -> this.highwayWaypoints = new ArrayList<>(h));
        this.currentWaypointIdx = input.getIntOr("waypointIdx", 0);
        this.lastLightPos = BlockPos.of(input.getLongOr("lastLight", 0));
        if (this.lastLightPos != null && this.lastLightPos.equals(BlockPos.ZERO)) this.lastLightPos = null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (sourcePos != null) output.putLong("source", sourcePos.asLong());
        if (targetPos != null) output.putLong("target", targetPos.asLong());
        if (hubPos != null) output.putLong("hub", hubPos.asLong());
        output.putString("networkId", networkId);
        output.putInt("taskIndex", taskIndex);
        output.putInt("homeSlot", homeSlotIndex);
        output.putString("state", state.name());
        output.putInt("panicTimer", panicTimer);
        if (!getCarriedItem().isEmpty()) output.store("carried", ItemStack.CODEC, getCarriedItem());
        if (!highwayWaypoints.isEmpty()) output.store("highway", BlockPos.CODEC.listOf(), highwayWaypoints);
        output.putInt("waypointIdx", currentWaypointIdx);
        if (lastLightPos != null) output.putLong("lastLight", lastLightPos.asLong());
    }
}
