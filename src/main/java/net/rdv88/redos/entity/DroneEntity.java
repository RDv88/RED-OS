package net.rdv88.redos.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.util.LogisticsEngine;

import java.util.Optional;
import java.util.UUID;

public class DroneEntity extends Mob {
    private static final EntityDataAccessor<ItemStack> CARRIED_ITEM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<BlockPos>> HUB_POS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<String> AGENT_ID = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    
    private UUID agentUUID = null;
    private BlockPos objectivePos = null;
    private TacticalState tacticalState = TacticalState.IDLE;
    private double lockedTargetY = -1;
    private int interactionTimer = 0;
    private int waypointCooldown = 0;
    private BlockPos sourcePos, targetPos;
    private final java.util.List<BlockPos> waypoints = new java.util.ArrayList<>();

    private enum TacticalState { IDLE, ASCENDING, CRUISING, DESCENDING, ACTING, EXIT_ASCENT }

    public DroneEntity(EntityType<? extends Mob> type, Level level) { 
        super(type, level); 
        this.noPhysics = true; 
        this.setNoGravity(true); 
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { 
        super.defineSynchedData(b); 
        b.define(CARRIED_ITEM, ItemStack.EMPTY); 
        b.define(HUB_POS, Optional.empty());
        b.define(AGENT_ID, "");
    }
    
    public void assignAgent(UUID id) { 
        this.agentUUID = id; 
        this.entityData.set(AGENT_ID, id != null ? id.toString() : "");
    }
    public void setTaskData(BlockPos s, BlockPos t, BlockPos h) { 
        this.sourcePos = s; 
        this.targetPos = t; 
        this.entityData.set(HUB_POS, Optional.of(h)); 
    }
    
    public Optional<BlockPos> getHubPos() { return this.entityData.get(HUB_POS); }

    public void setTacticalObjective(BlockPos pos) {
        if (this.objectivePos != null && this.objectivePos.equals(pos) && tacticalState != TacticalState.IDLE) return;
        this.objectivePos = pos;
        
        // STRICT PHASING: Only trigger ascent sequence if we are taking off from a standstill or aborting a descent.
        if (tacticalState == TacticalState.IDLE || tacticalState == TacticalState.DESCENDING || tacticalState == TacticalState.ACTING) {
            double cruiseY = pos.getY() + 1.9;
            this.tacticalState = (Math.abs(position().y - cruiseY) < 0.4) ? TacticalState.CRUISING : TacticalState.ASCENDING;
            this.lockedTargetY = -1; 
        }
    }

    public ItemStack getCarriedItem() { return this.entityData.get(CARRIED_ITEM); }
    public void setCarriedItem(ItemStack s) { this.entityData.set(CARRIED_ITEM, s); }
    
    public void setWaypoints(java.util.List<BlockPos> list) {
        this.waypoints.clear();
        if (list != null) this.waypoints.addAll(list);
    }
    public java.util.List<BlockPos> getWaypoints() { return new java.util.ArrayList<>(this.waypoints); }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        // We load the UUID to sync it, but the drone will self-destruct if it's not in the Engine's RAM.
        input.read("AgentUUID", net.minecraft.core.UUIDUtil.CODEC).ifPresent(uuid -> {
            this.agentUUID = uuid;
            this.entityData.set(AGENT_ID, uuid.toString());
        });
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        // WE DO NOT CALL SUPER. This prevents the drone from being saved in the chunk.
        // It's a "Transient Actor" - only exists while the Engine says so.
    }

