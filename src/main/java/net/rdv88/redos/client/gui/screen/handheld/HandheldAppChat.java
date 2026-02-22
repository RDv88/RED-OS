package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HandheldAppChat implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    
    private enum Tab { GENERAL, PRIVATE, DISCORD }
    private static Tab currentTab = Tab.GENERAL;
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static long lastRequestTime = 0;
    private static boolean isDataReceived = false;
    private static String selectedPlayerName = null; 
    
    private static final List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> clientHistory = new ArrayList<>();
    private static final List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.PrivateEntry> privateHistory = new ArrayList<>();
    
    private static EditBox chatInput;

    public static void updateHistory(List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> history, List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.PrivateEntry> pHistory) {
        clientHistory.clear();
        clientHistory.addAll(history);
        privateHistory.clear();
        privateHistory.addAll(pHistory);
        isDataReceived = true;
        HandheldScreen.refreshApp();
    }

    private static HandheldAppChat currentInstance;

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        currentInstance = this;
        if (System.currentTimeMillis() - lastRequestTime > 5000) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.RequestChatSyncPayload());
            lastRequestTime = System.currentTimeMillis();
            isDataReceived = false;
        }

        int tabW = (w - 6) / 3;
        
        adder.add(new HandheldScreen.NavButton(sx + 2, sy + 14, tabW, 14, "GENERAL", b -> {
            currentTab = Tab.GENERAL; targetScroll = 0; selectedPlayerName = null; HandheldScreen.refreshApp();
        }, currentTab == Tab.GENERAL ? 0xFF880000 : 0xFF222222));

        adder.add(new HandheldScreen.NavButton(sx + 2 + tabW + 1, sy + 14, tabW, 14, "PRIVATE", b -> {
            currentTab = Tab.PRIVATE; targetScroll = 0; HandheldScreen.refreshApp();
        }, currentTab == Tab.PRIVATE ? 0xFF880000 : 0xFF222222));

        adder.add(new HandheldScreen.NavButton(sx + 2 + (tabW + 1) * 2, sy + 14, tabW, 14, "DISCORD", b -> {
            currentTab = Tab.DISCORD; targetScroll = 0; selectedPlayerName = null; HandheldScreen.refreshApp();
        }, currentTab == Tab.DISCORD ? 0xFF880000 : 0xFF222222));

        if (currentTab == Tab.PRIVATE && selectedPlayerName == null) {
            var players = Minecraft.getInstance().getConnection().getOnlinePlayers();
            int px = sx + 12; int py = sy + 50 - (int)scrollPos; int count = 0;
            for (var p : players) {
                String name = p.getProfile().name();
                if (name.equals(Minecraft.getInstance().player.getName().getString())) continue;
                PlayerButton btn = new PlayerButton(px, py, 36, 36, name, b -> {
                    selectedPlayerName = name; targetScroll = 0; scrollPos = 0; HandheldScreen.refreshApp();
                });
                if (py > sy + 40 && py < sy + h - 45) adder.add(btn);
                count++;
                if (count % 4 == 0) { px = sx + 12; py += 48; } else { px += 44; }
            }
        } else if (currentTab == Tab.PRIVATE && selectedPlayerName != null) {
            adder.add(new HandheldScreen.NavButton(sx + w - 25, sy + 30, 20, 12, "<-", b -> {
                selectedPlayerName = null; targetScroll = 0; HandheldScreen.refreshApp();
            }, 0xFF444444));
        }

        boolean showInput = (currentTab == Tab.GENERAL) || (selectedPlayerName != null);
        if (chatInput == null) {
            chatInput = new EditBox(font, sx + 5, sy + h - 35, w - 10, 14, Component.literal("ChatInput"));
            chatInput.setMaxLength(100);
            chatInput.setBordered(false);
            chatInput.setTextColor(0xFFFFFFFF);
        } else {
            chatInput.setX(sx + 5); chatInput.setY(sy + h - 35); chatInput.setWidth(w - 10);
        }
        chatInput.setVisible(showInput);
        adder.add(chatInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int onlineCount = Minecraft.getInstance().getConnection().getOnlinePlayers().size();
        
        if (chatInput != null && chatInput.isVisible()) {
            g.fill(sx + 5, sy + h - 21, sx + w - 5, sy + h - 20, 0xFF440000);
            if (chatInput.getValue().isEmpty()) {
                g.drawString(font, "Type message...", sx + 7, sy + h - 35, 0xFF444444, false);
            }
        }

        if (currentTab == Tab.GENERAL) {
            g.drawString(font, "ONLINE PLAYERS: [" + onlineCount + "]", sx + 8, sy + 35, 0xFF888888, false);
            drawChatContent(g, sx, sy, w, h, true);
        } else if (currentTab == Tab.PRIVATE) {
            if (selectedPlayerName == null) {
                g.drawString(font, "ONLINE PLAYERS: [" + (onlineCount - 1) + "]", sx + 8, sy + 35, 0xFF888888, false);
                drawScrollbar(g, sx, sy, w, h, onlineCount - 1, w - 26);
            } else {
                g.drawString(font, "CHAT WITH: " + selectedPlayerName, sx + 8, sy + 35, 0xFFAA0000, false);
                drawChatContent(g, sx, sy, w, h, false);
            }
        } else {
            int cx = sx + w / 2;
            g.drawCenteredString(font, "CONNECTING TO " + currentTab.name() + "...", cx, sy + 80, 0xFF444444);
        }
    }

    private void drawChatContent(GuiGraphics g, int sx, int sy, int w, int h, boolean general) {
        var entries = general ? clientHistory.stream().map(e -> new DisplayEntry(e.sender(), e.message(), e.timestamp())).toList()
                             : privateHistory.stream().filter(e -> e.from().equals(selectedPlayerName) || e.to().equals(selectedPlayerName))
                                             .map(e -> new DisplayEntry(e.from(), e.message(), e.timestamp())).toList();

        if (entries.isEmpty()) {
            int cx = sx + w / 2;
            String status = isDataReceived ? "Welcome to RED-OS Chat" : "[ CONNECTING... ]";
            g.drawCenteredString(font, status, cx, sy + 80, 0xFF444444);
            return;
        }

        int chatY = (int)(sy + h - 50 + scrollPos); 
        int maxTextW = w - 26;
        float mScale = 0.95f; // Message scale
        int minY = sy + 45;
        g.enableScissor(sx, minY, sx + w, sy + h - 40); 
        var timeFormat = new java.text.SimpleDateFormat("dd-MM HH:mm");

        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            String namePart = entry.sender() + ": ";
            String message = "§f" + entry.message(); // Color code added BEFORE wrapping
            String time = timeFormat.format(new java.util.Date(entry.timestamp()));
            
            // Adjust wrapping for smaller text: more text fits in the same width
            var wrappedLines = font.split(Component.literal(namePart + message), (int)(maxTextW / mScale));

            // 1. Draw Timestamp (Footer)
            if (chatY > minY - 10) {
                org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
                g.pose().get(oldM);
                float tScale = 0.5f;
                g.pose().translate(sx + 8, chatY + 2);
                g.pose().scale(tScale, tScale);
                g.drawString(font, time, 0, 0, 0xFF666666, false);
                g.pose().set(oldM);
            }
            chatY -= 7;

            // 2. Draw Message Lines
            for (int j = wrappedLines.size() - 1; j >= 0; j--) {
                if (chatY < minY - 10) break;
                
                if (j == 0) {
                    float pScale = 0.8f;
                    int pWidth = (int)(font.width(namePart) * pScale) + 2;
                    int pColor = getPlayerColor(entry.sender());
                    
                    StringBuilder sb = new StringBuilder();
                    wrappedLines.get(j).accept((idx, style, cp) -> { sb.append(Character.toChars(cp)); return true; });
                    String lineContent = sb.toString();

                    // Draw Name (0.8f)
                    org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
                    g.pose().get(oldM);
                    g.pose().translate(sx + 8, chatY + 1);
                    g.pose().scale(pScale, pScale);
                    g.drawString(font, namePart, 0, 0, pColor, false);
                    g.pose().set(oldM);

                    // Draw Message part (0.95f)
                    if (lineContent.startsWith(namePart)) {
                        String msgPart = lineContent.substring(namePart.length());
                        org.joml.Matrix3x2f msgM = new org.joml.Matrix3x2f();
                        g.pose().get(msgM);
                        g.pose().translate(sx + 8 + pWidth, chatY + 0.5f);
                        g.pose().scale(mScale, mScale);
                        g.drawString(font, msgPart, 0, 0, 0xFFFFFFFF, false);
                        g.pose().set(msgM);
                    }
                } else {
                    // Subsequent lines (0.95f)
                    org.joml.Matrix3x2f msgM = new org.joml.Matrix3x2f();
                    g.pose().get(msgM);
                    g.pose().translate(sx + 8, chatY + 0.5f);
                    g.pose().scale(mScale, mScale);
                    g.drawString(font, wrappedLines.get(j), 0, 0, 0xFFFFFFFF, false);
                    g.pose().set(msgM);
                }
                chatY -= 9; 
            }
            chatY -= 1; 
        }
        g.disableScissor();
        drawScrollbar(g, sx, sy, w, h, entries.size(), maxTextW);
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int w, int h, int count, int maxTextW) {
        if (count > 3) {
            int barX = sx + w - 4; int barY = sy + 45; int barH = h - 90;
            g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
            int totalH = calculateTotalHeight(maxTextW);
            double maxScroll = Math.max(1, totalH - 100); 
            int handleH = 20;
            int handleY = (int)(barY + barH - handleH - (scrollPos / maxScroll) * (barH - handleH));
            g.fill(barX - 1, Math.clamp(handleY, barY, barY + barH - handleH), barX + 2, handleY + handleH, 0xAAFFFFFF); 
        }
    }

    private int calculateTotalHeight(int maxW) {
        int total = 0;
        float mScale = 0.95f;
        int onlineCount = Minecraft.getInstance().getConnection().getOnlinePlayers().size();
        if (currentTab == Tab.GENERAL) {
            for (var entry : clientHistory) {
                total += font.split(Component.literal(entry.sender() + ": " + entry.message()), (int)(maxW / mScale)).size() * 9 + 10;
            }
        } else if (currentTab == Tab.PRIVATE) {
            if (selectedPlayerName == null) {
                int pCount = onlineCount - 1;
                if (pCount <= 0) return 0;
                return (int)Math.ceil(pCount / 4.0) * 48 + 60;
            } else {
                var filtered = privateHistory.stream().filter(e -> e.from().equals(selectedPlayerName) || e.to().equals(selectedPlayerName)).toList();
                for (var entry : filtered) {
                    total += font.split(Component.literal(entry.from() + ": " + entry.message()), (int)(maxW / mScale)).size() * 9 + 10;
                }
            }
        }
        return total;
    }

    private int getPlayerColor(String name) {
        int hash = name.hashCode();
        float hue = (Math.abs(hash) % 360) / 360.0f;
        int color = net.minecraft.util.Mth.hsvToRgb(hue, 0.7f, 0.9f);
        return 0xFF000000 | color;
    }

    @Override
    public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int totalH = calculateTotalHeight(w - 26);
        double maxScroll = Math.max(0, totalH - 95);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
        else scrollPos = targetScroll;
    }

    @Override public void tick() {}
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (chatInput != null && chatInput.isVisible() && chatInput.isFocused()) {
            if (event.key() == 257 || event.key() == 335) { sendMessage(); return true; }
            return chatInput.keyPressed(event);
        }
        return false;
    }

    public void sendMessage() {
        if (chatInput != null && !chatInput.getValue().isEmpty()) {
            String msg = chatInput.getValue();
            if (currentTab == Tab.PRIVATE && selectedPlayerName != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.SendPrivateMessagePayload(selectedPlayerName, msg));
            } else {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.SendChatMessagePayload(msg));
            }
            chatInput.setValue("");
        }
    }

    public void back() { HandheldScreen.requestAppSwitch("HOME"); }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) { 
        targetScroll += (v * 20); HandheldScreen.refreshApp(); return true; 
    }
    @Override public boolean isEditMode() { return chatInput != null && chatInput.isVisible() && chatInput.isFocused(); }
    @Override public Optional<GuiEventListener> getInitialFocus() { return Optional.ofNullable(chatInput); }
    public static void clearState() { currentTab = Tab.GENERAL; selectedPlayerName = null; }

    private record DisplayEntry(String sender, String message, long timestamp) {}

    private class PlayerButton extends Button {
        private final String playerName;
        public PlayerButton(int x, int y, int w, int h, String name, OnPress press) {
            super(x, y, w, h, Component.empty(), press, DEFAULT_NARRATION);
            this.playerName = name;
        }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int color = isHovered() ? 0xFF441111 : 0xFF220505;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.renderItem(new ItemStack(Items.PLAYER_HEAD), getX() + (width - 16) / 2, getY() + 4);
            org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
            g.pose().get(oldM);
            float scale = 0.7f;
            int labelX = getX() + (width - (int)(font.width(playerName) * scale)) / 2;
            g.pose().translate(labelX, getY() + height - 8);
            g.pose().scale(scale, scale);
            g.drawString(font, playerName, 0, 0, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            g.pose().set(oldM);
        }
    }
}