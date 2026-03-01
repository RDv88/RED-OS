package net.rdv88.redos.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.stream.Collectors;

public class LogisticsEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-logistics");
    private static final ConcurrentHashMap<UUID, VirtualAgent> FLEET = new ConcurrentHashMap<>();
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_DELAY_MS = 60000;
    private static long lastSaveTime = 0;
    private static boolean initialScanDone = false;

    public enum State { STEP1_INITIALIZING, STEP2_GOING_TO_SOURCE, STEP3_GOING_TO_TARGET, STEP4_RETURNING_HOME, STEP5_TERMINATING, STEP_RETURNING_ITEMS_TO_SOURCE }

    public static void loadFleet(MinecraftServer server) {
        if (server == null) return;
        java.io.File file = getFleetFile();
        if (!file.exists()) return;
        try (java.io.FileReader reader = new java.io.FileReader(file)) {
            FleetContainer data = GSON.fromJson(reader, FleetContainer.class);
            if (data != null && data.agents != null) {
                FLEET.clear();
                HolderLookup.Provider registries = server.registryAccess();
                for (AgentData ad : data.agents) {
                    VirtualAgent va = new VirtualAgent(UUID.fromString(ad.uuid), new BlockPos(ad.hubX, ad.hubY, ad.hubZ), ad.networkId, ad.homeSlot);
                    if (ad.srcX != 0 || ad.srcY != 0) va.sourcePos = new BlockPos(ad.srcX, ad.srcY, ad.srcZ);
                    if (ad.dstX != 0 || ad.dstY != 0) va.targetPos = new BlockPos(ad.dstX, ad.dstY, ad.dstZ);
                    va.currentPos = new Vec3(ad.curX, ad.curY, ad.curZ);
                    va.state = State.valueOf(ad.state);
                    va.taskIndex = ad.taskIndex;
                    va.carriedItem = itemFromBase64(ad.itemBase64, registries);
                    if (ad.waypoints != null) va.waypoints = ad.waypoints.stream().map(BlockPos::of).collect(Collectors.toCollection(ArrayList::new));
                    FLEET.put(va.uuid, va);
                }
                LOGGER.info("RED-OS: Logistics Fleet restored ({} agents)", FLEET.size());
            }
        } catch (Exception e) { LOGGER.error("RED-OS: Failed to load fleet memory", e); }
    }

    public static void saveFleet(MinecraftServer server, boolean async) {
        if (server == null) return;
        List<AgentData> agents = new ArrayList<>();
        HolderLookup.Provider registries = server.registryAccess();
        for (VirtualAgent va : FLEET.values()) {
            AgentData ad = new AgentData();
            ad.uuid = va.uuid.toString(); ad.networkId = va.networkId; ad.homeSlot = va.homeSlot;
            ad.hubX = va.hubPos.getX(); ad.hubY = va.hubPos.getY(); ad.hubZ = va.hubPos.getZ();
            if (va.sourcePos != null) { ad.srcX = va.sourcePos.getX(); ad.srcY = va.sourcePos.getY(); ad.srcZ = va.sourcePos.getZ(); }
            if (va.targetPos != null) { ad.dstX = va.targetPos.getX(); ad.dstY = va.targetPos.getY(); ad.dstZ = va.targetPos.getZ(); }
            ad.curX = va.currentPos.x; ad.curY = va.currentPos.y; ad.curZ = va.currentPos.z;
            ad.state = va.state.name(); ad.taskIndex = va.taskIndex;
            ad.itemBase64 = itemToBase64(va.carriedItem, registries);
            if (va.waypoints != null) ad.waypoints = va.waypoints.stream().map(BlockPos::asLong).collect(Collectors.toList());
            agents.add(ad);
        }
        FleetContainer container = new FleetContainer(); container.agents = agents;
        Runnable task = () -> {
            try (java.io.FileWriter writer = new java.io.FileWriter(getFleetFile())) { GSON.toJson(container, writer); }
            catch (Exception e) { LOGGER.error("RED-OS: Failed to save fleet memory", e); }
        };
        if (async) CompletableFuture.runAsync(task); else task.run();
    }

    private static java.io.File getFleetFile() {
        java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("redos");
        if (!configDir.toFile().exists()) configDir.toFile().mkdirs();
        return new java.io.File(configDir.toFile(), "logistics_fleet.json");
    }

    private static String itemToBase64(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return "";
        try {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            Tag nbt = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            NbtIo.writeCompressed((CompoundTag)nbt, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) { return ""; }
    }

    private static ItemStack itemFromBase64(String base64, HolderLookup.Provider registries) {
        if (base64 == null || base64.isEmpty()) return ItemStack.EMPTY;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, nbt).getOrThrow();
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private static class FleetContainer { public List<AgentData> agents; }
    private static class AgentData { 
        public String uuid, networkId, state, itemBase64; 
        public int hubX, hubY, hubZ, srcX, srcY, srcZ, dstX, dstY, dstZ; 
        public double curX, curY, curZ; 
        public int homeSlot, taskIndex;
        public List<Long> waypoints;
    }

    public static void tick(ServerLevel level) {
        if (!initialScanDone) {
            TechNetwork.SERVER_REGISTRY.values().stream().filter(n -> n.type == TechNetwork.NodeType.DRONE_STATION).forEach(n -> processPlanningForHub(level, n.pos));
            initialScanDone = true;
        }
        if (System.currentTimeMillis() - lastSaveTime > SAVE_DELAY_MS) { saveFleet(level.getServer(), true); lastSaveTime = System.currentTimeMillis(); }
        for (VirtualAgent agent : FLEET.values()) {
            boolean playerNearby = level.players().stream().anyMatch(p -> p.position().distanceToSqr(agent.currentPos) < 6400);
            if (playerNearby) processTacticalMode(level, agent); else processGhostMode(level, agent);
        }
    }

    private static void processTacticalMode(ServerLevel level, VirtualAgent agent) {
        ensurePuppetExists(level, agent);
        DroneEntity p = getPuppet(level, agent);
        if (p == null) return;
        agent.currentPos = p.position();
        if (!ItemStack.matches(p.getCarriedItem(), agent.carriedItem)) p.setCarriedItem(agent.carriedItem.copy());
        if (agent.handshakeReceived || agent.state == State.STEP1_INITIALIZING) handleCoreLogic(level, agent, true);
    }

    private static void processGhostMode(ServerLevel level, VirtualAgent agent) {
        if (agent.isPhysicalActive) removePuppet(level, agent);
        if (agent.ghostTicksRemaining > 0) { 
            agent.ghostTicksRemaining--; 
            BlockPos goal = getGoal(agent);
            if (goal != null) {
                Vec3 target = Vec3.atCenterOf(goal).add(0, 1.9, 0);
                double d = agent.currentPos.distanceTo(target);
                if (d > 0.1) agent.currentPos = agent.currentPos.add(target.subtract(agent.currentPos).normalize().scale(Math.min(0.55, d)));
            }
            return; 
        }
        handleCoreLogic(level, agent, false);
    }

    private static void handleCoreLogic(ServerLevel level, VirtualAgent agent, boolean isTactical) {
        DroneEntity p = isTactical ? getPuppet(level, agent) : null;
        if (agent.waypoints.isEmpty()) {
            BlockPos goal = getGoal(agent);
            if (goal != null && BlockPos.containing(agent.currentPos).distSqr(goal) > 4) {
                calculatePath(level, agent, BlockPos.containing(agent.currentPos), goal);
                if (p != null) p.setWaypoints(new ArrayList<>(agent.waypoints));
            }
        }
        if (!agent.waypoints.isEmpty()) {
            BlockPos reached = agent.waypoints.remove(0);
            agent.currentPos = Vec3.atCenterOf(reached).add(0, 1.9, 0);
            syncObjectives(agent, p);
            agent.handshakeReceived = false;
            return;
        }
        switch (agent.state) {
            case STEP1_INITIALIZING -> { 
                agent.state = State.STEP2_GOING_TO_SOURCE; agent.lastStatusMessage = "§eEngaging collection protocols...";
                calculatePath(level, agent, agent.hubPos, agent.sourcePos); syncObjectives(agent, p);
            }
            case STEP2_GOING_TO_SOURCE -> {
                agent.carriedItem = callTagTransaction(level, agent.sourcePos, true, ItemStack.EMPTY);
                if (!agent.carriedItem.isEmpty()) {
                    agent.state = State.STEP3_GOING_TO_TARGET; agent.lastStatusMessage = "§aItems secured. En route to Target.";
                    calculatePath(level, agent, agent.sourcePos, agent.targetPos);
                } else if (!tryShuttle(level, agent, agent.sourcePos, p)) {
                    agent.state = State.STEP4_RETURNING_HOME; agent.lastStatusMessage = "§eSource empty. Returning to Hub.";
                    calculatePath(level, agent, agent.sourcePos, agent.hubPos);
                }
                syncObjectives(agent, p); agent.handshakeReceived = false;
            }
            case STEP3_GOING_TO_TARGET -> {
                ItemStack before = agent.carriedItem.copy();
                ItemStack left = callTagTransaction(level, agent.targetPos, false, agent.carriedItem);
                agent.carriedItem = left;
                if (!agent.carriedItem.isEmpty()) {
                    if (ItemStack.matches(before, left) && level.getBlockEntity(agent.hubPos) instanceof DroneStationBlockEntity hub) hub.markTaskFull(agent.taskIndex, true);
                    agent.state = State.STEP_RETURNING_ITEMS_TO_SOURCE; agent.lastStatusMessage = "§eTarget Full! Returning to Source.";
                    calculatePath(level, agent, agent.targetPos, agent.sourcePos);
                } else if (!tryShuttle(level, agent, agent.targetPos, p)) {
                    agent.state = State.STEP4_RETURNING_HOME; agent.lastStatusMessage = "§7Task complete. Returning to Hub.";
                    calculatePath(level, agent, agent.targetPos, agent.hubPos);
                }
                syncObjectives(agent, p); agent.handshakeReceived = false;
            }
            case STEP_RETURNING_ITEMS_TO_SOURCE -> {
                agent.carriedItem = callTagTransaction(level, agent.sourcePos, false, agent.carriedItem);
                if (!agent.carriedItem.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, agent.sourcePos.getX()+0.5, agent.sourcePos.getY()+1.0, agent.sourcePos.getZ()+0.5, agent.carriedItem.copy()));
                    agent.carriedItem = ItemStack.EMPTY;
                }
                agent.state = State.STEP4_RETURNING_HOME; agent.lastStatusMessage = "§7Returning to Hub.";
                calculatePath(level, agent, agent.sourcePos, agent.hubPos);
                syncObjectives(agent, p); agent.handshakeReceived = false;
            }
            case STEP4_RETURNING_HOME -> {
                if (level.getBlockEntity(agent.hubPos) instanceof DroneStationBlockEntity hub) {
                    if (!agent.carriedItem.isEmpty()) agent.carriedItem = hub.getInventory().addItem(agent.carriedItem);
                    hub.onDroneReturn(agent.uuid, agent.homeSlot);
                }
                FLEET.remove(agent.uuid);
            }
        }
    }

    private static void calculatePath(ServerLevel level, VirtualAgent agent, BlockPos start, BlockPos end) {
        List<BlockPos> path = TechNetwork.findMeshPath(level, start, end, agent.networkId);
        if (path.isEmpty() && !start.equals(end)) { agent.lastStatusMessage = "§cPath blocked! Seeking Asylum."; handleAsylum(level, agent, start); }
        else agent.waypoints = new ArrayList<>(path);
    }

    private static boolean tryShuttle(ServerLevel level, VirtualAgent agent, BlockPos current, DroneEntity p) {
        TechNetwork.NetworkNode hubNode = TechNetwork.getNodeAt(agent.hubPos);
        if (hubNode != null && hubNode.settings.get("task_list") instanceof List<?> tasks) {
            int nextTaskIdx = findBestTaskIndex(level, agent.hubPos, tasks, agent.uuid);
            if (nextTaskIdx != -1) {
                java.util.Map<?, ?> tMap = (java.util.Map<?, ?>) tasks.get(nextTaskIdx);
                agent.sourcePos = parsePos(tMap.get("source")); agent.targetPos = parsePos(tMap.get("target")); agent.taskIndex = nextTaskIdx;
                agent.state = State.STEP2_GOING_TO_SOURCE; agent.lastStatusMessage = "§bShuttling to next assignment.";
                calculatePath(level, agent, current, agent.sourcePos); return true;
            }
        }
        return false;
    }

    private static void syncObjectives(VirtualAgent agent, DroneEntity p) {
        BlockPos next = agent.waypoints.isEmpty() ? getGoal(agent) : agent.waypoints.get(0);
        if (p != null) { p.setWaypoints(new ArrayList<>(agent.waypoints)); if (next != null) p.setTacticalObjective(next); p.setCarriedItem(agent.carriedItem.copy()); }
        else if (next != null) agent.ghostTicksRemaining = (int) (agent.currentPos.distanceTo(Vec3.atCenterOf(next).add(0, 1.9, 0)) / 0.55) + 5;
    }

    private static BlockPos getGoal(VirtualAgent agent) {
        return switch (agent.state) {
            case STEP2_GOING_TO_SOURCE -> agent.sourcePos;
            case STEP3_GOING_TO_TARGET -> agent.targetPos;
            case STEP_RETURNING_ITEMS_TO_SOURCE -> agent.sourcePos;
            case STEP4_RETURNING_HOME -> agent.hubPos;
            default -> null;
        };
    }

    private static void ensurePuppetExists(ServerLevel level, VirtualAgent agent) {
        if (agent.isPhysicalActive && agent.puppetUuid != null && level.getEntity(agent.puppetUuid) != null) return;
        agent.isPhysicalActive = false; agent.puppetUuid = null;
        if (level.hasChunk(BlockPos.containing(agent.currentPos).getX() >> 4, BlockPos.containing(agent.currentPos).getZ() >> 4)) {
            DroneEntity p = new DroneEntity(ModEntities.DRONE, level);
            p.setPos(agent.currentPos.x, agent.currentPos.y, agent.currentPos.z);
            p.assignAgent(agent.uuid);
            p.setTaskData(agent.sourcePos, agent.targetPos, agent.hubPos);
            p.setCarriedItem(agent.carriedItem.copy());
            p.setWaypoints(new ArrayList<>(agent.waypoints));
            if (level.addFreshEntity(p)) { agent.puppetUuid = p.getUUID(); agent.isPhysicalActive = true; BlockPos goal = getGoal(agent); if (goal != null) p.setTacticalObjective(goal); agent.handshakeReceived = false; }
        }
    }

    private static void removePuppet(ServerLevel level, VirtualAgent agent) {
        if (agent.puppetUuid != null) { Entity e = level.getEntity(agent.puppetUuid); if (e != null) e.discard(); agent.puppetUuid = null; agent.isPhysicalActive = false; }
    }

    private static DroneEntity getPuppet(ServerLevel level, VirtualAgent agent) { if (agent.puppetUuid == null) return null; Entity e = level.getEntity(agent.puppetUuid); return (e instanceof DroneEntity p) ? p : null; }
    public static void receiveHandshake(UUID id, Vec3 pos, ItemStack items) { VirtualAgent a = FLEET.get(id); if (a != null) { a.currentPos = pos; a.handshakeReceived = true; } }
    public static void syncPuppetData(UUID id, Vec3 pos) { VirtualAgent a = FLEET.get(id); if (a != null) a.currentPos = pos; }
    public static void notifyPuppetDead(UUID id) { FLEET.remove(id); }
    public static boolean isAgentActive(UUID uuid) { return uuid != null && FLEET.containsKey(uuid); }
    public static boolean isOfficialPuppet(UUID agentId, UUID entityId) { VirtualAgent a = FLEET.get(agentId); return a != null && entityId.equals(a.puppetUuid); }
    public static void registerEvents() {} 
    public static BlockPos requestOrders(UUID id) { VirtualAgent a = FLEET.get(id); return a != null ? getGoal(a) : null; }

    private static void processPlanningForHub(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DroneStationBlockEntity hub) || !hub.canLaunch()) return;
        TechNetwork.NetworkNode node = TechNetwork.getNodeAt(pos);
        if (node == null) return;
        int dronesInHub = ((Number) node.settings.getOrDefault("drone_count", 0)).intValue();
        long activeDrones = FLEET.values().stream().filter(a -> a.hubPos.equals(pos)).count();
        if (activeDrones >= dronesInHub) return;
        if (node.settings.get("task_list") instanceof List<?> tasks) {
            int bestTaskIdx = findBestTaskIndex(level, pos, tasks, null);
            if (bestTaskIdx != -1) {
                java.util.Map<?, ?> tMap = (java.util.Map<?, ?>) tasks.get(bestTaskIdx);
                int availableSlot = -1;
                for (int s = 0; s < 3; s++) { int slot = s; if (FLEET.values().stream().noneMatch(a -> a.hubPos.equals(pos) && a.homeSlot == slot)) { availableSlot = s; break; } }
                if (availableSlot == -1) return;
                UUID id = UUID.randomUUID();
                VirtualAgent va = new VirtualAgent(id, pos, node.networkId, availableSlot);
                va.sourcePos = parsePos(tMap.get("source")); va.targetPos = parsePos(tMap.get("target")); va.taskIndex = bestTaskIdx;
                List<BlockPos> path = TechNetwork.findMeshPath(level, pos, va.sourcePos, node.networkId);
                if (path.isEmpty() && !pos.equals(va.sourcePos)) return;
                va.waypoints = new ArrayList<>(path);
                FLEET.put(id, va);
                hub.startLaunchCooldown();
            }
        }
    }

    private static int findBestTaskIndex(ServerLevel level, BlockPos hubPos, List<?> tasks, UUID requestingDrone) {
        int bestIdx = -1; int lowestPrioValue = Integer.MAX_VALUE;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i) instanceof java.util.Map<?, ?> tMap && (Boolean) tMap.get("enabled")) {
                if (Boolean.TRUE.equals(tMap.get("is_full"))) continue;
                int prio = 2; Object pObj = tMap.get("priority"); if (pObj instanceof Number n) prio = n.intValue();
                BlockPos src = parsePos(tMap.get("source")), dst = parsePos(tMap.get("target"));
                if (src != null && dst != null && canPerformTask(level, src, dst) && prio < lowestPrioValue) { lowestPrioValue = prio; bestIdx = i; }
            }
        }
        return bestIdx;
    }

    private static boolean canPerformTask(ServerLevel level, BlockPos src, BlockPos dst) {
        TechNetwork.NetworkNode srcNode = TechNetwork.getNodeAt(src); TechNetwork.NetworkNode dstNode = TechNetwork.getNodeAt(dst);
        if (srcNode == null || dstNode == null) return false;
        return ((Number) srcNode.settings.getOrDefault("item_count", 0)).intValue() > 0 && ((Number) dstNode.settings.getOrDefault("free_space", 0)).intValue() > 0;
    }

    private static BlockPos parsePos(Object obj) {
        if (obj instanceof BlockPos bp) return bp;
        if (obj instanceof java.util.Map<?, ?> map) { try { return new BlockPos(((Number) map.get("x")).intValue(), ((Number) map.get("y")).intValue(), ((Number) map.get("z")).intValue()); } catch (Exception e) { return null; } }
        return null;
    }

    public static void forceHubReset(ServerLevel level, BlockPos hubPos) {
        FLEET.values().removeIf(a -> a.hubPos.equals(hubPos));
        for (Entity e : level.getAllEntities()) { if (e instanceof DroneEntity drone && drone.getHubPos().map(p -> p.equals(hubPos)).orElse(false)) e.discard(); }
    }

    public static void onHubUpdated(ServerLevel level, BlockPos hubPos) { processPlanningForHub(level, hubPos); }
    public static void onNetworkUpdate(ServerLevel level, BlockPos tagPos) {
        String netId = TechNetwork.getNetIdFromRegistry(level, tagPos);
        if (netId != null && !netId.isEmpty()) {
            TechNetwork.SERVER_REGISTRY.values().stream().filter(n -> n.type == TechNetwork.NodeType.DRONE_STATION && n.networkId.equals(netId))
                .forEach(n -> { if (level.getBlockEntity(n.pos) instanceof DroneStationBlockEntity hub) hub.resetFullTasksForTarget(tagPos); processPlanningForHub(level, n.pos); });
        }
    }
    private static void handleAsylum(ServerLevel level, VirtualAgent agent, BlockPos asylumPos) {
        if (level.getBlockEntity(agent.hubPos) instanceof DroneStationBlockEntity hub) hub.getInventory().removeItem(agent.homeSlot, 1);
        ItemStack droneItem = new ItemStack(ModItems.DRONE_UNIT);
        ItemStack leftDrone = callTagTransaction(level, asylumPos, false, droneItem);
        ItemStack leftCargo = agent.carriedItem.isEmpty() ? ItemStack.EMPTY : callTagTransaction(level, asylumPos, false, agent.carriedItem);
        if (!leftDrone.isEmpty()) level.addFreshEntity(new ItemEntity(level, asylumPos.getX()+0.5, asylumPos.getY()+1.0, asylumPos.getZ()+0.5, leftDrone));
        if (!leftCargo.isEmpty()) level.addFreshEntity(new ItemEntity(level, asylumPos.getX()+0.5, asylumPos.getY()+1.0, asylumPos.getZ()+0.5, leftCargo));
        removePuppet(level, agent); FLEET.remove(agent.uuid);
    }
    private static ItemStack callTagTransaction(ServerLevel level, BlockPos pos, boolean pickup, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof IOTagBlockEntity tag) return tag.secureRemoteProcess(pickup, stack);
        return pickup ? ItemStack.EMPTY : stack;
    }
    public static java.util.Collection<VirtualAgent> getFleet() { return FLEET.values(); }
}