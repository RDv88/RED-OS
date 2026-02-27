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
    
    private UUID agentUUID = null;
    private BlockPos objectivePos = null;
    private TacticalState tacticalState = TacticalState.IDLE;
    private double lockedTargetY = -1;
    private int interactionTimer = 0;
    private BlockPos sourcePos, targetPos;

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
    }
    
    public void assignAgent(UUID id) { this.agentUUID = id; }
    public void setTaskData(BlockPos s, BlockPos t, BlockPos h) { 
        this.sourcePos = s; 
        this.targetPos = t; 
        this.entityData.set(HUB_POS, Optional.of(h)); 
    }
    
    public Optional<BlockPos> getHubPos() { return this.entityData.get(HUB_POS); }

    public void setTacticalObjective(BlockPos pos) {
        if (this.objectivePos != null && this.objectivePos.equals(pos) && tacticalState != TacticalState.IDLE) return;
        this.objectivePos = pos;
        double cruiseY = pos.getY() + 1.9;
        this.tacticalState = (Math.abs(position().y - cruiseY) < 0.4) ? TacticalState.CRUISING : TacticalState.ASCENDING;
        this.lockedTargetY = -1; 
    }

    public ItemStack getCarriedItem() { return this.entityData.get(CARRIED_ITEM); }
    public void setCarriedItem(ItemStack s) { this.entityData.set(CARRIED_ITEM, s); }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (agentUUID != null) {
            output.store("AgentUUID", net.minecraft.core.UUIDUtil.CODEC, agentUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read("AgentUUID", net.minecraft.core.UUIDUtil.CODEC).ifPresent(uuid -> {
            this.agentUUID = uuid;
            if (!level().isClientSide() && !net.rdv88.redos.util.LogisticsEngine.isAgentActive(this.agentUUID)) {
                this.discard();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            spawnPurpleGlowParticles();
            return;
        }

        if (agentUUID != null) LogisticsEngine.syncPuppetData(agentUUID, position(), getCarriedItem());

        if (interactionTimer > 0) { 
            interactionTimer--; 
            if (interactionTimer > 20) this.setDeltaMovement(0, -0.06, 0);
            else if (interactionTimer == 20) { LogisticsEngine.receiveHandshake(agentUUID, position(), getCarriedItem()); this.setDeltaMovement(Vec3.ZERO); }
            else if (interactionTimer > 10) this.setDeltaMovement(0, 0.06, 0);
            else this.setDeltaMovement(Vec3.ZERO);
            return; 
        }

        switch (tacticalState) {
            case ASCENDING -> {
                if (lockedTargetY == -1) lockedTargetY = objectivePos.getY() + 1.9;
                if (position().y < lockedTargetY - 0.1) this.setDeltaMovement(0, 0.1, 0);
                else { this.setDeltaMovement(Vec3.ZERO); tacticalState = TacticalState.CRUISING; }
            }
            case CRUISING -> {
                if (agentUUID != null) {
                    BlockPos nextWp = LogisticsEngine.getNextWaypoint(agentUUID);
                    BlockPos lookAheadWp = LogisticsEngine.getLookAheadWaypoint(agentUUID);
                    BlockPos target = (nextWp != null) ? nextWp : objectivePos;
                    if (target != null) {
                        Vec3 targetVec = new Vec3(target.getX() + 0.5, position().y, target.getZ() + 0.5);
                        double dist = position().distanceTo(targetVec);
                        double baseSpeed = 0.55; 
                        if (lookAheadWp != null) {
                            Vec3 nextDir = targetVec.subtract(position()).normalize();
                            Vec3 futureDir = Vec3.atCenterOf(lookAheadWp).subtract(targetVec).normalize();
                            if (nextDir.dot(futureDir) < 0.85) baseSpeed = 0.35; else baseSpeed = 0.65; 
                        }
                        if (dist > 0.5) {
                            if (nextWp != null && lookAheadWp != null && dist < 2.0) {
                                Vec3 futureVec = new Vec3(lookAheadWp.getX() + 0.5, position().y, lookAheadWp.getZ() + 0.5);
                                targetVec = position().lerp(futureVec, (2.0 - dist) / 1.5);
                            }
                            moveSmoothly(targetVec, baseSpeed);
                        } else {
                            if (nextWp != null) LogisticsEngine.reachWaypoint(agentUUID);
                            else { this.setDeltaMovement(Vec3.ZERO); tacticalState = TacticalState.DESCENDING; }
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
                else { this.setDeltaMovement(Vec3.ZERO); LogisticsEngine.receiveHandshake(agentUUID, position(), getCarriedItem()); tacticalState = TacticalState.IDLE; }
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