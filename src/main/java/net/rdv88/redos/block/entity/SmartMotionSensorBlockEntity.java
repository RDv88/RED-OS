package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.block.custom.SmartMotionSensorBlock;
import net.rdv88.redos.util.TechNetwork;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SmartMotionSensorBlockEntity extends BlockEntity {
    public String name = "Smart Sensor";
    public String networkId = "00000";
    private String serial = UUID.randomUUID().toString();
    private boolean detectPlayers = true;
    private boolean detectMobs = false;
    private boolean detectAnimals = false;
    private boolean detectVillagers = false;
    private boolean alertsEnabled = true;
    private int detectionRange = 3;
    private int holdTime = 30; // 1.5 seconds (30 ticks)
    private int logCooldown = 0;
    private int deactivationTimer = 0;

    public SmartMotionSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_MOTION_SENSOR_BLOCK_ENTITY, pos, state);
    }

    public void setName(String name) { this.name = name; setChanged(); }
    public String getName() { return name; }
    public void setNetworkId(String id) { this.networkId = id; setChanged(); }
    public String getNetworkId() { return networkId; }
    public String getSerial() { return serial; }
    
    public void setSettings(boolean p, boolean m, boolean a, boolean v, boolean alerts, int range, int hold) {
        this.detectPlayers = p; 
        this.detectMobs = m; 
        this.detectAnimals = a; 
        this.detectVillagers = v; 
        this.alertsEnabled = alerts;
        this.detectionRange = Math.clamp(range, 1, 15);
        this.holdTime = Math.clamp(hold, 0, 100);
        setChanged();
        if (this.level != null && !this.level.isClientSide()) registerInNetwork();
    }

    private void registerInNetwork() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("detect_players", this.detectPlayers);
        settings.put("detect_mobs", this.detectMobs);
        settings.put("detect_animals", this.detectAnimals);
        settings.put("detect_villagers", this.detectVillagers);
        settings.put("alerts_enabled", this.alertsEnabled);
        settings.put("range", this.detectionRange);
        settings.put("hold_time", this.holdTime);
        TechNetwork.registerNode(level, worldPosition, this.networkId, this.name, TechNetwork.NodeType.SENSOR, this.serial, settings);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SmartMotionSensorBlockEntity blockEntity) {
        if (level.isClientSide()) return;

        if (level.getGameTime() % 400 == 0) blockEntity.registerInNetwork();

        if (level.getGameTime() % 2 == 0) {
            AttachFace face = state.getValue(SmartMotionSensorBlock.FACE);
            Direction lookDir = switch (face) {
                case CEILING -> Direction.DOWN;
                case FLOOR -> Direction.UP;
                default -> state.getValue(SmartMotionSensorBlock.FACING);
            };

            double range = (double)blockEntity.detectionRange;
            Vec3 center = pos.getCenter();
            Vec3 rayStart = center.add(new Vec3(lookDir.getStepX(), lookDir.getStepY(), lookDir.getStepZ()).scale(0.55));
            
            AABB area = new AABB(pos);
            area = area.expandTowards(lookDir.getStepX() * range, lookDir.getStepY() * range, lookDir.getStepZ() * range);
            if (lookDir.getAxis() == Direction.Axis.X) area = area.inflate(0, 5, 5);
            else if (lookDir.getAxis() == Direction.Axis.Y) area = area.inflate(5, 0, 5);
            else area = area.inflate(5, 5, 0);

            List<Entity> entities = level.getEntitiesOfClass(Entity.class, area, e -> {
                if (!(e instanceof LivingEntity)) return false;
                boolean match = false;
                if (e instanceof Player && blockEntity.detectPlayers) match = true;
                else if (e instanceof Enemy && blockEntity.detectMobs) match = true;
                else if (e instanceof Animal && blockEntity.detectAnimals) match = true;
                else if ((e instanceof Villager || e instanceof Npc) && blockEntity.detectVillagers) match = true;
                if (!match) return false;
                ClipContext context = new ClipContext(rayStart, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e);
                BlockHitResult hit = level.clip(context);
                return hit.getType() == HitResult.Type.MISS;
            });

            boolean detected = !entities.isEmpty();
            boolean currentState = state.getValue(SmartMotionSensorBlock.POWERED);

            if (detected) {
                blockEntity.deactivationTimer = blockEntity.holdTime;
                if (!currentState) {
                    blockEntity.updateState(level, pos, state, true);
                }
            } else {
                if (blockEntity.deactivationTimer > 0) {
                    blockEntity.deactivationTimer -= 2;
                } else if (currentState) {
                    blockEntity.updateState(level, pos, state, false);
                }
            }
        }

        if (blockEntity.logCooldown > 0) blockEntity.logCooldown--;
    }

    private void updateState(Level level, BlockPos pos, BlockState state, boolean powered) {
        BlockState newState = state.setValue(SmartMotionSensorBlock.POWERED, powered);
        level.setBlock(pos, newState, 3);
        if (newState.getBlock() instanceof SmartMotionSensorBlock sensorBlock) {
            sensorBlock.updateNeighbors(newState, level, pos);
        }
        if (powered && logCooldown <= 0 && alertsEnabled) {
            TechNetwork.broadcastToNetwork(level, networkId, "§6[Sensor] §fMotion Detected at " + name);
            logCooldown = 100;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("name", name);
        output.putString("networkId", networkId);
        output.putString("serial", serial);
        output.putBoolean("detectPlayers", detectPlayers);
        output.putBoolean("detectMobs", detectMobs);
        output.putBoolean("detectAnimals", detectAnimals);
        output.putBoolean("detectVillagers", detectVillagers);
        output.putBoolean("alertsEnabled", alertsEnabled);
        output.putInt("detectionRange", detectionRange);
        output.putInt("holdTime", holdTime);
        output.putInt("deactivationTimer", deactivationTimer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.name = input.getStringOr("name", "Smart Sensor");
        this.networkId = input.getStringOr("networkId", "00000");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());
        this.detectPlayers = input.getBooleanOr("detectPlayers", true);
        this.detectMobs = input.getBooleanOr("detectMobs", false);
        this.detectAnimals = input.getBooleanOr("detectAnimals", false);
        this.detectVillagers = input.getBooleanOr("detectVillagers", false);
        this.alertsEnabled = input.getBooleanOr("alertsEnabled", true);
        this.detectionRange = input.getIntOr("detectionRange", 3);
        this.holdTime = input.getIntOr("holdTime", 30);
        this.deactivationTimer = input.getIntOr("deactivationTimer", 0);
    }
}