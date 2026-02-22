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
            int px = sx + 12; int py = sy + 50; int count = 0;
            for (var p : players) {
                String name = p.getProfile().name();
                if (name.equals(Minecraft.getInstance().player.getName().getString())) continue;
                adder.add(new PlayerButton(px, py, 36, 36, name, b -> {
                    selectedPlayerName = name; targetScroll = 0; HandheldScreen.refreshApp();
                }));
                count++;
                if (count % 4 == 0) { px = sx + 12; py += 48; } else { px += 44; }
            }
        } else if (currentTab == Tab.PRIVATE && selectedPlayerName != null) {
            adder.add(new HandheldScreen.NavButton(sx + w - 25, sy + 30, 20, 12, "<-", b -> {
                selectedPlayerName = null; targetScroll = 0; HandheldScreen.refreshApp();
            }, 0xFF444444));
        }

        if (chatInput == null) {
            chatInput = new EditBox(font, sx + 5, sy + h - 35, w - 10, 14, Component.literal("ChatInput"));
            chatInput.setMaxLength(100);
            chatInput.setBordered(false);
            chatInput.setTextColor(0xFFFFFFFF);
        } else {
            chatInput.setX(sx + 5); chatInput.setY(sy + h - 35); chatInput.setWidth(w - 10);
        }
        adder.add(chatInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        g.drawString(font, "> CHAT ENGINE", sx + 5, sy + 18, 0xFFAA0000, false);
        g.fill(sx + 5, sy + h - 21, sx + w - 5, sy + h - 20, 0xFF440000);

        if (chatInput != null && chatInput.getValue().isEmpty()) {
            String hint = (currentTab == Tab.PRIVATE && selectedPlayerName == null) ? "Select a player..." : "Type message...";
            g.drawString(font, hint, sx + 7, sy + h - 35, 0xFF444444, false);
        }

        if (currentTab == Tab.GENERAL) {
            if (clientHistory.isEmpty()) {
                int cx = sx + w / 2;
                String status = isDataReceived ? "Welcome to RED-OS Chat" : "[ CONNECTING TO NETWORK... ]";
                g.drawCenteredString(font, status, cx, sy + 80, 0xFF444444);
            } else {
                int chatY = (int)(sy + h - 50 + scrollPos); 
                int maxTextW = w - 26; 
                int minY = sy + 45;
                g.enableScissor(sx, minY, sx + w, sy + h - 40); 
                var timeFormat = new java.text.SimpleDateFormat("HH:mm");

                for (int i = clientHistory.size() - 1; i >= 0; i--) {
                    var entry = clientHistory.get(i);
                    String time = timeFormat.format(new java.util.Date(entry.timestamp()));
                    String pFix = "[" + time + "] " + entry.sender() + ": ";
                    String message = entry.message();
                    
                    var wrappedLines = font.split(Component.literal(pFix + message), maxTextW);

                    for (int j = wrappedLines.size() - 1; j >= 0; j--) {
                        if (chatY < minY - 10) break;
                        g.drawString(font, wrappedLines.get(j), sx + 8, chatY, 0xFFFFFFFF, false);
                        if (j == 0) {
                            float pScale = 0.8f;
                            int pWidth = (int)(font.width(pFix) * pScale) + 2;
                            StringBuilder sb = new StringBuilder();
                            wrappedLines.get(j).accept((idx, style, cp) -> { sb.append(Character.toChars(cp)); return true; });
                            String lineContent = sb.toString();
                            g.fill(sx + 7, chatY - 1, sx + 8 + pWidth, chatY + 9, 0xFF100505);
                            org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
                            g.pose().get(oldM);
                            g.pose().translate(sx + 8, chatY + 1);
                            g.pose().scale(pScale, pScale);
                            g.drawString(font, "§c" + pFix, 0, 0, 0xFFFFFFFF, false);
                            g.pose().set(oldM);
                        }
                        chatY -= 10;
                    }
                    chatY -= 2;
                }
                g.disableScissor();
                drawScrollbar(g, sx, sy, w, h, clientHistory.size(), maxTextW);
            }
        } else if (currentTab == Tab.PRIVATE) {
            if (selectedPlayerName == null) {
                g.drawString(font, "ONLINE PLAYERS:", sx + 8, sy + 35, 0xFF888888, false);
            } else {
                g.drawString(font, "CHAT WITH: " + selectedPlayerName, sx + 8, sy + 35, 0xFFAA0000, false);
                var filtered = privateHistory.stream()
                    .filter(e -> e.from().equals(selectedPlayerName) || e.to().equals(selectedPlayerName))
                    .map(e -> new net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry(e.from(), e.message(), e.timestamp()))
                    .toList();
                renderPrivateConversation(g, sx, sy, w, h, filtered);
            }
        } else {
            int cx = sx + w / 2;
            g.drawCenteredString(font, "CONNECTING TO " + currentTab.name() + "...", cx, sy + 80, 0xFF444444);
        }
    }

    private void renderPrivateConversation(GuiGraphics g, int sx, int sy, int w, int h, List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> entries) {
        if (entries.isEmpty()) {
            int cx = sx + w / 2;
            g.drawCenteredString(font, "Start a conversation...", cx, sy + 80, 0xFF444444);
            return;
        }

        int chatY = (int)(sy + h - 50 + scrollPos); 
        int maxTextW = w - 26;
        int minY = sy + 45;
        g.enableScissor(sx, minY, sx + w, sy + h - 40); 
        var timeFormat = new java.text.SimpleDateFormat("HH:mm");

        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            String time = timeFormat.format(new java.util.Date(entry.timestamp()));
            String pFix = "[" + time + "] " + entry.sender() + ": ";
            String message = entry.message();
            
            var wrappedLines = font.split(Component.literal(pFix + message), maxTextW);

            for (int j = wrappedLines.size() - 1; j >= 0; j--) {
                if (chatY < minY - 10) break;
                g.drawString(font, wrappedLines.get(j), sx + 8, chatY, 0xFFFFFFFF, false);
                if (j == 0) {
                    float pScale = 0.8f;
                    int pWidth = (int)(font.width(pFix) * pScale) + 2;
                    StringBuilder sb = new StringBuilder();
                    wrappedLines.get(j).accept((idx, style, cp) -> { sb.append(Character.toChars(cp)); return true; });
                    String lineContent = sb.toString();
                    g.fill(sx + 7, chatY - 1, sx + 8 + pWidth, chatY + 9, 0xFF100505);
                    org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
                    g.pose().get(oldM);
                    g.pose().translate(sx + 8, chatY + 1);
                    g.pose().scale(pScale, pScale);
                    g.drawString(font, "§c" + pFix, 0, 0, 0xFFFFFFFF, false);
                    g.pose().set(oldM);
                }
                chatY -= 10;
            }
            chatY -= 2;
        }
        g.disableScissor();
        drawScrollbar(g, sx, sy, w, h, entries.size(), maxTextW);
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int w, int h, int count, int maxTextW) {
        if (count > 5) {
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
        List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> targetList;
        if (currentTab == Tab.GENERAL) {
            targetList = clientHistory;
        } else {
            targetList = privateHistory.stream()
                .filter(e -> e.from().equals(selectedPlayerName) || e.to().equals(selectedPlayerName))
                .map(e -> new net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry(e.from(), e.message(), e.timestamp()))
                .toList();
        }

        for (var entry : targetList) {
            String pFix = "[" + new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(entry.timestamp())) + "] " + entry.sender() + ": ";
            int pWidth = (int)(font.width(pFix) * 0.8f) + 2;
            int msgSpace = maxW - pWidth; 
            total += font.split(Component.literal(entry.message()), msgSpace).size() * 10 + 2; 
        }
        return total;
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
        if (chatInput != null && chatInput.isFocused()) {
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
    @Override public boolean isEditMode() { return chatInput != null && chatInput.isFocused(); }
    @Override public Optional<GuiEventListener> getInitialFocus() { return Optional.ofNullable(chatInput); }
    public static void clearState() { currentTab = Tab.GENERAL; selectedPlayerName = null; }

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