package net.rdv88.redos.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.rdv88.redos.item.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JetpackHandler {
    private static final Map<UUID, Float> ENERGY = new HashMap<>();
    private static final Map<UUID, Float> CURRENT_THRUST = new HashMap<>();
    private static final Map<UUID, Long> LAST_BOOST_TIME = new HashMap<>();
    private static final Map<UUID, Boolean> IS_BOOSTING = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_SUPERSONIC = new HashMap<>();

    public static void setBoosting(UUID uuid, boolean boosting) {
        IS_BOOSTING.put(uuid, boosting);
    }

    public static void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() != ModItems.ION_EXO_CHESTPLATE) {
            if (ENERGY.containsKey(uuid)) {
                ENERGY.remove(uuid);
                CURRENT_THRUST.remove(uuid);
                IS_BOOSTING.remove(uuid);
                WAS_SUPERSONIC.remove(uuid);
                player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            }
            return;
        }

        float energy = ENERGY.getOrDefault(uuid, 1.0f);
        boolean isBoosting = IS_BOOSTING.getOrDefault(uuid, false) && player.isFallFlying();
        long lastBoost = LAST_BOOST_TIME.getOrDefault(uuid, 0L);
        Vec3 vel = player.getDeltaMovement();
        double speedMs = vel.length() * 20.0;
        if (player.onGround() && speedMs < 2.0) speedMs = 0.0;

        if (isBoosting && energy > 0.01f) {
            if (player.horizontalCollision) {
                CURRENT_THRUST.put(uuid, 0.0f);
            } else {
                float thrust = CURRENT_THRUST.getOrDefault(uuid, 0.0f);
                thrust = Math.min(0.7f, thrust + 0.0125f);
                CURRENT_THRUST.put(uuid, thrust);
                LAST_BOOST_TIME.put(uuid, player.level().getGameTime());

                Vec3 look = player.getLookAngle();
                double boostX = look.x * thrust;
                double boostZ = look.z * thrust;
                double boostY = (look.y * thrust * 0.5) + 0.05;

                // GLIDE BYPASS: Let momentum carry over smoothly!
                boolean coasting = speedMs > 60.0 && look.y >= -0.5;
                if (!coasting) {
                    player.addDeltaMovement(new Vec3(boostX, boostY, boostZ));
                    energy = Math.max(0.0f, energy - 0.0125f);
                    
                    // JETPACK EXHAUST PARTICLES (Blue Flames + Electric Sparks)
                    ServerLevel sLevel = (ServerLevel) player.level();
                    Vec3 pos = player.position().add(0, 0.5, 0).subtract(look.scale(0.5));
                    sLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.05);
                    sLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 1, 0.2, 0.2, 0.2, 0.1);
                }

                player.fallDistance = 0;
                if (!coasting) player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        } else {
            CURRENT_THRUST.put(uuid, 0.0f);
            if (player.level().getGameTime() - lastBoost > 30) {
                float regenRate = player.onGround() ? 0.015f : 0.006f;
                if (!player.onGround() && player.isFallFlying() && player.getDeltaMovement().y < -0.6) regenRate = 0.025f;
                energy = Math.min(1.0f, energy + regenRate);
            }
        }

        if (player.isFallFlying() && speedMs > 80.0) {
            int sl = (int) Math.min(4, (speedMs - 80.0) / 5.0);
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 2, sl, false, false, false));
            if (!WAS_SUPERSONIC.getOrDefault(uuid, false)) {
                net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, player.level());
                bolt.setPos(player.getX(), player.getY(), player.getZ());
                bolt.setVisualOnly(false);
                player.level().addFreshEntity(bolt);
            }
        }
        WAS_SUPERSONIC.put(uuid, player.isFallFlying() && speedMs >= 80.0);

        ENERGY.put(uuid, energy);
        player.connection.send(new ClientboundSetExperiencePacket(energy, player.totalExperience, player.experienceLevel));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(String.format("§bSpeed: §f%.1f m/s  §eEnergy: §f%.0f%%", speedMs, energy * 100)), true);
        
        IS_BOOSTING.put(uuid, false);
    }
}
