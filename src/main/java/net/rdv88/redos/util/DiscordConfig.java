package net.rdv88.redos.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class DiscordConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("redos/discord_bridge.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String botToken = "";
    public String channelId = "";
    public boolean enabled = false;

    private static String worldName = "default";
    public static void setWorldName(String name) { worldName = name; }

    private static String xor(String input) {
        String key = "redos_" + worldName;
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            output.append((char) (input.charAt(i) ^ key.charAt(i % key.length())));
        }
        return java.util.Base64.getEncoder().encodeToString(output.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String dexor(String input) {
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(input), java.nio.charset.StandardCharsets.UTF_8);
            String key = "redos_" + worldName;
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < decoded.length(); i++) {
                output.append((char) (decoded.charAt(i) ^ key.charAt(i % key.length())));
            }
            return output.toString();
        } catch (Exception e) { return ""; }
    }

    private static DiscordConfig instance;

    public static DiscordConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                instance = GSON.fromJson(reader, DiscordConfig.class);
                if (instance != null && !instance.botToken.isEmpty()) {
                    instance.botToken = dexor(instance.botToken);
                    org.slf4j.LoggerFactory.getLogger("redos-discord").info("RED-OS: Discord config loaded and decrypted.");
                }
            } catch (Exception e) {
                instance = new DiscordConfig();
            }
        } else {
            instance = new DiscordConfig();
            save();
        }
    }

    public static void save() {
        if (instance == null) return;
        try {
            File file = CONFIG_PATH.toFile();
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            
            // Temporary copy for encryption
            DiscordConfig copy = new DiscordConfig();
            copy.botToken = xor(instance.botToken);
            copy.channelId = instance.channelId;
            copy.enabled = instance.enabled;

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(copy, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
