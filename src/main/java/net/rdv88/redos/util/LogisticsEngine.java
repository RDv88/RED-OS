package net.rdv88.redos.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.entity.DroneEntity;
import net.rdv88.redos.entity.ModEntities;
import net.rdv88.redos.item.ModItems;
import net.rdv88.redos.block.entity.DroneStationBlockEntity;
import net.rdv88.redos.block.entity.IOTagBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LogisticsEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-logistics");
    private static final ConcurrentHashMap<UUID, VirtualAgent> FLEET = new ConcurrentHashMap<>();
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_DELAY_MS = 150000;
    private static long lastSaveTime = 0;
    private static boolean initialScanDone = false;

    public enum State { STEP1_INITIALIZING, STEP2_GOING_TO_SOURCE, STEP3_GOING_TO_TARGET, STEP4_RETURNING_HOME, STEP5_TERMINATING }

    public static class VirtualAgent {
        public final UUID uuid;
        public final BlockPos hubPos;
        public final String networkId;
        public final int homeSlot;
        public BlockPos sourcePos, targetPos;
        public List<BlockPos> waypoints = new ArrayList<>();
        public Vec3 currentPos;
        public State state;
        public ItemStack carriedItem = ItemStack.EMPTY;
        public int taskIndex = -1;
        public transient UUID puppetUuid = null;
        public transient boolean handshakeReceived = false;
        public transient int ghostTicksRemaining = 0;

        public VirtualAgent(UUID uuid, BlockPos hub, String netId, int slot) {
            this.uuid = uuid; this.hubPos = hub; this.networkId = netId; this.homeSlot = slot;
            this.state = State.STEP1_INITIALIZING;
            this.currentPos = Vec3.atCenterOf(hub).add(0, 0.5, 0);
        }
    }

    public static void loadFleet(ServerLevel level) {
        java.io.File file = getFleetFile();
        if (!file.exists()) return;
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            FleetContainer data = GSON.fromJson(reader, FleetContainer.class);
            if (data != null && data.agents != null) {
                FLEET.clear();
                for (AgentData ad : data.agents) {
                    VirtualAgent va = new VirtualAgent(UUID.fromString(ad.uuid), new BlockPos(ad.hubX, ad.hubY, ad.hubZ), ad.networkId, ad.homeSlot);
                    va.sourcePos = new BlockPos(ad.srcX, ad.srcY, ad.srcZ);
                    va.targetPos = new BlockPos(ad.dstX, ad.dstY, ad.dstZ);
                    va.currentPos = new Vec3(ad.curX, ad.curY, ad.curZ);
                    va.state = State.valueOf(ad.state);
                    va.taskIndex = ad.taskIndex;
                    va.carriedItem = itemFromBase64(ad.itemBase64, level);
                    FLEET.put(va.uuid, va);
                }
                LOGGER.info("RED-OS: Logistics Fleet restored ({} agents)", FLEET.size());
            }
        } catch (Exception e) { LOGGER.error("RED-OS: Failed to load fleet memory", e); }
    }

    public static void saveFleet(ServerLevel level, boolean async) {
        if (level == null) return;
        List<AgentData> agents = new ArrayList<>();
        for (VirtualAgent va : FLEET.values()) {
            AgentData ad = new AgentData();
            ad.uuid = va.uuid.toString(); ad.networkId = va.networkId; ad.homeSlot = va.homeSlot;
            ad.hubX = va.hubPos.getX(); ad.hubY = va.hubPos.getY(); ad.hubZ = va.hubPos.getZ();
            if (va.sourcePos != null) { ad.srcX = va.sourcePos.getX(); ad.srcY = va.sourcePos.getY(); ad.srcZ = va.sourcePos.getZ(); }
            if (va.targetPos != null) { ad.dstX = va.targetPos.getX(); ad.dstY = va.targetPos.getY(); ad.dstZ = va.targetPos.getZ(); }
            ad.curX = va.currentPos.x; ad.curY = va.currentPos.y; ad.curZ = va.currentPos.z;
            ad.state = va.state.name(); ad.taskIndex = va.taskIndex;
            ad.itemBase64 = itemToBase64(va.carriedItem, level);
            agents.add(ad);
        }
        FleetContainer container = new FleetContainer(); container.agents = agents;
        Runnable task = () -> {
            try (java.io.FileWriter writer = new java.io.FileWriter(getFleetFile())) {
                GSON.toJson(container, writer);
            } catch (Exception e) { LOGGER.error("RED-OS: Failed to save fleet memory", e); }
        };
        if (async) CompletableFuture.runAsync(task); else task.run();
    }

    private static java.io.File getFleetFile() {
        java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("redos");
        return new java.io.File(configDir.toFile(), "logistics_fleet.json");
    }

    private static String itemToBase64(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return "";
        try {
            var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            net.minecraft.nbt.Tag nbt = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            NbtIo.writeCompressed((CompoundTag)nbt, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return ""; }
    }

    private static ItemStack itemFromBase64(String base64, ServerLevel level) {
        if (base64 == null || base64.isEmpty()) return ItemStack.EMPTY;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, nbt).getOrThrow();
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private static class FleetContainer { public List<AgentData> agents; }
    private static class AgentData { public String uuid, networkId, state, itemBase64; public int hubX, hubY, hubZ, srcX, srcY, srcZ, dstX, dstY, dstZ; public double curX, curY, curZ; public int homeSlot, taskIndex; }

    public static void tick(ServerLevel level) {
        if (!initialScanDone) { loadFleet(level); initialScanDone = true; }
        if (System.currentTimeMillis() - lastSaveTime > SAVE_DELAY_MS) { saveFleet(level, true); lastSaveTime = System.currentTimeMillis(); }
        for (VirtualAgent agent : FLEET.values()) {
            boolean playerNearby = level.players().stream().anyMatch(p -> p.position().distanceToSqr(agent.currentPos) < 4096);
            if (playerNearby) processTacticalMode(level, agent);
            else processGhostMode(level, agent);
        }
    }

    private static void processTacticalMode(ServerLevel level, VirtualAgent agent) {
        ensurePuppetExists(level, agent);
        DroneEntity p = getPuppet(level, agent);
        if (p == null) return;
        agent.currentPos = p.position();
        p.setCarriedItem(agent.carriedItem.copy()); // Cargo Hard-Sync Fix!

        if (!agent.handshakeReceived && agent.state != State.STEP1_INITIALIZING) return;

        switch (agent.state) {
            case STEP1_INITIALIZING -> { 
                agent.state = State.STEP2_GOING_TO_SOURCE; 
                p.setTacticalObjective(agent.sourcePos);
                agent.handshakeReceived = true;
            }
            case STEP2_GOING_TO_SOURCE -> {
                agent.carriedItem = callTagTransaction(level, agent.sourcePos, true, ItemStack.EMPTY);
                p.setCarriedItem(agent.carriedItem.copy());
                agent.state = State.STEP3_GOING_TO_TARGET;
                List<BlockPos> path = TechNetwork.findMeshPath(level, agent.sourcePos, agent.targetPos, agent.networkId);
                if (path.isEmpty() && !agent.sourcePos.equals(agent.targetPos)) { handleAsylum(level, agent, agent.sourcePos); return; }
                agent.waypoints = new ArrayList<>(path);
                p.setTacticalObjective(agent.targetPos);
                agent.handshakeReceived = false; 
            }
            case STEP3_GOING_TO_TARGET -> {
                ItemStack left = callTagTransaction(level, agent.targetPos, false, agent.carriedItem);
                agent.carriedItem = left;
                p.setCarriedItem(agent.carriedItem.copy());
                if (agent.carriedItem.isEmpty()) {
                    TechNetwork.NetworkNode hubNode = TechNetwork.getNodeAt(agent.hubPos);
                    if (hubNode != null && hubNode.settings.get("task_list") instanceof List<?> tasks) {
                        int nextTaskIdx = findBestTaskIndex(level, agent.hubPos, tasks, agent.uuid);
                        if (nextTaskIdx != -1) {
                            java.util.Map<?, ?> tMap = (java.util.Map<?, ?>) tasks.get(nextTaskIdx);
                            BlockPos currentChest = agent.targetPos;
                            agent.sourcePos = parsePos(tMap.get("source"));
                            agent.targetPos = parsePos(tMap.get("target"));
                            agent.taskIndex = nextTaskIdx;
                            agent.state = State.STEP2_GOING_TO_SOURCE;
                            List<BlockPos> path = TechNetwork.findMeshPath(level, currentChest, agent.sourcePos, agent.networkId);
                            if (path.isEmpty() && !currentChest.equals(agent.sourcePos)) { handleAsylum(level, agent, currentChest); return; }
                            agent.waypoints = new ArrayList<>(path);
                            p.setTacticalObjective(agent.sourcePos);
                        } else {
                            agent.state = State.STEP4_RETURNING_HOME;
                            List<BlockPos> returnPath = TechNetwork.findMeshPath(level, agent.targetPos, agent.hubPos, agent.networkId);
                            if (returnPath.isEmpty() && !agent.targetPos.equals(agent.hubPos)) { handleAsylum(level, agent, agent.targetPos); return; }
                            agent.waypoints = new ArrayList<>(returnPath);
                            p.setTacticalObjective(agent.hubPos);
                        }
                    } else { agent.state = State.STEP4_RETURNING_HOME; p.setTacticalObjective(agent.hubPos); }
                } else { agent.state = State.STEP4_RETURNING_HOME; p.setTacticalObjective(agent.hubPos); }
                agent.handshakeReceived = false; 
            }
            case STEP4_RETURNING_HOME -> {
                if (level.getBlockEntity(agent.hubPos) instanceof DroneStationBlockEntity hub) {
                    if (!agent.carriedItem.isEmpty()) agent.carriedItem = hub.getInventory().addItem(agent.carriedItem);
                    hub.onDroneReturn(agent.uuid, agent.homeSlot);
                }
                agent.state = State.STEP5_TERMINATING; agent.handshakeReceived = false; 
            }
            case STEP5_TERMINATING -> {}
        }
    }

    private static void processGhostMode(ServerLevel level, VirtualAgent agent) {
        removePuppet(level, agent);
        if (agent.ghostTicksRemaining > 0) { 
            agent.ghostTicksRemaining--; 
            BlockPos targetGoal = !agent.waypoints.isEmpty() ? agent.waypoints.get(0) : 
                                 (agent.state == State.STEP2_GOING_TO_SOURCE ? agent.sourcePos : 
                                  agent.state == State.STEP3_GOING_TO_TARGET ? agent.targetPos : agent.hubPos);
            if (targetGoal != null) {
                Vec3 targetVec = Vec3.atCenterOf(targetGoal).add(0, 1.9, 0);
                Vec3 dir = targetVec.subtract(agent.currentPos).normalize();
                agent.currentPos = agent.currentPos.add(dir.scale(0.55));
            }
            return; 
        }
        if (!agent.waypoints.isEmpty()) {
            BlockPos reached = agent.waypoints.remove(0);
            agent.currentPos = Vec3.atCenterOf(reached).add(0, 1.9, 0);
            BlockPos nextGoal = agent.waypoints.isEmpty() ? (agent.state == State.STEP2_GOING_TO_SOURCE ? agent.sourcePos : agent.state == State.STEP3_GOING_TO_TARGET ? agent.targetPos : agent.hubPos) : agent.waypoints.get(0);
            agent.ghostTicksRemaining = calculateTravelTime(agent.currentPos, nextGoal);
            return;
        }
        switch (agent.state) {
            case STEP1_INITIALIZING -> { 
                agent.state = State.STEP2_GOING_TO_SOURCE; 
                agent.waypoints = new ArrayList<>(TechNetwork.findMeshPath(level, agent.hubPos, agent.sourcePos, agent.networkId));
                BlockPos nextGoal = agent.waypoints.isEmpty() ? agent.sourcePos : agent.waypoints.get(0);
                agent.ghostTicksRemaining = calculateTravelTime(agent.currentPos, nextGoal);
            }
            case STEP2_GOING_TO_SOURCE -> {
                agent.carriedItem = callTagTransaction(level, agent.sourcePos, true, ItemStack.EMPTY);
                if (!agent.carriedItem.isEmpty()) {
                    agent.currentPos = Vec3.atCenterOf(agent.sourcePos).add(0, 1.9, 0);
                    agent.state = State.STEP3_GOING_TO_TARGET;
                    agent.waypoints = new ArrayList<>(TechNetwork.findMeshPath(level, agent.sourcePos, agent.targetPos, agent.networkId));
                    if (agent.waypoints.isEmpty() && !agent.sourcePos.equals(agent.targetPos)) { handleAsylum(level, agent, agent.sourcePos); return; }
                    BlockPos nextGoal = agent.waypoints.isEmpty() ? agent.targetPos : agent.waypoints.get(0);
                    agent.ghostTicksRemaining = calculateTravelTime(agent.currentPos, nextGoal);
                } else agent.ghostTicksRemaining = 100;
            }
            case STEP3_GOING_TO_TARGET -> {
                ItemStack left = callTagTransaction(level, agent.targetPos, false, agent.carriedItem);
                agent.carriedItem = left;
                if (agent.carriedItem.isEmpty()) {
                    TechNetwork.NetworkNode hubNode = TechNetwork.getNodeAt(agent.hubPos);
                    if (hubNode != null && hubNode.settings.get("task_list") instanceof List<?> tasks) {
                        int nextTaskIdx = findBestTaskIndex(level, agent.hubPos, tasks, agent.uuid);
                        if (nextTaskIdx != -1) {
                            java.util.Map<?, ?> tMap = (java.util.Map<?, ?>) tasks.get(nextTaskIdx);
                            BlockPos lastT = agent.targetPos;
                            agent.sourcePos = parsePos(tMap.get("source"));
                            agent.targetPos = parsePos(tMap.get("target"));
                            agent.taskIndex = nextTaskIdx;
                            agent.currentPos = Vec3.atCenterOf(lastT).add(0, 1.9, 0);
                            agent.state = State.STEP2_GOING_TO_SOURCE;
                            agent.waypoints = new ArrayList<>(TechNetwork.findMeshPath(level, lastT, agent.sourcePos, agent.networkId));
                            if (agent.waypoints.isEmpty() && !lastT.equals(agent.sourcePos)) { handleAsylum(level, agent, lastT); return; }
                            BlockPos nextGoal = agent.waypoints.isEmpty() ? agent.sourcePos : agent.waypoints.get(0);
                            agent.ghostTicksRemaining = calculateTravelTime(agent.currentPos, nextGoal);
                            return;
                        }
                    }
                }
                agent.currentPos = Vec3.atCenterOf(agent.targetPos).add(0, 1.9, 0);
                agent.state = State.STEP4_RETURNING_HOME;
                agent.waypoints = new ArrayList<>(TechNetwork.findMeshPath(level, agent.targetPos, agent.hubPos, agent.networkId));
                if (agent.waypoints.isEmpty() && !agent.targetPos.equals(agent.hubPos)) { handleAsylum(level, agent, agent.targetPos); return; }
                BlockPos nextGoal = agent.waypoints.isEmpty() ? agent.hubPos : agent.waypoints.get(0);
                agent.ghostTicksRemaining = calculateTravelTime(agent.currentPos, nextGoal);
            }
            case STEP4_RETURNING_HOME -> {
                if (level.getBlockEntity(agent.hubPos) instanceof DroneStationBlockEntity hub) {
                    if (!agent.carriedItem.isEmpty()) hub.getInventory().addItem(agent.carriedItem);
                    hub.onDroneReturn(agent.uuid, agent.homeSlot);
                }
                FLEET.remove(agent.uuid);
            }
        }
    }

    private static ItemStack callTagTransaction(ServerLevel level, BlockPos pos, boolean pickup, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof IOTagBlockEntity tag) return tag.secureRemoteProcess(pickup, stack);
        return pickup ? ItemStack.EMPTY : stack;
    }

    private static int calculateTravelTime(Vec3 start, BlockPos end) { return (int) (start.distanceTo(Vec3.atCenterOf(end)) / 0.55) + 20; }

    private static void ensurePuppetExists(ServerLevel level, VirtualAgent agent) {
        if (agent.puppetUuid != null && level.getEntity(agent.puppetUuid) != null) return;
        DroneEntity p = new DroneEntity(ModEntities.DRONE, level);
        p.setPos(agent.currentPos.x, agent.currentPos.y, agent.currentPos.z);
        p.assignAgent(agent.uuid);
        p.setTaskData(agent.sourcePos, agent.targetPos, agent.hubPos);
        p.setCarriedItem(agent.carriedItem.copy());
        if (level.addFreshEntity(p)) {
            agent.puppetUuid = p.getUUID();
            BlockPos goal = switch (agent.state) { case STEP2_GOING_TO_SOURCE -> agent.sourcePos; case STEP3_GOING_TO_TARGET -> agent.targetPos; case STEP4_RETURNING_HOME -> agent.hubPos; default -> null; };
            if (goal != null) p.setTacticalObjective(goal);
            agent.handshakeReceived = false; 
        }
    }

    private static void removePuppet(ServerLevel level, VirtualAgent agent) {
        if (agent.puppetUuid != null) { 
            Entity e = level.getEntity(agent.puppetUuid); 
            if (e instanceof DroneEntity p) {
                agent.currentPos = p.position();
                agent.carriedItem = p.getCarriedItem().copy(); 
                BlockPos nextGoal = !agent.waypoints.isEmpty() ? agent.waypoints.get(0) : (agent.state == State.STEP2_GOING_TO_SOURCE ? agent.sourcePos : agent.state == State.STEP3_GOING_TO_TARGET ? agent.targetPos : agent.hubPos);
                if (nextGoal != null) agent.ghostTicksRemaining = calculateTravelTime(agent.currentPos, nextGoal);
            }
            if (e != null) e.discard(); 
            agent.puppetUuid = null; 
        }
    }

    private static DroneEntity getPuppet(ServerLevel level, VirtualAgent agent) { if (agent.puppetUuid == null) return null; Entity e = level.getEntity(agent.puppetUuid); return (e instanceof DroneEntity p) ? p : null; }
    public static void receiveHandshake(UUID id, Vec3 pos, ItemStack items) { VirtualAgent a = FLEET.get(id); if (a != null) { a.currentPos = pos; a.handshakeReceived = true; } }
    public static void syncPuppetData(UUID id, Vec3 pos, ItemStack items) { VirtualAgent a = FLEET.get(id); if (a != null) { a.currentPos = pos; a.carriedItem = items.copy(); } }
    public static void notifyPuppetDead(UUID id) { FLEET.remove(id); }

    private static void processPlanningForHub(ServerLevel level, BlockPos pos) {
        TechNetwork.NetworkNode node = TechNetwork.getNodeAt(pos);
        if (node != null && node.type == TechNetwork.NodeType.DRONE_STATION) {
            int dronesInHub = ((Number) node.settings.getOrDefault("drone_count", 0)).intValue();
            long activeDrones = FLEET.values().stream().filter(a -> a.hubPos.equals(pos)).count();
            if (activeDrones >= dronesInHub) return;
            Object tasksObj = node.settings.get("task_list");
            if (tasksObj instanceof List<?> tasks) {
                int bestTaskIdx = findBestTaskIndex(level, pos, tasks, null);
                if (bestTaskIdx != -1) {
                    java.util.Map<?, ?> tMap = (java.util.Map<?, ?>) tasks.get(bestTaskIdx);
                    BlockPos src = parsePos(tMap.get("source")), dst = parsePos(tMap.get("target"));
                    int availableSlot = -1;
                    for (int s = 0; s < 3; s++) { int slot = s; if (FLEET.values().stream().noneMatch(a -> a.hubPos.equals(pos) && a.homeSlot == slot)) { availableSlot = s; break; } }
                    if (availableSlot == -1) return;
                    UUID id = UUID.randomUUID();
                    VirtualAgent va = new VirtualAgent(id, pos, node.networkId, availableSlot);
                    va.sourcePos = src; va.targetPos = dst; va.taskIndex = bestTaskIdx;
                    List<BlockPos> path = TechNetwork.findMeshPath(level, pos, src, node.networkId);
                    if (path.isEmpty() && !pos.equals(src)) return;
                    va.waypoints = new ArrayList<>(path);
                    FLEET.put(id, va);
                }
            }
        }
    }

    private static int findBestTaskIndex(ServerLevel level, BlockPos hubPos, List<?> tasks, UUID requestingDrone) {
        int bestIdx = -1; int lowestPrio = Integer.MAX_VALUE;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) instanceof java.util.Map<?, ?> tMap && (Boolean) tMap.get("enabled")) {
                int prio = 2; Object pObj = tMap.get("priority"); if (pObj instanceof Number n) prio = n.intValue();
                BlockPos src = parsePos(tMap.get("source")), dst = parsePos(tMap.get("target"));
                if (src != null && dst != null && canPerformTask(level, src, dst)) {
                    int finalI = i; if (FLEET.values().stream().noneMatch(a -> a.hubPos.equals(hubPos) && a.taskIndex == finalI && !a.uuid.equals(requestingDrone))) { if (prio < lowestPrio) { lowestPrio = prio; bestIdx = i; } }
                }
            }
        }
        return bestIdx;
    }

    private static boolean canPerformTask(ServerLevel level, BlockPos src, BlockPos dst) {
        TechNetwork.NetworkNode srcNode = TechNetwork.getNodeAt(src); TechNetwork.NetworkNode dstNode = TechNetwork.getNodeAt(dst);
        if (srcNode == null || dstNode == null) return false;
        int itemCount = ((Number) srcNode.settings.getOrDefault("item_count", 0)).intValue();
        int freeSpace = ((Number) dstNode.settings.getOrDefault("free_space", 0)).intValue();
        return itemCount > 0 && freeSpace > 0;
    }

    private static BlockPos parsePos(Object obj) {
        if (obj instanceof BlockPos bp) return bp;
        if (obj instanceof java.util.Map<?, ?> map) { try { int x = ((Number) map.get("x")).intValue(), y = ((Number) map.get("y")).intValue(), z = ((Number) map.get("z")).intValue(); return new BlockPos(x, y, z); } catch (Exception e) { return null; } }
        return null;
    }

    private static void handleAsylum(ServerLevel level, VirtualAgent agent, BlockPos asylumPos) {
        if (level.getBlockEntity(agent.hubPos) instanceof DroneStationBlockEntity hub) { hub.getInventory().removeItem(agent.homeSlot, 1); }
        ItemStack droneItem = new ItemStack(ModItems.DRONE_UNIT);
        callTagTransaction(level, asylumPos, false, droneItem);
        if (!agent.carriedItem.isEmpty()) callTagTransaction(level, asylumPos, false, agent.carriedItem);
        removePuppet(level, agent); FLEET.remove(agent.uuid);
    }

    public static void forceHubReset(ServerLevel level, BlockPos hubPos) {
        String netId = TechNetwork.getNetIdFromRegistry(level, hubPos);
        long ghostRemoved = FLEET.values().stream().filter(a -> a.hubPos.equals(hubPos)).count();
        FLEET.values().removeIf(a -> a.hubPos.equals(hubPos));
        int entitiesRemoved = 0;
        for (Entity e : level.getAllEntities()) { if (e instanceof DroneEntity drone) { if (drone.getHubPos().map(p -> p.equals(hubPos)).orElse(false)) { e.discard(); entitiesRemoved++; } } }
        TechNetwork.broadcastToNetwork(level, netId, "§6[SYSTEM] §7Hub Reset: " + ghostRemoved + " agents & " + entitiesRemoved + " entities cleared.");
    }

    public static void onHubUpdated(ServerLevel level, BlockPos hubPos) { processPlanningForHub(level, hubPos); }
    public static void onNetworkUpdate(ServerLevel level, BlockPos tagPos) {
        String netId = TechNetwork.getNetIdFromRegistry(level, tagPos);
        if (netId != null && !netId.isEmpty()) { for (TechNetwork.NetworkNode node : TechNetwork.SERVER_REGISTRY.values()) { if (node.type == TechNetwork.NodeType.DRONE_STATION && node.networkId.equals(netId)) processPlanningForHub(level, node.pos); } }
    }
    public static BlockPos getNextWaypoint(UUID agentId) { VirtualAgent a = FLEET.get(agentId); if (a != null && !a.waypoints.isEmpty()) return a.waypoints.get(0); return null; }
    public static BlockPos getLookAheadWaypoint(UUID agentId) { VirtualAgent a = FLEET.get(agentId); if (a != null && a.waypoints.size() > 1) return a.waypoints.get(1); return null; }
    public static void reachWaypoint(UUID agentId) { VirtualAgent a = FLEET.get(agentId); if (a != null && !a.waypoints.isEmpty()) a.waypoints.remove(0); }
    public static java.util.Collection<VirtualAgent> getFleet() { return FLEET.values(); }
}