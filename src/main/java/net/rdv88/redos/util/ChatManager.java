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
    private static final long SAVE_DELAY_MS = 150000; // 2.5 minutes
    private static final int MAX_HISTORY = 100;

    public record ChatEntry(String sender, String message, long timestamp) {}
    private static class ChatData { public List<ChatEntry> history = new ArrayList<>(); }

    private static final List<ChatEntry> GENERAL_HISTORY = new ArrayList<>();
    private static long lastChangeTime = 0;
    private static boolean isDirty = false;
    private static final AtomicBoolean IS_SAVING = new AtomicBoolean(false);

    public static void addMessage(String sender, String message) {
        GENERAL_HISTORY.add(new ChatEntry(sender, message, System.currentTimeMillis()));
        if (GENERAL_HISTORY.size() > MAX_HISTORY) GENERAL_HISTORY.remove(0);
        
        isDirty = true;
        lastChangeTime = System.currentTimeMillis();
    }

    public static List<ChatEntry> getGeneralHistory() {
        return new ArrayList<>(GENERAL_HISTORY);
    }

    private static File getChatFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("redos");
        File dir = configDir.toFile();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "chat_history.json");
    }

    public static void loadHistory() {
        File file = getChatFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            ChatData data = GSON.fromJson(reader, ChatData.class);
            if (data != null && data.history != null) {
                GENERAL_HISTORY.clear();
                for (ChatEntry entry : data.history) {
                    // DECODE from Base64 for RAM usage
                    String decodedMsg = new String(java.util.Base64.getDecoder().decode(entry.message()), java.nio.charset.StandardCharsets.UTF_8);
                    GENERAL_HISTORY.add(new ChatEntry(entry.sender(), decodedMsg, entry.timestamp()));
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

    public static void saveHistory(boolean async) {
        if (IS_SAVING.get()) return;
        
        // Encode messages to Base64 for secure disk storage
        List<ChatEntry> encodedHistory = new ArrayList<>();
        for (ChatEntry entry : GENERAL_HISTORY) {
            String encodedMsg = java.util.Base64.getEncoder().encodeToString(entry.message().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            encodedHistory.add(new ChatEntry(entry.sender(), encodedMsg, entry.timestamp()));
        }
        
        Runnable saveTask = () -> {
            if (!IS_SAVING.compareAndSet(false, true)) return;
            try (FileWriter writer = new FileWriter(getChatFile())) {
                ChatData data = new ChatData();
                data.history = encodedHistory;
                GSON.toJson(data, writer);
                LOGGER.info("RED-OS: Chat history secured and synced to disk ({} messages)", encodedHistory.size());
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