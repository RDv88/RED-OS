package net.rdv88.redos.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.rdv88.redos.block.entity.*;
import net.rdv88.redos.network.payload.SyncProfilesPayload;
import net.rdv88.redos.util.TechNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HandheldDeviceItem extends Item {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos");

    public HandheldDeviceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (!level.isClientSide() && player != null) {
            Object be = level.getBlockEntity(pos);
            String deviceId = null;

            if (be instanceof WirelessCameraBlockEntity cam) deviceId = cam.getNetworkId();
            else if (be instanceof LongRangeTransmitterBlockEntity lrt) deviceId = lrt.getNetworkId();
            else if (be instanceof ShortRangeTransmitterBlockEntity srt) deviceId = srt.getNetworkId();
            else if (be instanceof SmartMotionSensorBlockEntity sensor) deviceId = sensor.getNetworkId();
            else if (be instanceof RemoteRedstoneTriggerBlockEntity trigger) deviceId = trigger.getNetworkId();
            else if (be instanceof QuantumPorterBlockEntity porter) deviceId = porter.getNetworkId();

            if (deviceId != null) {
                String handheldId = getNetworkId(stack);
                boolean matches = handheldId.equals(deviceId) || handheldId.contains(deviceId);
                if (matches || player.isCreative()) return InteractionResult.SUCCESS;
                else player.displayClientMessage(Component.literal("§cAccess Denied: Network ID mismatch."), true);
                return InteractionResult.CONSUME;
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
             // VERIFIED FIX: Use fully qualified name to prevent class loading on server
             net.rdv88.redos.client.ClientUtil.openHandheldScreen(stack, player);
        }
        return InteractionResult.SUCCESS;
    }

    public static void setProfiles(ItemStack stack, List<SyncProfilesPayload.ProfileData> profiles) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = new ListTag();
        for (SyncProfilesPayload.ProfileData p : profiles) {
            CompoundTag pTag = new CompoundTag();
            pTag.put("name", net.minecraft.nbt.StringTag.valueOf(p.name()));
            pTag.put("id", net.minecraft.nbt.StringTag.valueOf(p.id()));
            list.add(pTag);
        }
        tag.put("profiles", list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static List<SyncProfilesPayload.ProfileData> getProfiles(ItemStack stack) {
        List<SyncProfilesPayload.ProfileData> profiles = new ArrayList<>();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.getList("profiles").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
                list.getCompound(i).ifPresent(pTag -> {
                    profiles.add(new SyncProfilesPayload.ProfileData(pTag.getString("name").orElse(""), pTag.getString("id").orElse("")));
                });
            }
        });
        if (profiles.isEmpty()) profiles.add(new SyncProfilesPayload.ProfileData("Default", "00000"));
        return profiles;
    }

    public static void setNetworkId(ItemStack stack, String id) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("networkId", net.minecraft.nbt.StringTag.valueOf(id));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getNetworkId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getString("networkId").orElse("00000");
    }
}
