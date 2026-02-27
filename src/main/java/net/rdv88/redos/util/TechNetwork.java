package net.rdv88.redos.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.rdv88.redos.item.HandheldDeviceItem;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;
import net.rdv88.redos.network.payload.SyncNetworkNodesPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class TechNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_DATA_VERSION = 6; 
    
    public static final Map<BlockPos, NetworkNode> SERVER_REGISTRY = new HashMap<>();
    private static final Map<BlockPos, NetworkNode> CLIENT_REGISTRY = new HashMap<>();
    private static final Map<UUID, String> ACTIVE_HANDHELD_USERS = new HashMap<>();
    private static String CURRENT_WORLD_ID = "default";
    
    private static boolean dbDirty = false;
    private static long lastChangeTime = 0;
    private static final long SAVE_DELAY_MS = 150000; 
    private static final AtomicBoolean IS_SAVING = new AtomicBoolean(false);

    private static Map<BlockPos, NetworkNode> getRegistry(Level level) {
        return level.isClientSide() ? CLIENT_REGISTRY : SERVER_REGISTRY;
    }

    public static Map<BlockPos, NetworkNode> getNodes() {
        return CLIENT_REGISTRY;
    }

    public static class NetworkNode {
        public BlockPos pos; 
        public String networkId; 
        public String customName;
        public NodeType type; 
        public String dimension;
        public String worldId;
        public String serial;
        public Map<String, Integer> neighborObs = new HashMap<>();
        public Map<String, Object> settings = new HashMap<>();

        public NetworkNode(BlockPos pos, String networkId, String customName, NodeType type, String dimension, String worldId, String serial) {
            this.pos = pos.immutable(); 
            this.networkId = networkId; 
            this.customName = customName; 
            this.type = type; 
            this.dimension = dimension;
            this.worldId = worldId;
            this.serial = serial != null ? serial : "LEGACY";
            
            if (type == NodeType.SENSOR) {
                settings.put("detect_players", true);
                settings.put("detect_mobs", false);
                settings.put("detect_animals", false);
                settings.put("detect_villagers", false);
                settings.put("alerts_enabled", true);
                settings.put("range", 3);
                settings.put("hold_time", 30);
            }
            if (type == NodeType.IO_TAG) {
                settings.put("mode", "PICKUP"); // PICKUP or DROP_OFF
                settings.put("filter_item", "AIR");
            }
        }
    }

    private static class DatabaseContainer {
        public int version;
        public List<NetworkNode> nodes;
        public DatabaseContainer(int version, List<NetworkNode> nodes) {
            this.version = version;
            this.nodes = nodes;
        }
    }

    public enum NodeType { SHORT_RANGE, LONG_RANGE, SENSOR, TRIGGER, CAMERA, SERVER, PORTER, IO_TAG, DRONE_STATION }

    public record LogisticsTaskData(BlockPos source, BlockPos target, int priority, int index) {}

    public static @org.jetbrains.annotations.Nullable LogisticsTaskData getBestTaskFromRAM(BlockPos hubPos, String networkId) {
        NetworkNode hubNode = SERVER_REGISTRY.get(hubPos);
        if (hubNode == null || hubNode.type != NodeType.DRONE_STATION) return null;

        Object tasksObj = hubNode.settings.get("task_list");
        if (!(tasksObj instanceof List<?> tasks)) return null;

        for (int p = 1; p <= 5; p++) {
            for (int i = 0; i < tasks.size(); i++) {
                Object taskObj = tasks.get(i);
                if (taskObj instanceof Map<?, ?> rawMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> taskMap = (Map<String, Object>) rawMap;
                    boolean enabled = (boolean) taskMap.getOrDefault("enabled", true);
                    int priority = ((Number) taskMap.getOrDefault("priority", 3)).intValue();
                    
                    if (enabled && priority == p) {
                        BlockPos src = (BlockPos) taskMap.get("source");
                        BlockPos dst = (BlockPos) taskMap.get("target");
                        
                        if (hasItemsAtSourceRAM(src, networkId) && hasSpaceAtTargetRAM(dst, networkId)) {
                            return new LogisticsTaskData(src, dst, priority, i);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasItemsAtSourceRAM(BlockPos tagPos, String networkId) {
        NetworkNode node = SERVER_REGISTRY.get(tagPos);
        if (node != null && node.type == NodeType.IO_TAG && node.networkId.equals(networkId)) {
            Object count = node.settings.get("item_count");
            return count instanceof Number n && n.intValue() > 0;
        }
        return false;
    }

    private static boolean hasSpaceAtTargetRAM(BlockPos tagPos, String networkId) {
        NetworkNode node = SERVER_REGISTRY.get(tagPos);
        if (node != null && node.type == NodeType.IO_TAG && node.networkId.equals(networkId)) {
            Object space = node.settings.get("free_space");
            return space instanceof Number n && n.intValue() >= 64;
        }
        return true; // Fallback
    }

    private static File getDatabaseFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("redos");
        File dir = configDir.toFile();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "network_db.json");
    }

    public static void loadDatabase(String worldId) {
        CURRENT_WORLD_ID = worldId;
        File file = getDatabaseFile();
        if (!file.exists()) { SERVER_REGISTRY.clear(); return; }
        try (FileReader reader = new FileReader(file)) {
            DatabaseContainer container = GSON.fromJson(reader, DatabaseContainer.class);
            SERVER_REGISTRY.clear();
            if (container != null && container.nodes != null) {
                for (NetworkNode node : container.nodes) {
                    if (node.worldId.equals(worldId)) {
                        if (node.serial == null || node.serial.isEmpty()) node.serial = "LEGACY";
                        if (node.settings == null) node.settings = new HashMap<>();
                        
                        // Default settings for new types
                        if (node.type == NodeType.SENSOR && node.settings.isEmpty()) {
                            node.settings.put("detect_players", true);
                            node.settings.put("detect_mobs", false);
                            node.settings.put("detect_animals", false);
                            node.settings.put("detect_villagers", false);
                            node.settings.put("alerts_enabled", true);
                            node.settings.put("range", 3);
                            node.settings.put("hold_time", 30);
                        }
                        if (node.type == NodeType.IO_TAG && node.settings.isEmpty()) {
                            node.settings.put("mode", "PICKUP");
                            node.settings.put("filter_item", "AIR");
                        }
                        
                        SERVER_REGISTRY.put(node.pos, node);
                    }
                }
                // DATABASE SCRUB: Mark dirty if nodes were potentially legacy so we resave with new defaults
                dbDirty = true;
            }
            LOGGER.info("Technology Database loaded for RED-OS 💾 (Version {}, {} nodes)", 
                container != null ? container.version : 0, SERVER_REGISTRY.size());
        } catch (IOException e) {
            LOGGER.error("RED-OS: Failed to load network database", e);
        }
    }

    public static void saveDatabase(boolean async) {
        if (CURRENT_WORLD_ID.equals("default")) return;
        if (IS_SAVING.get()) return;
        List<NetworkNode> currentNodes = new ArrayList<>(SERVER_REGISTRY.values());
        String activeWorldId = CURRENT_WORLD_ID;
        Runnable saveTask = () -> {
            if (!IS_SAVING.compareAndSet(false, true)) return;
            try {
                File file = getDatabaseFile();
                List<NetworkNode> allNodes = new ArrayList<>();
                if (file.exists()) {
                    try (FileReader reader = new FileReader(file)) {
                        DatabaseContainer container = GSON.fromJson(reader, DatabaseContainer.class);
                        if (container != null && container.nodes != null) {
                            for (NetworkNode n : container.nodes) if (!n.worldId.equals(activeWorldId)) allNodes.add(n);
                        }
                    } catch (IOException ignored) {}
                }
                allNodes.addAll(currentNodes);
                try (FileWriter writer = new FileWriter(file)) {
                    DatabaseContainer container = new DatabaseContainer(CURRENT_DATA_VERSION, allNodes);
                    GSON.toJson(container, writer);
                    LOGGER.info("RED-OS: JSON Database synced ({} nodes, async={})", currentNodes.size(), async);
                } catch (IOException e) { LOGGER.error("RED-OS: Failed to save network database", e); }
            } finally { IS_SAVING.set(false); }
        };
        if (async) CompletableFuture.runAsync(saveTask); else saveTask.run();
    }

    public static void registerNode(Level level, BlockPos pos, String networkId, String name, NodeType type, String serial, Map<String, Object> initialSettings) {
        if (level == null || level.isClientSide()) return;
        String dim = level.dimension().identifier().toString();
        NetworkNode node = SERVER_REGISTRY.get(pos);
        boolean changed = false;
        if (node == null) {
            node = new NetworkNode(pos, networkId, name, type, dim, CURRENT_WORLD_ID, serial);
            if (initialSettings != null) node.settings.putAll(initialSettings);
            SERVER_REGISTRY.put(pos.immutable(), node);
            changed = true;
        } else {
            if (!node.networkId.equals(networkId)) { node.networkId = networkId; changed = true; }
            if (!node.customName.equals(name)) { node.customName = name; changed = true; }
            if (node.type != type) { node.type = type; changed = true; }
            if (serial != null && !serial.equals(node.serial)) {
                if (node.serial.equals("LEGACY")) LOGGER.info("RED-OS: Legacy node at {} upgraded to serial {}", pos, serial);
                node.serial = serial;
                changed = true;
            }
            if (initialSettings != null) {
                for (Map.Entry<String, Object> entry : initialSettings.entrySet()) {
                    if (!Objects.equals(node.settings.get(entry.getKey()), entry.getValue())) {
                        node.settings.put(entry.getKey(), entry.getValue());
                        changed = true;
                    }
                }
            }
        }
        if (changed) { dbDirty = true; lastChangeTime = System.currentTimeMillis(); syncToAll(level); }
    }

    public static void registerNode(Level level, BlockPos pos, String networkId, String name, NodeType type, String serial) {
        registerNode(level, pos, networkId, name, type, serial, null);
    }

    public static void registerNode(Level level, BlockPos pos, String networkId, String name, NodeType type) {
        registerNode(level, pos, networkId, name, type, "LEGACY", null);
    }

    public static void removeNode(Level level, BlockPos pos) {
        NetworkNode node = SERVER_REGISTRY.get(pos);
        if (node != null) {
            if (node.type == NodeType.IO_TAG && !level.isClientSide() && level instanceof ServerLevel serverLevel) {
                // Notify all Hubs on the same network that this tag is gone
                for (NetworkNode other : SERVER_REGISTRY.values()) {
                    if (other.type == NodeType.DRONE_STATION && other.networkId.equals(node.networkId)) {
                        // Hub connection cleanup is now handled by LogisticsEngine
                    }
                }
            }
            SERVER_REGISTRY.remove(pos);
            dbDirty = true;
            lastChangeTime = System.currentTimeMillis();
            if (level != null) syncToAll(level);
        }
    }

    public static void tickLiveUpdates(net.minecraft.server.MinecraftServer server) {
        long serverTicks = server.getTickCount();
        if (serverTicks % 10 == 0 && dbDirty && (System.currentTimeMillis() - lastChangeTime > SAVE_DELAY_MS)) { saveDatabase(true); dbDirty = false; }

        if (!SERVER_REGISTRY.isEmpty()) {
            int nodeIndex = (int)(serverTicks % SERVER_REGISTRY.size());
            List<NetworkNode> nodes = new ArrayList<>(SERVER_REGISTRY.values());
            NetworkNode node = nodes.get(nodeIndex);
            Level overworld = server.overworld();
            
            if (overworld.hasChunk(node.pos.getX() >> 4, node.pos.getZ() >> 4)) {
                BlockEntity be = overworld.getBlockEntity(node.pos);
                boolean isValid = false;
                if (be != null) {
                    if (node.type == NodeType.CAMERA && be instanceof net.rdv88.redos.block.entity.WirelessCameraBlockEntity) isValid = true;
                    else if (node.type == NodeType.SENSOR && be instanceof net.rdv88.redos.block.entity.SmartMotionSensorBlockEntity) isValid = true;
                    else if (node.type == NodeType.TRIGGER && be instanceof net.rdv88.redos.block.entity.RemoteRedstoneTriggerBlockEntity) isValid = true;
                    else if (node.type == NodeType.SHORT_RANGE && be instanceof net.rdv88.redos.block.entity.ShortRangeTransmitterBlockEntity) isValid = true;
                    else if (node.type == NodeType.LONG_RANGE && be instanceof net.rdv88.redos.block.entity.LongRangeTransmitterBlockEntity) isValid = true;
                    else if (node.type == NodeType.PORTER && be instanceof net.rdv88.redos.block.entity.QuantumPorterBlockEntity) isValid = true;
                    else if (node.type == NodeType.IO_TAG && be instanceof net.rdv88.redos.block.entity.IOTagBlockEntity) isValid = true;
                    else if (node.type == NodeType.DRONE_STATION && be instanceof net.rdv88.redos.block.entity.DroneStationBlockEntity) isValid = true;
                }

                if (!isValid) {
                    LOGGER.warn("RED-OS: Removing Ghost Node at {} (Type: {})", node.pos, node.type);
                    removeNode(overworld, node.pos);
                } else if (isValid) {
                    if (node.type == NodeType.SHORT_RANGE || node.type == NodeType.LONG_RANGE) {
                        for (NetworkNode neighbor : SERVER_REGISTRY.values()) { if (node == neighbor || !node.dimension.equals(neighbor.dimension)) continue; calculateSignal(overworld, node.pos, neighbor.pos, node.type, true); }
                    }
                }
            }
        }

        for (Map.Entry<UUID, String> entry : ACTIVE_HANDHELD_USERS.entrySet()) {
            UUID playerId = entry.getKey(); if ((serverTicks + (playerId.hashCode() & 0xFFFF)) % 10 != 0) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) { var data = calculateVisibleDevices(player, entry.getValue()); net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SyncHandheldDataPayload(data)); }
        }
    }

    public static void refreshNodeConnections(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        NetworkNode node = SERVER_REGISTRY.get(pos);
        if (node == null) return;

        // ONLY refresh if it's a transmitter (infrastructure)
        if (node.type != NodeType.SHORT_RANGE && node.type != NodeType.LONG_RANGE) return;

        // Force signal calculation with all neighbors in the same dimension
        String dim = level.dimension().identifier().toString();
        for (NetworkNode neighbor : SERVER_REGISTRY.values()) {
            if (node == neighbor || !node.dimension.equals(neighbor.dimension)) continue;
            // Using isInfrastructure=true ensures the result is saved to neighborObs RAM
            calculateSignal(level, node.pos, neighbor.pos, node.type, true);
        }
    }

    public static List<SyncHandheldDataPayload.DeviceEntry> calculateVisibleDevices(ServerPlayer player, String networkIds) {
        List<SyncHandheldDataPayload.DeviceEntry> visible = new ArrayList<>();
        Set<String> activeIds = new HashSet<>(Arrays.asList(networkIds.split(",")));
        Level level = player.level(); BlockPos playerPos = player.blockPosition(); String dim = level.dimension().identifier().toString();
        Map<BlockPos, NetworkNode> registry = getRegistry(level);
        NetworkNode gateway = null; double gatewaySignal = 0;
        for (NetworkNode node : registry.values()) { if (node.dimension.equals(dim) && activeIds.contains(node.networkId) && (node.type == NodeType.SHORT_RANGE || node.type == NodeType.LONG_RANGE)) { double sig = calculateSignal(level, node.pos, playerPos, node.type, false); if (sig > gatewaySignal) { gatewaySignal = sig; gateway = node; } } }
        for (NetworkNode node : registry.values()) {
            if (!node.dimension.equals(dim) || !activeIds.contains(node.networkId)) continue;
            double directSig = calculateSignal(level, node.pos, playerPos, NodeType.SHORT_RANGE, false);
            if (node.type == NodeType.SHORT_RANGE || node.type == NodeType.LONG_RANGE) directSig = Math.max(directSig, calculateSignal(level, node.pos, playerPos, node.type, false));
            int finalSignal = (int)directSig; String connectionMode = "Direct"; boolean connected = directSig > 0;
            if (gateway != null) {
                Map<BlockPos, NetworkNode> mesh = getConnectedMeshNodes(level, node.pos, node.networkId);
                if (mesh.containsKey(gateway.pos) || node.pos.equals(gateway.pos)) {
                    NetworkNode bestNeighbor = null; double bestMeshSig = 0;
                    for (NetworkNode neighbor : registry.values()) { if (neighbor == node || !neighbor.dimension.equals(dim) || !neighbor.networkId.equals(node.networkId)) continue; if (neighbor.type != NodeType.SHORT_RANGE && neighbor.type != NodeType.LONG_RANGE) continue; double sig = calculateSignal(level, neighbor.pos, node.pos, neighbor.type, true); if (sig > bestMeshSig) { bestMeshSig = sig; bestNeighbor = neighbor; } }
                    if (gatewaySignal > directSig) { finalSignal = (int)gatewaySignal; connectionMode = (bestNeighbor != null && !bestNeighbor.pos.equals(node.pos)) ? bestNeighbor.customName : "Direct"; connected = true; }
                }
            }
            if (connected) {
                boolean dp = (boolean)node.settings.getOrDefault("detect_players", true); 
                boolean dm = (boolean)node.settings.getOrDefault("detect_mobs", true); 
                boolean da = (boolean)node.settings.getOrDefault("detect_animals", false); 
                boolean dv = (boolean)node.settings.getOrDefault("detect_villagers", false);
                boolean alerts = (boolean)node.settings.getOrDefault("alerts_enabled", true);
                int range = ((Number)node.settings.getOrDefault("range", 10)).intValue();
                int hold = ((Number)node.settings.getOrDefault("hold_time", 20)).intValue();
                
                // NEW: Extract inventory data for IO Tags
                int itemCount = 0;
                int freeSpace = 0;
                if (node.type == NodeType.IO_TAG) {
                    Object countObj = node.settings.get("item_count");
                    if (countObj instanceof Number num) itemCount = num.intValue();
                    
                    Object freeObj = node.settings.get("free_space");
                    if (freeObj instanceof Number num) freeSpace = num.intValue();
                }

                visible.add(new SyncHandheldDataPayload.DeviceEntry(node.pos, node.networkId, node.customName, node.type.name(), finalSignal, connectionMode, dp, dm, da, dv, alerts, range, hold, itemCount, freeSpace));
            }
        }
        return visible;
    }

    public static int countSolidBlocks(Level level, BlockPos start, BlockPos end) { if (start.equals(end)) return 0; net.minecraft.world.phys.Vec3 startVec = net.minecraft.world.phys.Vec3.atCenterOf(start); net.minecraft.world.phys.Vec3 endVec = net.minecraft.world.phys.Vec3.atCenterOf(end); int obs = 0; double dist = Math.sqrt(start.distSqr(end)); if (dist == 0) return 0; net.minecraft.world.phys.Vec3 dir = endVec.subtract(startVec).normalize(); for (double d = 1.0; d < dist; d += 1.0) { net.minecraft.world.phys.Vec3 step = startVec.add(dir.scale(d)); BlockPos p = BlockPos.containing(step.x, step.y, step.z); if (!p.equals(start) && !p.equals(end)) { if (level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) { if (level.getBlockState(p).isRedstoneConductor(level, p)) { obs++; } } else { return -1; } } } return obs; }
    public static double calculateSignal(Level level, BlockPos tPos, BlockPos rPos, NodeType type, boolean isInfrastructure) { 
        double dist = Math.sqrt(tPos.distSqr(rPos)); 
        int measured = countSolidBlocks(level, tPos, rPos); 
        int obs = 2; 
        
        if (measured == -1) {
            // UNLOADED CHUNK BRIDGE: Use RAM cache if physical world is not available
            NetworkNode node = SERVER_REGISTRY.get(tPos);
            if (node != null) {
                String targetKey = rPos.getX() + ", " + rPos.getY() + ", " + rPos.getZ();
                obs = node.neighborObs.getOrDefault(targetKey, 2);
            }
        } else {
            obs = measured;
            if (isInfrastructure) { 
                NetworkNode node = SERVER_REGISTRY.get(tPos); 
                String targetKey = rPos.getX() + ", " + rPos.getY() + ", " + rPos.getZ(); 
                if (node != null && (!node.neighborObs.containsKey(targetKey) || node.neighborObs.get(targetKey) != obs)) { 
                    node.neighborObs.put(targetKey, obs); dbDirty = true; lastChangeTime = System.currentTimeMillis(); 
                } 
            }
        }
        
        double baseRange = (type == NodeType.SHORT_RANGE) ? 20 : 128; 
        double maxRange = baseRange; 
        double impact = 0; 
        if (type == NodeType.SHORT_RANGE) { 
            impact = 0.1 * (Math.pow(2, obs) - 1); 
            maxRange = Math.min(64, baseRange * (1.0 + (impact / 100.0))); 
        } else { 
            impact = obs * 4.0; 
            maxRange = Math.max(0, baseRange * (1.0 - (impact / 100.0))); 
        } 
        if (maxRange <= 0 || dist > maxRange) return 0; 
        double rawSignal = (1.0 - (dist / maxRange)) * 100.0; 
        double finalSignal = (type == NodeType.SHORT_RANGE) ? rawSignal + impact : rawSignal - impact; 
        if (finalSignal < 1.0) return 0; 
        return Math.min(100, finalSignal); 
    }
    
    public static Map<BlockPos, NetworkNode> getConnectedMeshNodes(Level level, BlockPos startPos, String networkId) {
        Map<BlockPos, NetworkNode> connected = new HashMap<>(); Queue<BlockPos> queue = new LinkedList<>();
        String dim = level.dimension().identifier().toString(); Map<BlockPos, NetworkNode> registry = getRegistry(level);
        for (NetworkNode n : registry.values()) { if (n.dimension.equals(dim) && (n.type == NodeType.SHORT_RANGE || n.type == NodeType.LONG_RANGE) && canTransmit(level, n.pos, startPos, networkId, true)) { if (!connected.containsKey(n.pos)) { connected.put(n.pos, n); queue.add(n.pos); } } }
        while (!queue.isEmpty()) { BlockPos current = queue.poll(); for (NetworkNode next : registry.values()) { if (!connected.containsKey(next.pos) && next.dimension.equals(dim) && (next.type == NodeType.SHORT_RANGE || next.type == NodeType.LONG_RANGE) && canTransmit(level, current, next.pos, networkId, true)) { connected.put(next.pos, next); queue.add(next.pos); } } }
        return connected;
    }

    private static boolean canTransmit(Level level, BlockPos tPos, BlockPos rPos, String networkId, boolean isInfrastructure) {
        NetworkNode tNode = getRegistry(level).get(tPos);
        if (tNode == null || !tNode.networkId.equals(networkId)) return false;
        if (calculateSignal(level, tPos, rPos, tNode.type, isInfrastructure) > 0) return true;
        if (isInfrastructure) {
            NetworkNode rNode = getRegistry(level).get(rPos);
            if (rNode != null && rNode.networkId.equals(networkId) && 
               (rNode.type == NodeType.SHORT_RANGE || rNode.type == NodeType.LONG_RANGE)) {
                return calculateSignal(level, rPos, tPos, rNode.type, isInfrastructure) > 0;
            }
        }
        return false;
    }
    
    public static boolean isConnected(Level level, BlockPos pos, String networkId) {
        if (networkId == null || networkId.isEmpty()) return false;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) { if (player.blockPosition().distSqr(pos) <= 100) return true; }
        String dim = level.dimension().identifier().toString();
        for (NetworkNode n : getRegistry(level).values()) { if (n.dimension.equals(dim) && (n.type == NodeType.SHORT_RANGE || n.type == NodeType.LONG_RANGE) && canTransmit(level, n.pos, pos, networkId, false)) return true; }
        return false;
    }

    public static boolean arePositionsConnected(Level level, BlockPos pos1, BlockPos pos2, String networkId) {
        if (networkId == null || networkId.isEmpty()) return false;
        if (pos1.distSqr(pos2) <= 100) return true;
        Map<BlockPos, NetworkNode> mesh = getConnectedMeshNodes(level, pos1, networkId);
        if (mesh.containsKey(pos2)) return true;
        for (BlockPos tPos : mesh.keySet()) if (canTransmit(level, tPos, pos2, networkId, true)) return true;
        return false;
    }

    public static List<BlockPos> findMeshPath(Level level, BlockPos start, BlockPos end, String networkId) {
        if (networkId == null || networkId.isEmpty()) return Collections.emptyList();
        
        // 1. OPTIMIZATION: Check if we can transmit DIRECTLY first (Limit: 20 blocks)
        if (start.distSqr(end) <= 400) { 
            if (isConnected(level, start, networkId) && isConnected(level, end, networkId)) {
                List<BlockPos> directPath = new ArrayList<>();
                directPath.add(end);
                return directPath;
            }
        }

        Map<BlockPos, NetworkNode> registry = getRegistry(level);
        Map<BlockPos, BlockPos> parentMap = new HashMap<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        // We start from the Hub's neighbors that are on the same network
        for (NetworkNode n : registry.values()) {
            if (n.networkId.equals(networkId) && canTransmit(level, n.pos, start, networkId, true)) {
                queue.add(n.pos);
                visited.add(n.pos);
                parentMap.put(n.pos, start);
            }
        }

        BlockPos targetNode = null;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            
            // Check if we can reach the target IO Tag from here
            if (canTransmit(level, current, end, networkId, true)) {
                targetNode = current;
                break;
            }

            // Explore other transmitters in the mesh
            for (NetworkNode next : registry.values()) {
                if (!visited.contains(next.pos) && next.networkId.equals(networkId) && 
                   (next.type == NodeType.SHORT_RANGE || next.type == NodeType.LONG_RANGE) &&
                   canTransmit(level, current, next.pos, networkId, true)) {
                    
                    visited.add(next.pos);
                    parentMap.put(next.pos, current);
                    queue.add(next.pos);
                }
            }
        }

        if (targetNode != null) {
            List<BlockPos> path = new ArrayList<>();
            path.add(end); // The final destination
            BlockPos step = targetNode;
            while (step != null && !step.equals(start)) {
                path.add(0, step);
                step = parentMap.get(step);
            }
            return path;
        }

        return Collections.emptyList();
    }

    public static void broadcastToNetwork(Level level, String networkId, String message) {
        if (level == null || level.isClientSide() || level.getServer() == null || networkId.isEmpty()) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(level.dimension())) continue;
            boolean hasInterface = false;
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.getItem() instanceof HandheldDeviceItem) {
                    String handheldIds = HandheldDeviceItem.getNetworkId(stack);
                    if (Arrays.asList(handheldIds.split(",")).contains(networkId)) {
                        hasInterface = true;
                        break;
                    }
                }
            }
            if (hasInterface && isConnected(player.level(), player.blockPosition(), networkId)) {
                player.displayClientMessage(Component.literal(message), true);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new net.rdv88.redos.network.payload.ActionFeedbackPayload(message, false));
            }
        }
    }
    public static void startTrackingHandheld(ServerPlayer player, String networkIds) { ACTIVE_HANDHELD_USERS.put(player.getUUID(), networkIds); }
    public static void stopTrackingHandheld(ServerPlayer player) { ACTIVE_HANDHELD_USERS.remove(player.getUUID()); }
    public static void syncToPlayer(ServerPlayer player) { List<SyncNetworkNodesPayload.NodeData> data = new ArrayList<>(); for (NetworkNode node : SERVER_REGISTRY.values()) data.add(new net.rdv88.redos.network.payload.SyncNetworkNodesPayload.NodeData(node.pos, node.networkId, node.customName, node.type.name(), node.dimension)); net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SyncNetworkNodesPayload(data)); }
    public static void clientSync(List<net.rdv88.redos.network.payload.SyncNetworkNodesPayload.NodeData> nodes) { CLIENT_REGISTRY.clear(); for (net.rdv88.redos.network.payload.SyncNetworkNodesPayload.NodeData data : nodes) CLIENT_REGISTRY.put(data.pos(), new NetworkNode(data.pos(), data.id(), data.name(), NodeType.valueOf(data.type()), data.dim(), "client", "client")); }
    public static void syncToAll(Level level) { if (level == null || level.isClientSide() || level.getServer() == null) return; List<SyncNetworkNodesPayload.NodeData> data = new ArrayList<>(); for (NetworkNode node : SERVER_REGISTRY.values()) data.add(new net.rdv88.redos.network.payload.SyncNetworkNodesPayload.NodeData(node.pos, node.networkId, node.customName, node.type.name(), node.dimension)); for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new net.rdv88.redos.network.payload.SyncNetworkNodesPayload(data)); }
    
    public static void forceSyncAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        List<SyncNetworkNodesPayload.NodeData> data = new ArrayList<>();
        for (NetworkNode node : SERVER_REGISTRY.values()) {
            data.add(new net.rdv88.redos.network.payload.SyncNetworkNodesPayload.NodeData(node.pos, node.networkId, node.customName, node.type.name(), node.dimension));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new SyncNetworkNodesPayload(data));
        }
        LOGGER.info("RED-OS: Force-sync triggered for all players.");
    }

    public static void updateObstructions(Level level, BlockPos pos) {}
    public static String getNetIdFromRegistry(Level level, BlockPos pos) { NetworkNode node = getRegistry(level).get(pos); return node != null ? node.networkId : ""; }
    
    public static @org.jetbrains.annotations.Nullable NetworkNode getNodeAt(BlockPos pos) {
        return SERVER_REGISTRY.get(pos);
    }

    public static NodeType getTypeFromRegistry(Level level, BlockPos pos) { NetworkNode node = getRegistry(level).get(pos); return node != null ? node.type : null; }

    public static class NetworkSavedData {
        public static NetworkSavedData get(ServerLevel level) { return new NetworkSavedData(); }
        public final Map<BlockPos, NetworkNode> nodes = SERVER_REGISTRY;
        public void setDirty() { dbDirty = true; lastChangeTime = System.currentTimeMillis(); }
    }
}