    @Override
    public void tick() {
        super.tick();

        // Forced properties (Safety Guard)
        this.noPhysics = true;
        this.setNoGravity(true);

        if (level().isClientSide()) {
            spawnPurpleGlowParticles();
            return;
        }

        // STAGGERED PERFORMANCE CHECK (Once every 2 seconds, spread across drones)
        if (this.tickCount % 40 == (this.getUUID().hashCode() & 39)) {
            if (this.agentUUID == null || !LogisticsEngine.isOfficialPuppet(this.agentUUID, this.getUUID())) {
                this.discard();
                return;
            }
        }

        if (agentUUID == null) {
            this.discard();
            return;
        }

        if (agentUUID != null) LogisticsEngine.syncPuppetData(agentUUID, position());

        if (interactionTimer > 0) interactionTimer--;
        if (waypointCooldown > 0) waypointCooldown--;

        if (interactionTimer > 0) { 
            if (interactionTimer > 20) this.setDeltaMovement(0, -0.06, 0);
            else if (interactionTimer == 20) { LogisticsEngine.receiveHandshake(agentUUID, position(), getCarriedItem()); this.setDeltaMovement(Vec3.ZERO); }
            else if (interactionTimer > 10) this.setDeltaMovement(0, 0.06, 0);
            else this.setDeltaMovement(Vec3.ZERO);
            return; 
        }

        switch (tacticalState) {
            case IDLE -> {
                if (agentUUID != null && this.tickCount % 20 == 0) {
                    BlockPos orders = LogisticsEngine.requestOrders(agentUUID);
                    if (orders != null && !orders.equals(objectivePos)) {
                        System.out.println("DRONE [" + agentUUID + "] WAKING UP. Received delayed orders for: " + orders);
                        setTacticalObjective(orders);
                    }
                }
            }
            case ASCENDING -> {
                if (lockedTargetY == -1) lockedTargetY = objectivePos.getY() + 1.9;
                if (position().y < lockedTargetY - 0.1) this.setDeltaMovement(0, 0.1, 0);
                else { this.setDeltaMovement(Vec3.ZERO); tacticalState = TacticalState.CRUISING; }
            }
            case CRUISING -> {
                if (agentUUID != null) {
                    BlockPos nextWp = this.waypoints.isEmpty() ? null : this.waypoints.get(0);
                    BlockPos lookAheadWp = this.waypoints.size() > 1 ? this.waypoints.get(1) : null;
                    BlockPos target = (nextWp != null) ? nextWp : objectivePos;
                                        if (target != null) {
                                            double targetY = target.getY() + 1.9;
                                            Vec3 targetVec = new Vec3(target.getX() + 0.5, targetY, target.getZ() + 0.5);
                                            double dist = position().distanceTo(targetVec);
                                            double baseSpeed = 0.55; 
                                            
                                            // Altitude correction: smoothly move towards the target's ideal flying height
                                            double yDiff = targetY - position().y;
                                            double yMotion = Math.clamp(yDiff * 0.1, -0.1, 0.1); // Smooth vertical adjustment
                                            
                                            if (lookAheadWp != null) {
                            Vec3 nextDir = targetVec.subtract(position()).normalize();
                            Vec3 futureDir = Vec3.atCenterOf(lookAheadWp).subtract(targetVec).normalize();
                            if (nextDir.dot(futureDir) < 0.85) baseSpeed = 0.35; else baseSpeed = 0.65; 
                        }
                        
                        if (nextWp == null) {
                            // BLOCK-BASED LANDING: If we are in the same X and Z block as the objective, we are there. Descend immediately.
                            if (this.blockPosition().getX() == objectivePos.getX() && this.blockPosition().getZ() == objectivePos.getZ()) {
                                this.setDeltaMovement(Vec3.ZERO);
                                tacticalState = TacticalState.DESCENDING;
                            } else {
                                // Still navigating to the exact block column of the target
                                moveSmoothly(targetVec, baseSpeed);
                                Vec3 m = this.getDeltaMovement();
                                this.setDeltaMovement(m.x, yMotion, m.z);
                            }
                        } else {
                            // WAYPOINT NAVIGATION: Wide 12.5m arc for transmitters
                            if (dist > 12.5) {
                                moveSmoothly(targetVec, baseSpeed);
                                Vec3 m = this.getDeltaMovement();
                                this.setDeltaMovement(m.x, yMotion, m.z);
                            } else {
                                if (waypointCooldown <= 0) {
                                    waypointCooldown = 10;
                                    this.waypoints.remove(0);
                                    
                                    // SYNC TO RAM: Ensure the Ghost mode also knows this waypoint is gone
                                    LogisticsEngine.getFleet().stream()
                                        .filter(a -> a.uuid.equals(agentUUID))
                                        .forEach(a -> a.waypoints = new java.util.ArrayList<>(this.waypoints));

                                    BlockPos immediateNext = this.waypoints.isEmpty() ? null : this.waypoints.get(0);
                                    if (immediateNext != null) {
                                        Vec3 nextVec = new Vec3(immediateNext.getX() + 0.5, position().y, immediateNext.getZ() + 0.5);
                                        moveSmoothly(nextVec, baseSpeed);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            case DESCENDING -> {
                double targetY = Vec3.atCenterOf(objectivePos).y + 0.5;
                if (position().y > targetY + 0.1) this.setDeltaMovement(0, -0.1, 0);
                else {
                    this.setDeltaMovement(Vec3.ZERO);
                    if (getHubPos().map(p -> p.equals(objectivePos)).orElse(false)) {
                        this.setPos(objectivePos.getX() + 0.5, targetY, objectivePos.getZ() + 0.5);
                        LogisticsEngine.receiveHandshake(agentUUID, position(), getCarriedItem());
                        this.discard(); LogisticsEngine.notifyPuppetDead(agentUUID);
                    } else tacticalState = TacticalState.ACTING;
                }
            }
            case ACTING -> { interactionTimer = 30; tacticalState = TacticalState.EXIT_ASCENT; lockedTargetY = -1; }
            case EXIT_ASCENT -> {
                if (lockedTargetY == -1) lockedTargetY = objectivePos.getY() + 1.9;
                if (position().y < lockedTargetY - 0.1) this.setDeltaMovement(0, 0.1, 0);
                else { this.setDeltaMovement(Vec3.ZERO); tacticalState = TacticalState.CRUISING; }
            }
        }
    }

    private void moveSmoothly(Vec3 target, double speed) {
        Vec3 motion = target.subtract(position()).normalize().scale(speed);
        this.setDeltaMovement(motion);
        float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
        this.setYRot(yaw); this.setYHeadRot(yaw);
    }

    private void spawnPurpleGlowParticles() {
        if (random.nextFloat() < 0.2f) {
            double ox = (random.nextDouble() - 0.5) * 0.4;
            double oz = (random.nextDouble() - 0.5) * 0.4;
            level().addParticle(ParticleTypes.WITCH, getX() + ox, getY() + 0.2, getZ() + oz, 0, -0.02, 0);
        }
    }

    public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0D).add(Attributes.MOVEMENT_SPEED, 0.3D).add(Attributes.FLYING_SPEED, 0.6D); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
}