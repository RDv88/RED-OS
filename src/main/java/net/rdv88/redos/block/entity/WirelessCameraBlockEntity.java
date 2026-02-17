package net.rdv88.redos.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.rdv88.redos.util.TechNetwork;
import java.util.UUID;

public class WirelessCameraBlockEntity extends BlockEntity {
    public String cameraName = "IP Camera";
    public String networkId = "00000";
    private float yaw = 0;
    private float pitch = 0;
    private String serial = UUID.randomUUID().toString();

    public WirelessCameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_CAMERA_BLOCK_ENTITY, pos, state);
    }

    public void setCameraName(String name) { this.cameraName = name; setChanged(); }
    public String getCameraName() { return cameraName; }
    public void setNetworkId(String id) { this.networkId = id; setChanged(); }
    public String getNetworkId() { return networkId; }
    public void setRotation(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; setChanged(); }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getSerial() { return serial; }

    public static void tick(Level level, BlockPos pos, BlockState state, WirelessCameraBlockEntity blockEntity) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 400 == 0) {
            TechNetwork.registerNode(level, pos, blockEntity.networkId, blockEntity.cameraName, TechNetwork.NodeType.CAMERA, blockEntity.serial);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        TechNetwork.removeNode(level, pos);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("cameraName", cameraName);
        output.putString("networkId", networkId);
        output.putString("serial", serial);
        output.putFloat("yaw", yaw);
        output.putFloat("pitch", pitch);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.cameraName = input.getStringOr("cameraName", "IP Camera");
        this.networkId = input.getStringOr("networkId", "00000");
        this.serial = input.getStringOr("serial", UUID.randomUUID().toString());
        this.yaw = input.getFloatOr("yaw", 0f);
        this.pitch = input.getFloatOr("pitch", 0f);
    }
}
