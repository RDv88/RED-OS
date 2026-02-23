package net.rdv88.redos.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ClientChatHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<ChatManager.PrivateEntry> PRIVATE_HISTORY = new ArrayList<>();

    private static File getStorageFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("redos");
        File dir = configDir.toFile();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "messenger_vault.json");
    }

    public static void load() {
        File file = getStorageFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            ChatManager.PrivateEntry[] data = GSON.fromJson(reader, ChatManager.PrivateEntry[].class);
            if (data != null) {
                PRIVATE_HISTORY.clear();
                for (var entry : data) PRIVATE_HISTORY.add(entry);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(getStorageFile())) {
            GSON.toJson(PRIVATE_HISTORY, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addEntry(ChatManager.PrivateEntry entry) {
        PRIVATE_HISTORY.add(entry);
        if (PRIVATE_HISTORY.size() > 500) PRIVATE_HISTORY.remove(0);
        save();
    }

    public static List<ChatManager.PrivateEntry> getHistory() {
        return new ArrayList<>(PRIVATE_HISTORY);
    }
}