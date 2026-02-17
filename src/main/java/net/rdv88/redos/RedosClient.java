package net.rdv88.redos;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.rdv88.redos.client.gui.screen.CameraLoadingScreen;
import net.rdv88.redos.client.gui.screen.CameraViewScreen;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.client.gui.screen.VersionNoticeScreen;
import net.rdv88.redos.client.render.entity.WatcherEntityRenderer;
import net.rdv88.redos.client.render.block.DroneStationBlockEntityRenderer;
import net.rdv88.redos.entity.ModEntities;
import net.rdv88.redos.network.payload.*;
import net.rdv88.redos.network.ModMessages;
import net.rdv88.redos.util.TechNetwork;
import net.rdv88.redos.util.UpdateChecker;
import net.rdv88.redos.util.PermissionCache;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class RedosClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-client");
    private static boolean serverVersionReceived = false;
    private static String lastServerVersion = "Unknown";

    public static void setVersionReceived(boolean received) { serverVersionReceived = received; }
    public static boolean isVersionReceived() { return serverVersionReceived; }
    public static void setServerVersion(String ver) { lastServerVersion = ver; }
    public static String getLastServerVersion() { return lastServerVersion; }

    private void handleVersionMismatch(String serverVer, String clientVer, Minecraft client) {
        if (!clientVer.equalsIgnoreCase(serverVer)) {
            String latest = UpdateChecker.getLatestRemoteVersion();
            String message = "§e§lRedos\n\nA version discrepancy was found between your\nlocal engine and the remote server host.\n\n§8» §7Your System: §aV" + clientVer + "\n§8» §7Remote Host: §eV" + serverVer + "\n\n§fLATEST BUILD: §bV" + latest + " §3(Available)\n\n§7Both systems must match to gain access.\n§7Please update your local software files.";
            client.execute(() -> { 
                client.setScreen(new VersionNoticeScreen(null, "", message, true, (shouldContinue) -> { 
                    client.disconnectFromWorld(Component.literal("Connection aborted by user.")); 
                })); 
            });
        }
    }

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(DroneStationBlockEntityRenderer.LAYER_LOCATION, DroneStationBlockEntityRenderer::createModelData);

        EntityRendererRegistry.register(ModEntities.WATCHER, WatcherEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DRONE, net.rdv88.redos.client.render.entity.DroneEntityRenderer::new);
        
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
            net.rdv88.redos.block.entity.ModBlockEntities.QUANTUM_PORTER_ENTITY, 
            context -> new net.rdv88.redos.client.render.block.QuantumPorterBlockEntityRenderer()
        );

        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
            net.rdv88.redos.block.entity.ModBlockEntities.DRONE_STATION_BLOCK_ENTITY, 
            DroneStationBlockEntityRenderer::new
        );

        net.minecraft.client.gui.screens.MenuScreens.register(net.rdv88.redos.screen.ModScreenHandlers.DRONE_STATION_SCREEN_HANDLER, net.rdv88.redos.client.gui.screen.DroneStationScreen::new);

        // --- NETWORK HANDLERS ---
        ClientLoginNetworking.registerGlobalReceiver(ModMessages.VERSION_CHECK_ID, (client, handler, buf, listenerAdder) -> {
            String serverVer = buf.readUtf().trim();
            String clientVer = Redos.VERSION.trim();
            setVersionReceived(true);
            setServerVersion(serverVer);
            handleVersionMismatch(serverVer, clientVer, client);
            FriendlyByteBuf response = new FriendlyByteBuf(Unpooled.buffer());
            response.writeUtf(clientVer);
            return CompletableFuture.completedFuture(response);
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPermissionsPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                String serverVer = payload.serverVersion().trim();
                String clientVer = Redos.VERSION.trim();
                setVersionReceived(true);
                setServerVersion(serverVer);
                PermissionCache.update(payload.hasMainAccess(), payload.hasHighTechAccess());
                handleVersionMismatch(serverVer, clientVer, context.client());
                HandheldScreen.refreshApp();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncNetworkNodesPayload.ID, (payload, context) -> {
            context.client().execute(() -> { TechNetwork.clientSync(payload.nodes()); });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(SyncHandheldDataPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                HandheldScreen.updateDeviceList(payload.devices());
                CameraViewScreen.setDeviceCache(payload.devices());
                net.rdv88.redos.client.gui.screen.QuantumPorterScreen.setDeviceCache(payload.devices());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PorterGuiResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft.getInstance().setScreen(new net.rdv88.redos.client.gui.screen.QuantumPorterScreen(payload.pos(), payload.name(), payload.status()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncDroneHubTasksPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof net.rdv88.redos.client.gui.screen.DroneStationScreen screen) {
                    screen.updateTaskList(payload.tasks());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CameraViewResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.success()) Minecraft.getInstance().setScreen(new CameraLoadingScreen(payload.cameraName()));
                else {
                    HandheldScreen.showToast("§c" + payload.message());
                    if (Minecraft.getInstance().screen instanceof CameraViewScreen || Minecraft.getInstance().screen instanceof CameraLoadingScreen) Minecraft.getInstance().setScreen(null);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ActionFeedbackPayload.ID, (payload, context) -> {
            context.client().execute(() -> { HandheldScreen.showToast(payload.message()); });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.isLocalServer()) {
                new Thread(() -> {
                    try {
                        int attempts = 0;
                        while (!UpdateChecker.isCheckDone() && attempts < 50) { Thread.sleep(100); attempts++; }
                        if (UpdateChecker.isNewerVersionAvailable()) {
                            String latest = UpdateChecker.getLatestRemoteVersion();
                            client.execute(() -> { if (client.player != null) client.player.displayClientMessage(Component.literal("§c[RED-OS] §7System Update Available: §bV" + latest), false); });
                        }
                    } catch (InterruptedException ignored) {}
                }).start();
            }
        });
    }
}
