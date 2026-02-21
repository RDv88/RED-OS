package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HandheldAppChat implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    
    private enum Tab { GENERAL, PRIVATE, DISCORD }
    private static Tab currentTab = Tab.GENERAL;
    private static int scrollOffset = 0;
    private static long lastRequestTime = 0;
    private static final List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> clientHistory = new ArrayList<>();
    
    private static EditBox chatInput;

    public static void updateHistory(List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> history) {
        clientHistory.clear();
        clientHistory.addAll(history);
        HandheldScreen.refreshApp();
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        // Throttled sync: only request if we haven't in the last 5 seconds
        if (System.currentTimeMillis() - lastRequestTime > 5000) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.RequestChatSyncPayload());
            lastRequestTime = System.currentTimeMillis();
        }

        int tabW = (w - 6) / 3;
        
        // TABS
        adder.add(new HandheldScreen.NavButton(sx + 2, sy + 14, tabW, 14, "GENERAL", b -> {
            currentTab = Tab.GENERAL; scrollOffset = 0; 
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.RequestChatSyncPayload());
            HandheldScreen.refreshApp();
        }, currentTab == Tab.GENERAL ? 0xFF880000 : 0xFF222222));

        adder.add(new HandheldScreen.NavButton(sx + 2 + tabW + 1, sy + 14, tabW, 14, "PRIVATE", b -> {
            currentTab = Tab.PRIVATE; scrollOffset = 0; HandheldScreen.refreshApp();
        }, currentTab == Tab.PRIVATE ? 0xFF880000 : 0xFF222222));

        adder.add(new HandheldScreen.NavButton(sx + 2 + (tabW + 1) * 2, sy + 14, tabW, 14, "DISCORD", b -> {
            currentTab = Tab.DISCORD; scrollOffset = 0; HandheldScreen.refreshApp();
        }, currentTab == Tab.DISCORD ? 0xFF880000 : 0xFF222222));

        // PERSISTENT Chat Input Field
        if (chatInput == null) {
            chatInput = new EditBox(font, sx + 5, sy + h - 35, w - 10, 14, Component.literal("ChatInput"));
            chatInput.setMaxLength(100);
            chatInput.setHint(Component.literal("Type message..."));
            chatInput.setBordered(false);
        } else {
            // Update position for existing box
            chatInput.setX(sx + 5);
            chatInput.setY(sy + h - 35);
            chatInput.setWidth(w - 10);
        }
        adder.add(chatInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        g.drawString(font, "> CHAT ENGINE", sx + 5, sy + 32, 0xFFAA0000, false);
        
        if (currentTab == Tab.GENERAL) {
            if (clientHistory.isEmpty()) {
                int cx = sx + w / 2;
                g.drawCenteredString(font, "[ FETCHING HISTORY... ]", cx, sy + 80, 0xFF444444);
            } else {
                int chatY = sy + h - 50;
                int maxTextW = w - 20;

                // Render messages from newest to oldest
                for (int i = clientHistory.size() - 1 - scrollOffset; i >= 0; i--) {
                    if (chatY < sy + 45) break;
                    var entry = clientHistory.get(i);
                    String fullText = "§c" + entry.sender() + ": §f" + entry.message();
                    
                    var wrappedLines = font.split(Component.literal(fullText), maxTextW);
                    for (int j = wrappedLines.size() - 1; j >= 0; j--) {
                        if (chatY < sy + 45) break;
                        g.drawString(font, wrappedLines.get(j), sx + 8, chatY, 0xFFFFFFFF, false);
                        chatY -= 10;
                    }
                    chatY -= 2;
                }
            }
        } else {
            int cx = sx + w / 2;
            String status = "CONNECTING TO " + currentTab.name() + "...";
            g.drawCenteredString(font, status, cx, sy + 80, 0xFF444444);
        }
        
        // Input background line
        g.fill(sx + 5, sy + h - 21, sx + w - 5, sy + h - 20, 0xFF440000);
    }

    @Override public void tick() {
        // EditBox handles its own ticking in 1.21.11
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (chatInput != null && chatInput.isFocused()) {
            if (event.key() == 257 || event.key() == 335) { // ENTER
                sendMessage();
                return true;
            }
            return chatInput.keyPressed(event);
        }
        return false;
    }

    public void sendMessage() {
        if (chatInput != null && !chatInput.getValue().isEmpty()) {
            String msg = chatInput.getValue();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.SendChatMessagePayload(msg));
            chatInput.setValue("");
        }
    }

    public void back() {
        HandheldScreen.requestAppSwitch("HOME");
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) { 
        if (v < 0) scrollOffset++; else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        HandheldScreen.refreshApp(); return true; 
    }
    
    @Override public boolean isEditMode() { return chatInput != null && chatInput.isFocused(); }
    @Override public Optional<GuiEventListener> getInitialFocus() { return Optional.ofNullable(chatInput); }
    
    public static void clearState() { currentTab = Tab.GENERAL; scrollOffset = 0; }
}