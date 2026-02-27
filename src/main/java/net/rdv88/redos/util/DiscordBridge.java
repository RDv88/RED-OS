package net.rdv88.redos.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-discord");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    
    private static WebSocket webSocket;
    private static ScheduledExecutorService scheduler;
    private static String lastSequence = null;
    private static boolean isConnected = false;

    public static boolean isConnected() { return isConnected && webSocket != null; }

    public static void init() {
        DiscordConfig config = DiscordConfig.get();
        if (config.enabled && !config.botToken.isEmpty()) {
            connect();
        }
    }

    public static void connect() {
        DiscordConfig config = DiscordConfig.get();
        isConnected = false;
        if (config.botToken.isEmpty()) return;

        if (scheduler != null) scheduler.shutdownNow();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        if (!config.enabled) {
            if (webSocket != null) webSocket.abort();
            LOGGER.info("RED-OS: Discord Bridge is disabled.");
            return;
        }

        if (webSocket != null) webSocket.abort();

        try {
            HTTP_CLIENT.newWebSocketBuilder()
                    .buildAsync(URI.create("wss://gateway.discord.gg/?v=10&encoding=json"), new DiscordListener());
            LOGGER.info("RED-OS: Discord Bridge initialization sequence started.");
        } catch (Exception e) {
            LOGGER.error("RED-OS: Failed to initiate Discord connection", e);
        }
    }

    public static void sendToDiscord(String username, String message, int color, String uuid) {
        DiscordConfig config = DiscordConfig.get();
        if (!config.enabled || config.botToken.isEmpty() || config.channelId.isEmpty()) return;

        if (username.startsWith("§b[D]")) return;

        String cleanUser = username.replaceAll("§[0-9a-fklmnor]", "");
        String cleanMsg = message.replaceAll("§[0-9a-fklmnor]", "");

        // Create Embed JSON with LARGE text formatting
        JsonObject embed = new JsonObject();
        // # makes it a large Header in Discord
        embed.addProperty("description", "### **" + cleanUser + "**\n" + cleanMsg);
        embed.addProperty("color", color & 0xFFFFFF);

        // Add 3D Head Thumbnail
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", "https://visage.surgeplay.com/head/64/" + uuid);
        embed.add("thumbnail", thumbnail);

        com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
        embeds.add(embed);

        JsonObject body = new JsonObject();
        body.add("embeds", embeds);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://discord.com/api/v10/channels/" + config.channelId + "/messages"))
                .header("Authorization", "Bot " + config.botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class DiscordListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            // Layer 1: Strict Intents (Only Messages + Content)
            JsonObject identify = new JsonObject();
            identify.addProperty("op", 2);
            JsonObject d = new JsonObject();
            d.addProperty("token", DiscordConfig.get().botToken);
            d.addProperty("intents", 33280); 
            JsonObject props = new JsonObject();
            props.addProperty("os", "linux");
            props.addProperty("browser", "redos");
            props.addProperty("device", "redos");
            d.add("properties", props);
            identify.add("d", d);
            
            ws.sendText(identify.toString(), true);
            LOGGER.info("RED-OS: Discord Handshake (Identify) sent.");
            WebSocket.Listener.super.onOpen(ws);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            String raw = data.toString();

            if (!raw.contains("MESSAGE_CREATE") && !raw.contains("READY")) {
                if (raw.contains("\"op\":10")) handleHello(ws, raw);
                return WebSocket.Listener.super.onText(ws, data, last);
            }

            try {
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                if (json.has("s") && !json.get("s").isJsonNull()) lastSequence = json.get("s").getAsString();
                
                if (json.has("t") && !json.get("t").isJsonNull()) {
                    String type = json.get("t").getAsString();
                    if (type.equals("READY")) {
                        isConnected = true;
                        LOGGER.info("RED-OS: Discord Bridge is now ONLINE.");
                    } else if (type.equals("MESSAGE_CREATE")) {
                        if (!raw.contains(DiscordConfig.get().channelId)) return WebSocket.Listener.super.onText(ws, data, last);
                        
                        JsonObject d = json.getAsJsonObject("d");
                        JsonObject authorObj = d.getAsJsonObject("author");
                        
                        // Use global_name (Nickname) if available, otherwise username
                        String author = authorObj.has("global_name") && !authorObj.get("global_name").isJsonNull() 
                                        ? authorObj.get("global_name").getAsString() 
                                        : authorObj.get("username").getAsString();
                                        
                        String content = d.get("content").getAsString();

                        // Detect Stickers, Attachments, and Embeds if text is empty
                        if (content.isEmpty()) {
                            if (d.has("sticker_items") && d.getAsJsonArray("sticker_items").size() > 0) {
                                content = "§7[Sticker]";
                            } else if (d.has("attachments") && d.getAsJsonArray("attachments").size() > 0) {
                                content = "§7[Media/Attachment]";
                            } else if (d.has("embeds") && d.getAsJsonArray("embeds").size() > 0) {
                                content = "§7[Embed/Link]";
                            }
                        }
                                        
                        boolean isBot = authorObj.has("bot") && authorObj.get("bot").getAsBoolean();

                        if (!isBot) {
                            LOGGER.info("RED-OS: Incoming message from Discord: <{}> {}", author, content);
                            ChatManager.addDiscordMessage(author, content);
                        }
                    }
                }
            } catch (Exception ignored) {}

            return WebSocket.Listener.super.onText(ws, data, last);
        }

        private void handleHello(WebSocket ws, String raw) {
            try {
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                int interval = json.getAsJsonObject("d").get("heartbeat_interval").getAsInt();
                scheduler.scheduleAtFixedRate(() -> {
                    JsonObject hb = new JsonObject();
                    hb.addProperty("op", 1);
                    hb.add( "d", lastSequence != null ? JsonParser.parseString(lastSequence) : com.google.gson.JsonNull.INSTANCE);
                    ws.sendText(hb.toString(), true);
                }, interval, interval, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOGGER.error("RED-OS: Failed to start Discord heartbeat", e);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            isConnected = false;
            LOGGER.warn("RED-OS: Discord connection closed! Code: {} | Reason: {}", statusCode, reason);
            
            // Critical codes: 4004 = Bad Token, 4014 = Missing Intents
            if (statusCode == 4004) LOGGER.error("RED-OS: CRITICAL ERROR - Your Discord Bot Token is invalid!");
            if (statusCode == 4014) LOGGER.error("RED-OS: CRITICAL ERROR - Message Content Intent is not enabled in Discord Portal!");

            if (DiscordConfig.get().enabled && statusCode != 4004) {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.schedule(DiscordBridge::connect, 10, TimeUnit.SECONDS);
                }
            }
            return WebSocket.Listener.super.onClose(ws, statusCode, reason);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            LOGGER.error("RED-OS: Discord WebSocket Error", error);
        }
    }
}
