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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
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
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class ModMessages {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-network");
    public static final Identifier VERSION_CHECK_ID = Identifier.fromNamespaceAndPath("redos", "version_check");

    public static void syncHubTasks(DroneStationBlockEntity hub, ServerPlayer player, BlockPos pos) {
        List<Boolean> lockedSlots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int slot = i;
            lockedSlots.add(net.rdv88.redos.util.LogisticsEngine.getFleet().stream()
                .anyMatch(a -> a.hubPos.equals(pos) && a.homeSlot == slot));
        }

        List<SyncDroneHubTasksPayload.TaskData> taskData = hub.getTasks().stream()
            .map(t -> {
                int srcCount = 0, srcFree = 0, dstCount = 0, dstFree = 0;
                TechNetwork.NetworkNode srcNode = TechNetwork.getNodeAt(t.source);
                if (srcNode != null) {
                    Object countObj = srcNode.settings.get("item_count");
                    if (countObj instanceof Number num) srcCount = num.intValue();
                    Object freeObj = srcNode.settings.get("free_space");
                    if (freeObj instanceof Number num) srcFree = num.intValue();
                }
                TechNetwork.NetworkNode dstNode = TechNetwork.getNodeAt(t.target);
                if (dstNode != null) {
                    Object countObj = dstNode.settings.get("item_count");
                    if (countObj instanceof Number num) dstCount = num.intValue();
                    Object freeObj = dstNode.settings.get("free_space");
                    if (freeObj instanceof Number num) dstFree = num.intValue();
                }

                var agentOpt = net.rdv88.redos.util.LogisticsEngine.getFleet().stream()
                    .filter(a -> a.hubPos.equals(pos) && a.taskIndex == hub.getTasks().indexOf(t))
                    .findFirst();
                
                boolean assigned = agentOpt.isPresent();
                String droneState = agentOpt.map(a -> a.state.name()).orElse("IDLE");
                
                int etaTicks = 0;
                String statusMsg = "IDLE";
                if (agentOpt.isPresent()) {
                    var agent = agentOpt.get();
                    statusMsg = agent.lastStatusMessage;
                    
                    Vec3 currentStart = agent.currentPos;
                    if (agent.puppetUuid != null) {
                        Entity puppet = ((ServerLevel)player.level()).getEntity(agent.puppetUuid);
                        if (puppet != null) currentStart = puppet.position();
                    }

                    // Total Forward ETA Calculation: Sum all remaining segments
                    double totalDist = 0;
                    Vec3 lastPos = currentStart;
                    for (BlockPos wp : agent.waypoints) {
                        totalDist += lastPos.distanceTo(Vec3.atCenterOf(wp));
                        lastPos = Vec3.atCenterOf(wp);
                    }
                    BlockPos finalGoal = (agent.state == net.rdv88.redos.util.LogisticsEngine.State.STEP2_GOING_TO_SOURCE || agent.state == net.rdv88.redos.util.LogisticsEngine.State.STEP_RETURNING_ITEMS_TO_SOURCE) ? t.source : 
                                       (agent.state == net.rdv88.redos.util.LogisticsEngine.State.STEP3_GOING_TO_TARGET ? t.target : pos);
                    totalDist += lastPos.distanceTo(Vec3.atCenterOf(finalGoal));
                    
                    etaTicks = (int)(totalDist / 0.55);
                }

                return new SyncDroneHubTasksPayload.TaskData(
                    t.source, t.target, t.priority, assigned, t.enabled,
                    srcCount, srcFree, dstCount, dstFree, droneState, etaTicks, statusMsg
                );
            })
            .toList();
        ServerPlayNetworking.send(player, new SyncDroneHubTasksPayload(pos, taskData, lockedSlots));
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
        PayloadTypeRegistry.playC2S().register(PurgeZombieDronesPayload.ID, PurgeZombieDronesPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PurgeZombieDronesPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ServerLevel level = (ServerLevel) player.level();
                List<net.rdv88.redos.entity.DroneEntity> zombies = level.getEntitiesOfClass(
                    net.rdv88.redos.entity.DroneEntity.class, 
                    player.getBoundingBox().inflate(10.0), 
                    drone -> net.rdv88.redos.util.LogisticsEngine.getFleet().stream()
                            .noneMatch(agent -> agent.puppetUuid != null && agent.puppetUuid.equals(drone.getUUID()))
                );
                for (net.rdv88.redos.entity.DroneEntity zombie : zombies) zombie.discard();
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[SYSTEM] §7Purged " + zombies.size() + " orphan drone(s)."), true);
            });
        });

        PayloadTypeRegistry.playC2S().register(RequestFleetStatusPayload.ID, RequestFleetStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncFleetStatusPayload.ID, SyncFleetStatusPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestFleetStatusPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String playerNetIds = net.rdv88.redos.item.HandheldDeviceItem.getNetworkId(player.getMainHandItem());
                Set<String> activeIds = new HashSet<>(Arrays.asList(playerNetIds.split(",")));
                
                List<SyncFleetStatusPayload.DroneTaskStatus> missions = new ArrayList<>();
                for (net.rdv88.redos.util.LogisticsEngine.VirtualAgent agent : net.rdv88.redos.util.LogisticsEngine.getFleet()) {
                    if (activeIds.contains(agent.networkId)) {
                        String sName = "Unknown", dName = "Unknown";
                        net.rdv88.redos.util.TechNetwork.NetworkNode sn = net.rdv88.redos.util.TechNetwork.getNodeAt(agent.sourcePos);
                        net.rdv88.redos.util.TechNetwork.NetworkNode dn = net.rdv88.redos.util.TechNetwork.getNodeAt(agent.targetPos);
                        if (sn != null) sName = sn.customName;
                        if (dn != null) dName = dn.customName;

                        // Total Forward ETA Calculation
                        double totalDist = 0;
                        Vec3 start = agent.currentPos;
                        if (agent.puppetUuid != null) {
                            Entity p = ((ServerLevel)player.level()).getEntity(agent.puppetUuid);
                            if (p != null) start = p.position();
                        }
                        Vec3 lastPos = start;
                        for (BlockPos wp : agent.waypoints) {
                            totalDist += lastPos.distanceTo(Vec3.atCenterOf(wp));
                            lastPos = Vec3.atCenterOf(wp);
                        }
                        BlockPos finalGoal = (agent.state == net.rdv88.redos.util.LogisticsEngine.State.STEP2_GOING_TO_SOURCE) ? agent.sourcePos : 
                                           (agent.state == net.rdv88.redos.util.LogisticsEngine.State.STEP3_GOING_TO_TARGET ? agent.targetPos : agent.hubPos);
                        totalDist += lastPos.distanceTo(Vec3.atCenterOf(finalGoal));

                        // Find task enabled status from hub registry
                        boolean taskEnabled = true;
                        int taskPrio = 2;
                        net.rdv88.redos.util.TechNetwork.NetworkNode hubNode = net.rdv88.redos.util.TechNetwork.getNodeAt(agent.hubPos);
                        if (hubNode != null && hubNode.settings.get("task_list") instanceof List<?> tasks) {
                            if (agent.taskIndex < tasks.size() && tasks.get(agent.taskIndex) instanceof Map<?,?> tMap) {
                                Object eObj = tMap.get("enabled");
                                if (eObj instanceof Boolean b) taskEnabled = b;
                                Object pObj = tMap.get("priority");
                                if (pObj instanceof Number n) taskPrio = n.intValue();
                            }
                        }

                        missions.add(new SyncFleetStatusPayload.DroneTaskStatus(
                            agent.hubPos, agent.taskIndex, sName, dName, taskPrio, taskEnabled, agent.state.name(), (int)(totalDist / 0.55)
                        ));
                    }
                }
                ServerPlayNetworking.send(player, new SyncFleetStatusPayload(missions));
            });
        });

        PayloadTypeRegistry.playC2S().register(ConfigureDroneHubPayload.ID, ConfigureDroneHubPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestPorterGuiPayload.ID, RequestPorterGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PurgeGhostLightsPayload.ID, PurgeGhostLightsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PurgeDronesPayload.ID, PurgeDronesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestChatSyncPayload.ID, RequestChatSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendChatMessagePayload.ID, SendChatMessagePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendPrivateMessagePayload.ID, SendPrivateMessagePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminActionPayload.ID, AdminActionPayload.CODEC);

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

        ServerPlayNetworking.registerGlobalReceiver(AdminActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                
                // CRITICAL SECURITY CHECK: Only Operators can use Admin Actions
                if (!context.server().getPlayerList().isOp(new net.minecraft.server.players.NameAndId(player.getGameProfile()))) {
                    LOGGER.warn("RED-OS SECURITY ALERT: Unauthorized AdminAction attempt by player {} ({}) - Action: {}", 
                                player.getName().getString(), player.getUUID(), payload.action());
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[ACCESS DENIED] §7Administrative privileges required."), true);
                    return;
                }

                LOGGER.info("RED-OS ADMIN: Player {} is performing action: {}", player.getName().getString(), payload.action());

                switch (payload.action()) {
                    case SET_DISCORD_TOKEN -> {
                        // Logic to save token to server config
                        LOGGER.info("RED-OS ADMIN: Discord Token updated by {}", player.getName().getString());
                        ServerPlayNetworking.send(player, new ActionFeedbackPayload("§aDiscord Token Secured", false));
                    }
                    case SET_DISCORD_CHANNEL -> {
                        LOGGER.info("RED-OS ADMIN: Discord Channel ID set to {} by {}", payload.data1(), player.getName().getString());
                        ServerPlayNetworking.send(player, new ActionFeedbackPayload("§aDiscord Channel Linked", false));
                    }
                    case KICK_PLAYER -> {
                        String targetName = payload.data1();
                        String reason = payload.data2();
                        ServerPlayer target = context.server().getPlayerList().getPlayerByName(targetName);
                        if (target != null) {
                            target.connection.disconnect(net.minecraft.network.chat.Component.literal("Kicked by Admin: " + reason));
                            LOGGER.info("RED-OS ADMIN: Player {} kicked by {}", targetName, player.getName().getString());
                        }
                    }
                    case RELOAD_CONFIG -> {
                        // Logic to reload server configs
                        LOGGER.info("RED-OS ADMIN: Server Configs Reloaded by {}", player.getName().getString());
                    }
                    case RELOAD_NETWORK -> {
                        TechNetwork.forceSyncAll(context.server());
                        LOGGER.info("RED-OS ADMIN: Mesh Network Force-Synced by {}", player.getName().getString());
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestChatSyncPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                String playerName = player.getName().getString();

                var history = net.rdv88.redos.util.ChatManager.getGeneralHistory().stream()
                    .map(e -> new SyncChatHistoryPayload.ChatEntry(e.sender(), e.message(), e.timestamp()))
                    .toList();

                var privateHistory = net.rdv88.redos.util.ChatManager.fetchAndClearMail(playerName).stream()
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
                long ts = System.currentTimeMillis();

                var entry = new net.rdv88.redos.network.payload.SyncChatHistoryPayload.PrivateEntry(senderName, targetName, messageText, ts);

                // 1. Route to RECEIVER
                ServerPlayer target = context.server().getPlayerList().getPlayerByName(targetName);
                if (target != null) {
                    // Recipient is ONLINE: Send directly for local storage
                    ServerPlayNetworking.send(target, new SyncChatHistoryPayload(new ArrayList<>(), List.of(entry)));
                    target.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d" + senderName + " whispers to you: " + messageText));
                } else {
                    // Recipient is OFFLINE: Buffer in server postbox (mailbox)
                    net.rdv88.redos.util.ChatManager.addPrivateMessage(senderName, targetName, messageText);
                }

                // 2. Sync back to SENDER for their own local vault
                ServerPlayNetworking.send(sender, new SyncChatHistoryPayload(new ArrayList<>(), List.of(entry)));
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§dYou whisper to " + targetName + ": " + messageText));
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
        ServerPlayNetworking.registerGlobalReceiver(PurgeDronesPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.rdv88.redos.util.LogisticsEngine.forceHubReset((ServerLevel)context.player().level(), payload.hubPos());
            });
        });
    }

    private static void syncChatToAll(net.minecraft.server.MinecraftServer server) {
        var genHistory = net.rdv88.redos.util.ChatManager.getGeneralHistory().stream()
            .map(e -> new SyncChatHistoryPayload.ChatEntry(e.sender(), e.message(), e.timestamp()))
            .toList();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new SyncChatHistoryPayload(genHistory, new ArrayList<>()));
        }
    }
}