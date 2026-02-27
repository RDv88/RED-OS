package net.rdv88.redos.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-chat");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_DELAY_MS = 150000; 
    private static final int MAX_HISTORY = 100;

    public record ChatEntry(String sender, String message, long timestamp) {}
    public record PrivateEntry(String from, String to, String message, long timestamp) {}
    
    private static class ChatData { 
        public List<ChatEntry> history = new ArrayList<>(); 
        public List<ChatEntry> discord = new ArrayList<>();
        public List<PrivateEntry> mailbox = new ArrayList<>(); // Temporary buffer for offline users
    }

    private static final List<ChatEntry> GENERAL_HISTORY = new ArrayList<>();
    private static final List<ChatEntry> DISCORD_HISTORY = new ArrayList<>();
    private static final List<PrivateEntry> OFFLINE_MAILBOX = new ArrayList<>();
    
    private static long lastChangeTime = 0;
    private static boolean isDirty = false;
    private static final AtomicBoolean IS_SAVING = new AtomicBoolean(false);

    public static void addMessage(String sender, String message, java.util.UUID uuid) {
        GENERAL_HISTORY.add(new ChatEntry(sender, message, System.currentTimeMillis()));
        if (GENERAL_HISTORY.size() > MAX_HISTORY) GENERAL_HISTORY.remove(0);
        isDirty = true; lastChangeTime = System.currentTimeMillis();
        
        // FORWARD TO DISCORD with unique player color and UUID for skin
        int color = getStaticPlayerColor(sender);
        DiscordBridge.sendToDiscord(sender, message, color, uuid.toString());
    }

    private static int getStaticPlayerColor(String name) {
        int hash = name.replaceAll("§[0-9a-fklmnor]", "").hashCode();
        float hue = (Math.abs(hash) % 360) / 360.0f;
        return 0xFF000000 | net.minecraft.util.Mth.hsvToRgb(hue, 0.7f, 0.9f);
    }

    public static void addDiscordMessage(String sender, String message) {
        String formattedSender = "§9[D] §f" + sender;
        GENERAL_HISTORY.add(new ChatEntry(formattedSender, message, System.currentTimeMillis()));
        if (GENERAL_HISTORY.size() > MAX_HISTORY) GENERAL_HISTORY.remove(0);
        isDirty = true; lastChangeTime = System.currentTimeMillis();
        
        // SYNC TO ALL CLIENTS & VANILLA CHAT
        net.minecraft.server.MinecraftServer server = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance() instanceof net.minecraft.server.MinecraftServer s ? s : null;
        if (server != null) {
            net.rdv88.redos.network.ModMessages.broadcastChatSync(server);
            
            // Broadcast to standard Minecraft chat
            net.minecraft.network.chat.Component vanillaMsg = net.minecraft.network.chat.Component.literal("§9[Discord] §f" + sender + "§7: " + message);
            server.getPlayerList().broadcastSystemMessage(vanillaMsg, false);
        }
    }

    public static List<ChatEntry> getDiscordHistory() {
        return new ArrayList<>(DISCORD_HISTORY);
    }

    public static void addPrivateMessage(String from, String to, String message) {
        // This is now purely for temporary buffering if recipient is offline
        OFFLINE_MAILBOX.add(new PrivateEntry(from, to, message, System.currentTimeMillis()));
        isDirty = true; lastChangeTime = System.currentTimeMillis();
    }

    public static List<ChatEntry> getGeneralHistory() {
        return new ArrayList<>(GENERAL_HISTORY);
    }

    public static List<PrivateEntry> fetchAndClearMail(String playerName) {
        List<PrivateEntry> mail = OFFLINE_MAILBOX.stream()
            .filter(e -> e.to().equals(playerName))
            .toList();
        OFFLINE_MAILBOX.removeAll(mail);
        if (!mail.isEmpty()) isDirty = true;
        return mail;
    }

    private static File getChatFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("redos");
        File dir = configDir.toFile();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "chat_history.json");
    }

    public static void loadHistory() {
        File file = getChatFile();
        if (!file.exists()) {
            GENERAL_HISTORY.add(new ChatEntry("§7SYSTEM", "§8--- RED-OS Chat initialized ---", System.currentTimeMillis()));
            saveHistory(false);
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            ChatData data = GSON.fromJson(reader, ChatData.class);
            if (data != null) {
                if (data.history != null) {
                    GENERAL_HISTORY.clear();
                    for (ChatEntry entry : data.history) {
                        String decodedMsg = new String(java.util.Base64.getDecoder().decode(entry.message()), java.nio.charset.StandardCharsets.UTF_8);
                        GENERAL_HISTORY.add(new ChatEntry(entry.sender(), decodedMsg, entry.timestamp()));
                    }
                }
                if (data.mailbox != null) {
                    OFFLINE_MAILBOX.clear();
                    for (PrivateEntry entry : data.mailbox) {
                        String decodedMsg = new String(java.util.Base64.getDecoder().decode(entry.message()), java.nio.charset.StandardCharsets.UTF_8);
                        OFFLINE_MAILBOX.add(new PrivateEntry(entry.from(), entry.to(), decodedMsg, entry.timestamp()));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("RED-OS: Failed to load chat history", e);
        }
    }

    public static void tick() {
        if (isDirty && (System.currentTimeMillis() - lastChangeTime > SAVE_DELAY_MS)) {
            saveHistory(true);
            isDirty = false;
        }
    }

    public static void registerEvents() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> loadHistory());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveHistory(false));

        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            addMessage(sender.getName().getString(), message.signedContent(), sender.getUUID());
        });

        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.COMMAND_MESSAGE.register((message, sender, params) -> {
            String senderName = sender.getTextName();
            String content = message.signedContent();
            java.util.UUID uuid = sender.getPlayer() != null ? sender.getPlayer().getUUID() : java.util.UUID.randomUUID();
            
            if (params.targetName().isPresent()) {
                String target = params.targetName().get().getString();
                addPrivateMessage(senderName, target, content);
            } else {
                addMessage(senderName, content, uuid);
            }
        });
    }

    public static void saveHistory(boolean async) {
        if (IS_SAVING.get()) return;
        
        List<ChatEntry> encodedHistory = new ArrayList<>();
        for (ChatEntry entry : GENERAL_HISTORY) {
            String encodedMsg = java.util.Base64.getEncoder().encodeToString(entry.message().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            encodedHistory.add(new ChatEntry(entry.sender(), encodedMsg, entry.timestamp()));
        }

        List<PrivateEntry> encodedMailbox = new ArrayList<>();
        for (PrivateEntry entry : OFFLINE_MAILBOX) {
            String encodedMsg = java.util.Base64.getEncoder().encodeToString(entry.message().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            encodedMailbox.add(new PrivateEntry(entry.from(), entry.to(), encodedMsg, entry.timestamp()));
        }
        
        Runnable saveTask = () -> {
            if (!IS_SAVING.compareAndSet(false, true)) return;
            try (FileWriter writer = new FileWriter(getChatFile())) {
                ChatData data = new ChatData();
                data.history = encodedHistory;
                data.mailbox = encodedMailbox;
                GSON.toJson(data, writer);
                LOGGER.info("RED-OS: Server Data Secured (Gen: {}, Mailbox: {})", encodedHistory.size(), encodedMailbox.size());
            } catch (IOException e) {
                LOGGER.error("RED-OS: Failed to save chat history", e);
            } finally {
                IS_SAVING.set(false);
            }
        };

        if (async) CompletableFuture.runAsync(saveTask);
        else saveTask.run();
    }
}