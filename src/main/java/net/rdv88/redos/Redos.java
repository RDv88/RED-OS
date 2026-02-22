package net.rdv88.redos;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.rdv88.redos.block.ModBlocks;
import net.rdv88.redos.block.entity.ModBlockEntities;
import net.rdv88.redos.item.ModItemGroups;
import net.rdv88.redos.item.ModItems;
import net.rdv88.redos.network.ModMessages;
import net.rdv88.redos.util.CameraViewHandler;
import net.rdv88.redos.util.TechNetwork;
import net.rdv88.redos.util.PermissionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Redos implements ModInitializer {
	public static final String MOD_ID = "redos";
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
    private static final Logger LOGGER = LoggerFactory.getLogger("redos");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing RED-OS V{}...", VERSION);
        net.rdv88.redos.util.UpdateChecker.checkForUpdates();
        net.rdv88.redos.util.ChatManager.registerEvents();

        // Server-side response receiver (Handshake Enforcement V1.0.5)
        ServerLoginNetworking.registerGlobalReceiver(ModMessages.VERSION_CHECK_ID, (server, handler, understood, buf, synchronizer, sender) -> {
            if (!understood) {
                handler.disconnect(net.minecraft.network.chat.Component.literal("§c[RED-OS] Error: Protocol mismatch. Please install RED-OS mod."));
                return;
            }
            
            if (buf.readableBytes() == 0) {
                LOGGER.warn("RED-OS: [SECURITY] Kicking player due to outdated version (pre-1.0.5)");
                handler.disconnect(net.minecraft.network.chat.Component.literal(
                    "§fInitialization paused.\n§fYour system needs a quick update!\n\n" +
                    "§8» §7Local Engine:  §cPRE-SECURITY (Unknown)\n\n" +
                    "§7Please update your mod to V" + VERSION + "\n§7to gain access."
                ));
                return;
            }

            String clientVersion = buf.readUtf().trim();
            String serverVersion = VERSION.trim();
            
            if (!clientVersion.equalsIgnoreCase(serverVersion)) {
                LOGGER.warn("RED-OS: [SECURITY] Kicking player due to version mismatch. Server: {}, Client: {}", serverVersion, clientVersion);
                handler.disconnect(net.minecraft.network.chat.Component.literal(
                    "§fInitialization paused.\n§fYour system needs a quick update!\n\n" +
                    "§8» §7Remote Host:   §aV" + serverVersion + "\n" +
                    "§8» §7Local Engine:  §cV" + clientVersion + "\n\n" +
                    "§7Visit our GitHub to find the latest version.\n§7Thank you!"
                ));
            }
        });

        ModItemGroups.registerItemGroups();
        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerBlockEntities();
        net.rdv88.redos.screen.ModScreenHandlers.registerScreenHandlers();
        net.rdv88.redos.entity.ModEntities.register();
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(net.rdv88.redos.entity.ModEntities.WATCHER, net.rdv88.redos.entity.WatcherEntity.createAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(net.rdv88.redos.entity.ModEntities.DRONE, net.rdv88.redos.entity.DroneEntity.createAttributes());
        ModMessages.registerNetworking();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("redos")
            .then(Commands.literal("debug")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        int total = TechNetwork.SERVER_REGISTRY.size();
                        int loaded = 0;
                        for (BlockPos pos : TechNetwork.SERVER_REGISTRY.keySet()) {
                            if (player.level().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) loaded++;
                        }
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§e[TechCraft JSON Debug] §fTotal Nodes: §b" + total + "§f | Loaded: §a" + loaded + "§f | Unloaded: §c" + (total - loaded)
                        ), false);
                    }
                    return 1;
                })));
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> true);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CameraViewHandler.tick(server);
            TechNetwork.tickLiveUpdates(server);
            net.rdv88.redos.util.ChatManager.tick();
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            String worldName = server.getWorldData().getLevelName();
            TechNetwork.loadDatabase(worldName);
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TechNetwork.saveDatabase(false);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            TechNetwork.saveDatabase(false);
        }));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            TechNetwork.syncToPlayer(handler.player);
            net.rdv88.redos.util.RecipeGuardian.checkAndFix(handler.player, server);
            PermissionHandler.pushPermissions(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CameraViewHandler.stopViewing(handler.player));
    }
}
