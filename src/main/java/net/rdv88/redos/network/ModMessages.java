package net.rdv88.redos.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import net.rdv88.redos.block.custom.QuantumPorterBlock;
import net.rdv88.redos.block.entity.*;
import net.rdv88.redos.network.payload.*;
import net.rdv88.redos.util.CameraViewHandler;
import net.rdv88.redos.util.TechNetwork;
import net.rdv88.redos.item.HandheldDeviceItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ModMessages {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-network");
    public static final Identifier VERSION_CHECK_ID = Identifier.fromNamespaceAndPath("redos", "version_check");

    private static void syncHubTasks(DroneStationBlockEntity hub, ServerPlayer player, BlockPos pos) {
        List<SyncDroneHubTasksPayload.TaskData> taskData = hub.getTasks().stream()
            .map(t -> {
                int srcCount = 0, srcFree = 0, dstCount = 0, dstFree = 0;
                
                // Fetch live RAM data for Source
                TechNetwork.NetworkNode srcNode = TechNetwork.getNodeAt(t.source);
                if (srcNode != null) {
                    Object countObj = srcNode.settings.get("item_count");
                    if (countObj instanceof Number num) srcCount = num.intValue();
                    
                    Object freeObj = srcNode.settings.get("free_space");
                    if (freeObj instanceof Number num) srcFree = num.intValue();
                }

                // Fetch live RAM data for Target
                TechNetwork.NetworkNode dstNode = TechNetwork.getNodeAt(t.target);
                if (dstNode != null) {
                    Object countObj = dstNode.settings.get("item_count");
                    if (countObj instanceof Number num) dstCount = num.intValue();
                    
                    Object freeObj = dstNode.settings.get("free_space");
                    if (freeObj instanceof Number num) dstFree = num.intValue();
                }

                return new SyncDroneHubTasksPayload.TaskData(
                    t.source, t.target, t.priority, t.isAssigned, t.enabled,
                    srcCount, srcFree, dstCount, dstFree
                );
            })
            .toList();
        ServerPlayNetworking.send(player, new SyncDroneHubTasksPayload(pos, taskData));
    }

    public static void registerNetworking() {
        LOGGER.info("RED-OS: Registering Network Channels...");

        PayloadTypeRegistry.playC2S().register(RequestCameraViewPayload.ID, RequestCameraViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StopCameraViewPayload.ID, StopCameraViewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SyncProfilesPayload.ID, SyncProfilesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureDevicePayload.ID, ConfigureDevicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TriggerActivationPayload.ID, TriggerActivationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestTeleportPayload.ID, RequestTeleportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureHandheldPayload.ID, ConfigureHandheldPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureSensorSettingsPayload.ID, ConfigureSensorSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureIOTagSettingsPayload.ID, ConfigureIOTagSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigureDroneHubPayload.ID, ConfigureDroneHubPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestPorterGuiPayload.ID, RequestPorterGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PurgeGhostLightsPayload.ID, PurgeGhostLightsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestChatSyncPayload.ID, RequestChatSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendChatMessagePayload.ID, SendChatMessagePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendPrivateMessagePayload.ID, SendPrivateMessagePayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncNetworkNodesPayload.ID, SyncNetworkNodesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncHandheldDataPayload.ID, SyncHandheldDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CameraViewResponsePayload.ID, CameraViewResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ActionFeedbackPayload.ID, ActionFeedbackPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPermissionsPayload.ID, SyncPermissionsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PorterGuiResponsePayload.ID, PorterGuiResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncDroneHubTasksPayload.ID, SyncDroneHubTasksPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncChatHistoryPayload.ID, SyncChatHistoryPayload.CODEC);

        // Server Side Handlers
        PayloadTypeRegistry.playC2S().register(RequestSyncDroneTasksPayload.ID, RequestSyncDroneTasksPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestChatSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String playerName = player.getName().getString();

                var history = net.rdv88.redos.util.ChatManager.getGeneralHistory().stream()
                    .map(e -> new SyncChatHistoryPayload.ChatEntry(e.sender(), e.message(), e.timestamp()))
                    .toList();

                var privateHistory = net.rdv88.redos.util.ChatManager.getPrivateHistoryFor(playerName).stream()
                    .map(e -> new SyncChatHistoryPayload.PrivateEntry(e.from(), e.to(), e.message(), e.timestamp()))
                    .toList();

                ServerPlayNetworking.send(player, new SyncChatHistoryPayload(history, privateHistory));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SendChatMessagePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String senderName = player.getName().getString();
                String messageText = payload.message();

                // 1. Add to RED-OS History (RAM + Disk)
                net.rdv88.redos.util.ChatManager.addMessage(senderName, messageText);

                // 2. Broadcast to regular Minecraft Chat
                net.minecraft.network.chat.Component chatComponent = net.minecraft.network.chat.Component.literal("<" + senderName + "> " + messageText);
                context.server().getPlayerList().broadcastSystemMessage(chatComponent, false);

                // 3. Sync update to all Handheld users (General + their own Private)
                syncChatToAll(context.server());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SendPrivateMessagePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer sender = context.player();
                String senderName = sender.getName().getString();
                String targetName = payload.targetName();
                String messageText = payload.message();

                // 1. Add to RED-OS Private History
                net.rdv88.redos.util.ChatManager.addPrivateMessage(senderName, targetName, messageText);

                // 2. Send actual Minecraft Whisper
                ServerPlayer target = context.server().getPlayerList().getPlayerByName(targetName);
                if (target != null) {
                    target.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d" + senderName + " whispers to you: " + messageText));
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§dYou whisper to " + targetName + ": " + messageText));
                }

                // 3. Sync update to both parties
                syncChatToAll(context.server());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestCameraViewPayload.ID, (payload, context) -> {
            context.server().execute(() -> { CameraViewHandler.startViewing(context.player(), payload.pos()); });
        });

        ServerPlayNetworking.registerGlobalReceiver(StopCameraViewPayload.ID, (payload, context) -> {
            context.server().execute(() -> { CameraViewHandler.stopViewing(context.player()); });
        });

        ServerPlayNetworking.registerGlobalReceiver(SyncProfilesPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof HandheldDeviceItem)) stack = player.getOffhandItem();
                if (stack.getItem() instanceof HandheldDeviceItem) {
                    HandheldDeviceItem.setProfiles(stack, payload.profiles());
                    ServerPlayNetworking.send(player, new ActionFeedbackPayload("§aProfiles Saved", false));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigureHandheldPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (payload.newId().equals("STOP_TRACKING")) {
                    TechNetwork.stopTrackingHandheld(player);
                } else {
                    ItemStack stack = player.getMainHandItem();
                    if (!(stack.getItem() instanceof HandheldDeviceItem)) stack = player.getOffhandItem();
                    if (stack.getItem() instanceof HandheldDeviceItem) {
                        HandheldDeviceItem.setNetworkId(stack, payload.newId());
                        TechNetwork.startTrackingHandheld(player, payload.newId());
                        var data = TechNetwork.calculateVisibleDevices(player, payload.newId());
                        ServerPlayNetworking.send(player, new SyncHandheldDataPayload(data));
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigureDevicePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                BlockPos targetPos = payload.pos();
                BlockState state = player.level().getBlockState(targetPos);
                
                if (state.is(net.rdv88.redos.block.ModBlocks.QUANTUM_PORTER) && state.getValue(QuantumPorterBlock.HALF) == DoubleBlockHalf.UPPER) {
                    targetPos = targetPos.below();
                }

                net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(targetPos);
                String deviceLabel = "Device";
                TechNetwork.NodeType type = null;
                String serial = "LEGACY";
                
                String finalName = payload.newName().trim();
                boolean nameIsBlank = finalName.isEmpty();
                String coords = String.format(" (%d, %d, %d)", targetPos.getX(), targetPos.getY(), targetPos.getZ());

                if (be instanceof WirelessCameraBlockEntity cam) { 
                    if (nameIsBlank) finalName = "Wireless Camera" + coords;
                    cam.setCameraName(finalName); cam.setNetworkId(payload.newId()); deviceLabel = "Camera"; type = TechNetwork.NodeType.CAMERA; serial = cam.getSerial(); 
                }
                else if (be instanceof SmartMotionSensorBlockEntity sensor) { 
                    if (nameIsBlank) finalName = "Sensor" + coords;
                    sensor.setName(finalName); sensor.setNetworkId(payload.newId()); deviceLabel = "Sensor"; type = TechNetwork.NodeType.SENSOR; serial = sensor.getSerial(); 
                }
                else if (be instanceof RemoteRedstoneTriggerBlockEntity trigger) { 
                    if (nameIsBlank) finalName = "Trigger" + coords;
                    trigger.setName(finalName); trigger.setNetworkId(payload.newId()); deviceLabel = "Trigger"; type = TechNetwork.NodeType.TRIGGER; serial = trigger.getSerial(); 
                }
                else if (be instanceof ShortRangeTransmitterBlockEntity srt) { 
                    if (nameIsBlank) finalName = "SR Transmitter" + coords;
                    srt.setName(finalName); srt.setNetworkId(payload.newId()); deviceLabel = "SR Transmitter"; type = TechNetwork.NodeType.SHORT_RANGE; serial = srt.getSerial(); 
                }
                else if (be instanceof LongRangeTransmitterBlockEntity lrt) { 
                    if (nameIsBlank) finalName = "Long Range Transmitter" + coords;
                    lrt.setName(finalName); lrt.setNetworkId(payload.newId()); deviceLabel = "LR Transmitter"; type = TechNetwork.NodeType.LONG_RANGE; serial = lrt.getSerial(); 
                }
                else if (be instanceof QuantumPorterBlockEntity porter) { 
                    if (nameIsBlank) finalName = "Porter" + coords;
                    porter.setName(finalName); porter.setNetworkId(payload.newId()); deviceLabel = "Quantum Porter"; type = TechNetwork.NodeType.PORTER; serial = porter.getSerial(); 
                }
                else if (be instanceof IOTagBlockEntity tag) {
                    if (nameIsBlank) finalName = "IO Tag" + coords;
                    tag.setName(finalName); tag.setNetworkId(payload.newId()); deviceLabel = "IO Tag"; type = TechNetwork.NodeType.IO_TAG; serial = tag.getSerial();
                }
                else if (be instanceof DroneStationBlockEntity hub) {
                    if (nameIsBlank) finalName = "Drone Hub" + coords;
                    hub.setName(finalName); hub.setNetworkId(payload.newId()); deviceLabel = "Drone Hub"; type = TechNetwork.NodeType.DRONE_STATION; serial = hub.getSerial();
                }

                if (type != null) {
                    TechNetwork.registerNode(player.level(), targetPos, payload.newId(), finalName, type, serial);
                    ItemStack handheld = player.getMainHandItem();
                    if (!(handheld.getItem() instanceof HandheldDeviceItem)) handheld = player.getOffhandItem();
                    String ids = HandheldDeviceItem.getNetworkId(handheld);
                    var data = TechNetwork.calculateVisibleDevices(player, ids);
                    ServerPlayNetworking.send(player, new SyncHandheldDataPayload(data));
                    ServerPlayNetworking.send(player, new ActionFeedbackPayload("§aUpdated " + deviceLabel, false));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestPorterGuiPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                BlockPos pos = payload.pos();
                BlockState state = player.level().getBlockState(pos);
                BlockPos basePos = state.getValue(QuantumPorterBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                
                net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(basePos);
                if (!(be instanceof QuantumPorterBlockEntity porter)) return;

                ItemStack handheld = null;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (s.getItem() instanceof HandheldDeviceItem) { handheld = s; break; }
                }

                int status = 0; 
                String handheldIds = "";
                
                if (handheld == null) {
                    status = 2; 
                } else {
                    handheldIds = HandheldDeviceItem.getNetworkId(handheld);
                    String porterId = porter.getNetworkId();
                    boolean hasAccess = Arrays.asList(handheldIds.split(",")).contains(porterId);
                    if (!hasAccess) {
                        status = 1; 
                    }
                }

                if (status == 0) {
                    TechNetwork.startTrackingHandheld(player, handheldIds);
                    var data = TechNetwork.calculateVisibleDevices(player, handheldIds);
                    ServerPlayNetworking.send(player, new SyncHandheldDataPayload(data));
                }

                ServerPlayNetworking.send(player, new PorterGuiResponsePayload(basePos, porter.getName(), status));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TriggerActivationPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.level.block.entity.BlockEntity be = context.player().level().getBlockEntity(payload.pos());
                if (be instanceof RemoteRedstoneTriggerBlockEntity trigger) {
                    trigger.trigger();
                    ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§aTrigger Activated", false));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestTeleportPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                BlockPos sourcePos = payload.sourcePos();
                BlockPos targetPos = payload.targetPos();
                ServerLevel level = (ServerLevel) player.level();
                
                // Activeer VERTREK porter
                net.minecraft.world.level.block.entity.BlockEntity sourceBe = level.getBlockEntity(sourcePos);
                if (sourceBe instanceof QuantumPorterBlockEntity porter) porter.activate();

                BlockState state = level.getBlockState(targetPos);
                Direction facing = Direction.NORTH;
                if (state.hasProperty(QuantumPorterBlock.FACING)) facing = state.getValue(QuantumPorterBlock.FACING);
                Vec3 landVec = new Vec3(targetPos.getX() + 0.5 + facing.getStepX(), targetPos.getY(), targetPos.getZ() + 0.5 + facing.getStepZ());
                TeleportTransition transition = new TeleportTransition(level, landVec, Vec3.ZERO, facing.getOpposite().toYRot(), 0, TeleportTransition.DO_NOTHING);
                player.teleport(transition);
                player.level().playSound(null, targetPos, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                
                // Activeer AANKOMST porter
                net.minecraft.world.level.block.entity.BlockEntity targetBe = level.getBlockEntity(targetPos);
                if (targetBe instanceof QuantumPorterBlockEntity porter) porter.activate();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigureSensorSettingsPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.level.block.entity.BlockEntity be = context.player().level().getBlockEntity(payload.pos());
                if (be instanceof SmartMotionSensorBlockEntity sensor) {
                    sensor.setSettings(payload.detectPlayers(), payload.detectMobs(), payload.detectAnimals(), payload.detectVillagers(), payload.alertsEnabled(), payload.range(), payload.holdTime());
                    ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§aSettings Updated", false));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigureIOTagSettingsPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.level.block.entity.BlockEntity be = context.player().level().getBlockEntity(payload.pos());
                if (be instanceof IOTagBlockEntity tag) {
                    tag.setSettings(payload.filterItem());
                    ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§aTag Settings Updated", false));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestSyncDroneTasksPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.level.block.entity.BlockEntity be = context.player().level().getBlockEntity(payload.pos());
                if (be instanceof DroneStationBlockEntity hub) {
                    syncHubTasks(hub, context.player(), payload.pos());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PurgeGhostLightsPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ServerLevel level = (ServerLevel) player.level();
                BlockPos playerPos = player.blockPosition();
                int removed = 0;
                int radius = 64;

                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius / 2; y <= radius / 2; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos p = playerPos.offset(x, y, z);
                            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                                level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                                removed++;
                            }
                        }
                    }
                }
                ServerPlayNetworking.send(player, new ActionFeedbackPayload("§aPurge Complete: " + removed + " lights removed", false));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigureDroneHubPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.world.level.block.entity.BlockEntity be = context.player().level().getBlockEntity(payload.pos());
                if (be instanceof DroneStationBlockEntity hub) {
                    if (payload.action().equals("ADD_TASK")) {
                        hub.addTask(payload.sourcePos(), payload.targetPos(), payload.priority());
                        syncHubTasks(hub, context.player(), payload.pos());
                        ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§aTask Assigned to Hub", false));
                    }
                    else if (payload.action().equals("REMOVE_TASK")) {
                        hub.removeTask(payload.priority()); // Index is passed in priority field for this action
                        syncHubTasks(hub, context.player(), payload.pos());
                        ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§cTask Removed", false));
                    }
                    else if (payload.action().equals("UPDATE_TASK")) {
                        hub.updateTask(payload.droneIndex(), payload.priority()); // Index in droneIndex, Prio in priority
                        syncHubTasks(hub, context.player(), payload.pos());
                        ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§bTask Priority Updated", false));
                    }
                    else if (payload.action().equals("TOGGLE_TASK")) {
                        hub.toggleTask(payload.priority()); // Index is passed in priority field
                        syncHubTasks(hub, context.player(), payload.pos());
                    }
                    else if (payload.action().equals("INSTALL_DRONE")) {
                        net.minecraft.world.item.ItemStack hand = context.player().getMainHandItem();
                        if (hand.is(net.rdv88.redos.item.ModItems.DRONE_UNIT)) {
                            hub.getInventory().setItem(payload.droneIndex(), hand.split(1));
                            ServerPlayNetworking.send(context.player(), new ActionFeedbackPayload("§aDrone Integrated", false));
                        }
                    }
                }
            });
        });
    }

    private static void syncChatToAll(net.minecraft.server.MinecraftServer server) {
        var genHistory = net.rdv88.redos.util.ChatManager.getGeneralHistory().stream()
            .map(e -> new SyncChatHistoryPayload.ChatEntry(e.sender(), e.message(), e.timestamp()))
            .toList();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String pName = p.getName().getString();
            var privHistory = net.rdv88.redos.util.ChatManager.getPrivateHistoryFor(pName).stream()
                .map(e -> new SyncChatHistoryPayload.PrivateEntry(e.from(), e.to(), e.message(), e.timestamp()))
                .toList();
            ServerPlayNetworking.send(p, new SyncChatHistoryPayload(genHistory, privHistory));
        }
    }
}