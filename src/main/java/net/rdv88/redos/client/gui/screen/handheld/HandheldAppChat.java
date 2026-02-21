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
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static int scrollOffset = 0;
    private static long lastRequestTime = 0;
    private static boolean isDataReceived = false;
    private static final List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> clientHistory = new ArrayList<>();
    
    private static EditBox chatInput;

    public static void updateHistory(List<net.rdv88.redos.network.payload.SyncChatHistoryPayload.ChatEntry> history) {
        clientHistory.clear();
        clientHistory.addAll(history);
        isDataReceived = true;
        HandheldScreen.refreshApp();
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        // Throttled sync: only request if we haven't in the last 5 seconds
        if (System.currentTimeMillis() - lastRequestTime > 5000) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.RequestChatSyncPayload());
            lastRequestTime = System.currentTimeMillis();
            isDataReceived = false;
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
            chatInput.setBordered(false);
            chatInput.setTextColor(0xFFFFFFFF);
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
        
        // Return to simple input underline
        g.fill(sx + 5, sy + h - 21, sx + w - 5, sy + h - 20, 0xFF440000);

        // Persistent Hint logic
        if (chatInput != null && chatInput.getValue().isEmpty()) {
            g.drawString(font, "Type message...", sx + 7, sy + h - 35, 0xFF444444, false);
        }

        if (currentTab == Tab.GENERAL) {
            if (clientHistory.isEmpty()) {
                int cx = sx + w / 2;
                String status = isDataReceived ? "Welcome to RED-OS CHAT" : "[ CONNECTING TO NETWORK... ]";
                g.drawCenteredString(font, status, cx, sy + 80, 0xFF444444);
            } else {
                int chatY = (int)(sy + h - 50 + scrollPos); 
                int maxTextW = w - 26; // Reduced by 6 pixels to clear scrollbar
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
                        
                        if (j == 0) {
                            // First line: Draw RED prefix and WHITE message separately to avoid overlap
                            float pScale = 0.8f;
                            int pWidth = (int)(font.width(pFix) * pScale) + 2;
                            
                            // 1. Draw small RED prefix
                            org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
                            g.pose().get(oldM);
                            g.pose().translate(sx + 8, chatY + 1);
                            g.pose().scale(pScale, pScale);
                            g.drawString(font, "§c" + pFix, 0, 0, 0xFFFFFFFF, false);
                            g.pose().set(oldM);

                            // 2. Draw normal WHITE message (starting AFTER the prefix width)
                            StringBuilder sb = new StringBuilder();
                            wrappedLines.get(j).accept((index, style, codePoint) -> {
                                sb.append(Character.toChars(codePoint));
                                return true;
                            });
                            String lineContent = sb.toString();
                            if (lineContent.startsWith(pFix)) {
                                String msgPart = lineContent.substring(pFix.length());
                                g.drawString(font, "§f" + msgPart, sx + 8 + pWidth, chatY, 0xFFFFFFFF, false);
                            }
                        } else {
                            // Subsequent lines: Normal full-width white text
                            g.drawString(font, wrappedLines.get(j), sx + 8, chatY, 0xFFFFFFFF, false);
                        }
                        chatY -= 10;
                    }
                    chatY -= 2; 
                }
                g.disableScissor();

                // Draw Scrollbar (Matches Home App style)
                if (clientHistory.size() > 5) {
                    int barX = sx + w - 4;
                    int barY = sy + 45;
                    int barH = h - 90;
                    g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
                    
                    int totalH = calculateTotalHeight(maxTextW);
                    double maxScroll = Math.max(1, totalH - 100); 
                    int handleH = 20;
                    int handleY = (int)(barY + barH - handleH - (scrollPos / maxScroll) * (barH - handleH));
                    handleY = Math.clamp(handleY, barY, barY + barH - handleH);
                    
                    g.fill(barX - 1, handleY, barX + 2, handleY + handleH, 0xAAFFFFFF); 
                }
            }
        } else {
            int cx = sx + w / 2;
            String status = "CONNECTING TO " + currentTab.name() + "...";
            g.drawCenteredString(font, status, cx, sy + 80, 0xFF444444);
        }
    }

        @Override
        public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
            // Dynamic scroll limit based on actual content height at this moment
            int maxW = 160;
            int totalH = calculateTotalHeight(maxW);
            double maxScroll = Math.max(0, totalH - 95); // 95 is approximate chat window height
    
            // Clamp targetScroll to ensure we never scroll past the beginning
            targetScroll = Math.clamp(targetScroll, 0, maxScroll);
    
            // SMOOTH SCROLL ANIMATION
            if (Math.abs(scrollPos - targetScroll) > 0.1) {
                scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
            } else {
                scrollPos = targetScroll;
            }
        }
    
        @Override public void tick() {}
    
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
        
    private int calculateTotalHeight(int maxW) {
        int total = 0;
        int textW = maxW - 6; 
        for (var entry : clientHistory) {
            String fullText = "[" + new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(entry.timestamp())) + "] " + entry.sender() + ": " + entry.message();
            total += font.split(Component.literal(fullText), textW).size() * 10 + 2; 
        }
        return total;
    }        @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) { 
            // Simple input, clamping happens in preRender for robustness
            targetScroll += (v * 20); 
            HandheldScreen.refreshApp(); 
            return true; 
        }
    
    @Override public boolean isEditMode() { return chatInput != null && chatInput.isFocused(); }
    @Override public Optional<GuiEventListener> getInitialFocus() { return Optional.ofNullable(chatInput); }
    
    public static void clearState() { currentTab = Tab.GENERAL; scrollOffset = 0; }
}