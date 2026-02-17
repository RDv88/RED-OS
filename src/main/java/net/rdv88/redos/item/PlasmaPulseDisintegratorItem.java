package net.rdv88.redos.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PlasmaPulseDisintegratorItem extends Item {
    public PlasmaPulseDisintegratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(1.0f), List.of(), List.of(), List.of()));
        user.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide()) return;

        int usedTicks = getUseDuration(stack, user) - remainingUseTicks;
        
        if (user instanceof Player player) {
            float progress = Math.min(1.0f, usedTicks / 40.0f);
            player.experienceProgress = progress;
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(progress, sp.totalExperience, sp.experienceLevel));
            }
        }

        // CONTINUOUS CHARGING SOUND (Beacon Ambient)
        if (usedTicks % 10 == 0) {
            // Cap the pitch at 2.0 (100% charge) but keep playing the pulse
            float pitch = Math.min(2.0f, 0.5f + (usedTicks / 40.0f) * 1.5f);
            level.playSound(null, user.getX(), user.getY(), user.getZ(), 
                SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0f, pitch);
        }

        // CONTINUOUS PARTICLES
        if (usedTicks > 5) {
            ((ServerLevel)level).sendParticles(ParticleTypes.ELECTRIC_SPARK, 
                user.getX(), user.getY() + 1.2, user.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(0.0f), List.of(), List.of(), List.of()));
        
        if (!(user instanceof Player player)) return false;

        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(sp.experienceProgress, sp.totalExperience, sp.experienceLevel));
        }

        int usedTicks = getUseDuration(stack, user) - remainingUseTicks;
        if (usedTicks >= 10) {
            float chargeFactor = Math.min(1.0f, usedTicks / 40.0f);
            firePulse(level, player, stack, chargeFactor);
            return true;
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
            return false;
        }
    }

    private void firePulse(Level level, Player player, ItemStack stack, float chargeFactor) {
        if (level.isClientSide()) return;

        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.8f + (chargeFactor * 0.4f));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 1.5f);

        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition();
        
        for (int i = 1; i < 50; i += 2) {
            Vec3 partPos = start.add(look.scale(i));
            if (i < 10) {
                ((ServerLevel)level).sendParticles(ParticleTypes.SONIC_BOOM, 
                    partPos.x, partPos.y, partPos.z, 1, 0, 0, 0, 0);
            }
            ((ServerLevel)level).sendParticles(ParticleTypes.GLOW, 
                partPos.x, partPos.y, partPos.z, 2, 0.1, 0.1, 0.1, 0.02);
        }

        double maxRange = 50.0;
        double angleThreshold = 0.96;

        AABB targetArea = player.getBoundingBox().inflate(maxRange);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, targetArea, 
            e -> e != player && e.isAlive());

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.position().subtract(player.position()).normalize();
            double dot = toTarget.dot(look);
            
            boolean intersects = target.getBoundingBox().clip(start, start.add(look.scale(maxRange))).isPresent();

            if (dot > angleThreshold || intersects) {
                double dist = Math.sqrt(target.distanceToSqr(player));
                if (dist > maxRange) continue;

                float distanceFactor = 1.0f - (float)(Math.floor(dist / 10.0) * 0.2);
                distanceFactor = Math.max(0.1f, distanceFactor);

                float finalDamage = 60.0f * chargeFactor * distanceFactor;
                target.hurt(level.damageSources().playerAttack(player), finalDamage);
                
                Vec3 kb = look.scale(2.0 * chargeFactor);
                target.push(kb.x, 0.3, kb.z);
            }
        }

        stack.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        player.getCooldowns().addCooldown(stack, 40); 
    }
}