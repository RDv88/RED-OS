package net.rdv88.redos.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.rdv88.redos.block.custom.WirelessCameraBlock;
import net.rdv88.redos.block.entity.WirelessCameraBlockEntity;
import net.rdv88.redos.entity.ModEntities;
import net.rdv88.redos.entity.WatcherEntity;
import net.rdv88.redos.network.payload.CameraViewResponsePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CameraViewHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos");
    private static final Map<UUID, CameraState> playerCameraStates = new HashMap<>();

    private static class CameraState {
        public BlockPos cameraPos;
        public int watcherEntityId;
        public GameType originalGameMode;
        public long startTime;
        public ChunkPos forcedChunk;
        public double returnX, returnY, returnZ;
        public float returnYaw, returnPitch;

        public CameraState(ServerPlayer player, BlockPos pos, int watcherId, GameType gm, ChunkPos forcedChunk) { 
            this.cameraPos = pos; 
            this.watcherEntityId = watcherId; 
            this.originalGameMode = gm; 
            this.startTime = System.currentTimeMillis();
            this.forcedChunk = forcedChunk;
            this.returnX = player.getX();
            this.returnY = player.getY();
            this.returnZ = player.getZ();
            this.returnYaw = player.getYRot();
            this.returnPitch = player.getXRot();
        }
    }

    public static boolean isViewingCamera(ServerPlayer player) {
        return playerCameraStates.containsKey(player.getUUID());
    }

    public static void startViewing(ServerPlayer player, BlockPos camPos) {
        if (playerCameraStates.containsKey(player.getUUID())) {
            stopViewing(player);
        }

        ServerLevel level = (ServerLevel) player.level();
        String netId = TechNetwork.getNetIdFromRegistry(level, camPos);
        String camName = "Unknown Camera";
        float camYaw = 0;
        float camPitch = 0;

        BlockState state = level.getBlockState(camPos);
        BlockEntity be = level.getBlockEntity(camPos);
        
        if (be instanceof WirelessCameraBlockEntity camBE) {
            camName = camBE.getCameraName();
            camYaw = camBE.getYaw();
            camPitch = camBE.getPitch();
        }
        
        if (!TechNetwork.isConnected(level, player.blockPosition(), netId)) {
            player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new CameraViewResponsePayload(false, "No Network connection", "")));
            return;
        }

        GameType oldGM = player.gameMode.getGameModeForPlayer();
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        level.setChunkForced(playerChunk.x, playerChunk.z, true);

        WatcherEntity watcher = new WatcherEntity(ModEntities.WATCHER, level);
        watcher.teleportTo(player.getX(), player.getY(), player.getZ());
        watcher.setYRot(player.getYRot());
        watcher.setXRot(player.getXRot());
        watcher.setYHeadRot(player.getYRot());
        watcher.setOwner(player);
        level.addFreshEntity(watcher);

        playerCameraStates.put(player.getUUID(), new CameraState(player, camPos, watcher.getId(), oldGM, playerChunk));

        player.setGameMode(GameType.SPECTATOR);
        // FORCE INVISIBILITY: This hides the player model from 3rd party first-person mods (like First Person Model)
        // Duration -1 (infinite until removed), Ambient=true, showParticles=false, showIcon=false
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, false, false, false));

        double spawnX = camPos.getX() + 0.5;
        double spawnY = camPos.getY() - 1.0; 
        double spawnZ = camPos.getZ() + 0.5;
        
        if (state.getBlock() instanceof WirelessCameraBlock) {
            Direction facing = state.getValue(WirelessCameraBlock.FACING);
            spawnX += facing.getStepX() * 0.5;
            spawnY += facing.getStepY() * 0.5;
            spawnZ += facing.getStepZ() * 0.5;
        }

        player.teleportTo(level, spawnX, spawnY, spawnZ, Set.of(), camYaw, camPitch, true);
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new CameraViewResponsePayload(true, "Connected", camName)));
    }

    public static void stopViewing(ServerPlayer player) {
        CameraState state = playerCameraStates.remove(player.getUUID());
        if (state == null) return;

        ServerLevel level = (ServerLevel) player.level();
        if (state.forcedChunk != null) {
            level.setChunkForced(state.forcedChunk.x, state.forcedChunk.z, false);
        }

        if (player.connection != null) {
            player.teleportTo(level, state.returnX, state.returnY, state.returnZ, Set.of(), state.returnYaw, state.returnPitch, true);
            player.setGameMode(state.originalGameMode);
            // REMOVE INVISIBILITY: Back to normal state
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            player.connection.send(new ClientboundSetCameraPacket(player));
        }

        Entity watcher = level.getEntity(state.watcherEntityId);
        if (watcher != null) {
            watcher.discard();
            LOGGER.info("RED-OS: Watcher dummy removed for {}", player.getName().getString());
        }
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        long serverTicks = server.getTickCount();
        for (UUID playerId : new HashSet<>(playerCameraStates.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.connection == null) {
                CameraState state = playerCameraStates.remove(playerId);
                if (state != null) {
                    server.overworld().setChunkForced(state.forcedChunk.x, state.forcedChunk.z, false);
                    Entity watcher = server.overworld().getEntity(state.watcherEntityId);
                    if (watcher != null) watcher.discard();
                }
                continue;
            }

            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                player.setGameMode(GameType.SPECTATOR);
            }

            if ((serverTicks + (playerId.hashCode() & 0xFFFF)) % 50 != 0) continue;
            CameraState state = playerCameraStates.get(playerId);
            ServerLevel level = (ServerLevel) player.level();
            if (System.currentTimeMillis() - state.startTime < 10000) continue;
            String camId = TechNetwork.getNetIdFromRegistry(level, state.cameraPos);
            if (!TechNetwork.isConnected(level, new BlockPos((int)state.returnX, (int)state.returnY, (int)state.returnZ), camId)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cCamera signal lost (Mesh Disconnected)"), true);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new net.rdv88.redos.network.payload.CameraViewResponsePayload(false, "Signal lost", ""));
                stopViewing(player);
            }
        }
    }
}
