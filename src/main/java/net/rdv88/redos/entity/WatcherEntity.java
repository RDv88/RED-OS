package net.rdv88.redos.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.rdv88.redos.network.payload.CameraViewResponsePayload;
import net.rdv88.redos.util.CameraViewHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WatcherEntity extends LivingEntity {
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(WatcherEntity.class, EntityDataSerializers.STRING);
    
    private ServerPlayer owner;

    public WatcherEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setInvulnerable(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_NAME, "");
    }

    public void setOwner(ServerPlayer owner) {
        this.owner = owner;
        this.entityData.set(OWNER_NAME, owner.getGameProfile().name());
        this.setHealth(owner.getHealth());
    }

    public String getOwnerName() {
        return this.entityData.get(OWNER_NAME);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        super.actuallyHurt(level, source, amount);
        
        // HYPER-SENSITIVE: Fixes Slime issue (any damage triggers recall)
        if (owner != null && !owner.isRemoved() && amount >= 0.0f) {
            float newHealth = owner.getHealth() - amount;
            owner.setHealth(Math.max(0, newHealth));

            level.getServer().execute(() -> {
                if (CameraViewHandler.isViewingCamera(owner)) {
                    ServerPlayNetworking.send(owner, new CameraViewResponsePayload(false, "RECALL_DUE_TO_DAMAGE", ""));
                    CameraViewHandler.stopViewing(owner);
                }
            });
        }
        this.invulnerableTime = 0;
    }

    public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && owner != null && !owner.isRemoved()) {
            if (this.level().getGameTime() % 20 == 0) {
                AABB searchArea = this.getBoundingBox().inflate(16.0);
                List<Mob> nearbyMobs = this.level().getEntitiesOfClass(Mob.class, searchArea, 
                    mob -> mob instanceof Enemy && mob.getTarget() == null);
                for (Mob mob : nearbyMobs) {
                    mob.setTarget(this);
                }
            }
            if (this.getHealth() != owner.getHealth()) {
                this.setHealth(owner.getHealth());
            }
        }
    }
}
