package net.rdv88.redos.client.gui.screen.handheld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.AdminActionPayload;
import net.rdv88.redos.network.payload.SyncSystemInfoPayload;
import org.joml.Matrix3x2f;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public class HandheldAppAdmin implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    
    private enum View { MENU, PLAYERS, WORLD, SYSTEM, DISCORD }
    private static View currentView = View.MENU;

    // Cache for system info
    private static double currentTps = 20.0;
    private static int droneCount = 0;
    private static int nodeCount = 0;
    private static String ramInfo = "0 / 0 MB";
    private static boolean discordOnline = false;
    
    public static boolean isDiscordOnline() { return discordOnline; }

    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;

    private EditBox genericInput;
    private EditBox genericInput2;
    private int requestTimer = 0;

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        switch (currentView) {
            case MENU -> setupMenu(sx, sy, w, h, adder);
            case PLAYERS -> setupPlayers(sx, sy, w, h, adder);
            case WORLD -> setupWorld(sx, sy, w, h, adder);
            case SYSTEM -> setupSystem(sx, sy, w, h, adder);
            case DISCORD -> setupDiscord(sx, sy, w, h, adder);
        }
    }

    private void setupMenu(int sx, int sy, int w, int h, WidgetAdder adder) {
        int size = 32;
        int gap = 10;
        int totalW = (4 * size) + (3 * gap);
        int startX = sx + (w - totalW) / 2;
        int btnY = sy + 65;
        
        adder.add(new IconButton(startX, btnY, size, size, new ItemStack(Items.PLAYER_HEAD), "Players", View.PLAYERS));
        adder.add(new IconButton(startX + size + gap, btnY, size, size, new ItemStack(Items.CLOCK), "World", View.WORLD));
        adder.add(new IconButton(startX + 2 * (size + gap), btnY, size, size, new ItemStack(Items.COMPARATOR), "System", View.SYSTEM));
        adder.add(new IconButton(startX + 3 * (size + gap), btnY, size, size, new ItemStack(Items.CYAN_WOOL), "Discord", View.DISCORD));
    }

    private void setupPlayers(int sx, int sy, int w, int h, WidgetAdder adder) {
        List<PlayerInfo> players = Minecraft.getInstance().getConnection().getOnlinePlayers().stream()
            .filter(p -> !p.getProfile().name().equals(Minecraft.getInstance().player.getName().getString()))
            .toList();
        int listY = sy + 32;
        
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo p = players.get(i);
            String name = p.getProfile().name();
            int rowY = (int)(listY + (i * 55) - scrollPos);
            
            if (rowY > sy + 5 && rowY < sy + 152) {
                // Row 1: TP
                adder.add(new CommandButton(sx + w - 45, rowY + 2, 35, 12, "TP", 0xFF0055AA, sy + 30, sy + 152, b -> sendAction(AdminActionPayload.ActionType.TP_TO_PLAYER, name, "")));
                
                // Row 2: Punish
                int bx = sx + 10;
                adder.add(new CommandButton(bx, rowY + 18, 35, 12, "KICK", 0xFFCC0000, sy + 30, sy + 152, b -> sendAction(AdminActionPayload.ActionType.KICK_PLAYER, name, "Admin Action")));
                adder.add(new CommandButton(bx + 38, rowY + 18, 35, 12, "KILL", 0xFFFF0000, sy + 30, sy + 152, b -> sendAction(AdminActionPayload.ActionType.KILL_PLAYER, name, "")));
                adder.add(new CommandButton(bx + 76, rowY + 18, 35, 12, "BAN", 0xFF440000, sy + 30, sy + 152, b -> sendAction(AdminActionPayload.ActionType.BAN_PLAYER, name, "Banned")));
                
                // Row 3: Rights
                int rx = sx + w - 83;
                adder.add(new CommandButton(rx, rowY + 34, 35, 12, "OP", 0xFF00AA00, sy + 30, sy + 152, b -> sendAction(AdminActionPayload.ActionType.OP_PLAYER, name, "")));
                adder.add(new CommandButton(rx + 38, rowY + 34, 35, 12, "DEOP", 0xFFAA0000, sy + 30, sy + 152, b -> sendAction(AdminActionPayload.ActionType.DEOP_PLAYER, name, "")));
            }
        }
    }

    private void setupWorld(int sx, int sy, int w, int h, WidgetAdder adder) {
        // Time Controls
        adder.add(new CommandButton(sx + 10, sy + 45, 45, 14, "DAWN", 0xFF884400, b -> sendAction(AdminActionPayload.ActionType.SET_TIME, "0", "")));
        adder.add(new CommandButton(sx + 60, sy + 45, 45, 14, "NOON", 0xFFCCAA00, b -> sendAction(AdminActionPayload.ActionType.SET_TIME, "6000", "")));
        adder.add(new CommandButton(sx + 110, sy + 45, 45, 14, "DUSK", 0xFF442266, b -> sendAction(AdminActionPayload.ActionType.SET_TIME, "12500", "")));
        adder.add(new CommandButton(sx + 160, sy + 45, 45, 14, "NIGHT", 0xFF111144, b -> sendAction(AdminActionPayload.ActionType.SET_TIME, "18000", "")));
        
        // Weather Controls
        int wy = sy + 85;
        adder.add(new CommandButton(sx + 10, wy, 60, 14, "SUNNY", 0xFF008800, b -> sendAction(AdminActionPayload.ActionType.SET_WEATHER, "CLEAR", "")));
        adder.add(new CommandButton(sx + 75, wy, 60, 14, "RAIN", 0xFF000088, b -> sendAction(AdminActionPayload.ActionType.SET_WEATHER, "RAIN", "")));
        adder.add(new CommandButton(sx + 140, wy, 60, 14, "STORM", 0xFF440044, b -> sendAction(AdminActionPayload.ActionType.SET_WEATHER, "THUNDER", "")));

        // Difficulty Row
        int dy = sy + 115;
        adder.add(new CommandButton(sx + 10, dy, 40, 12, "PEACE", 0xFF00AA00, b -> sendAction(AdminActionPayload.ActionType.SET_DIFFICULTY, "peaceful", "")));
        adder.add(new CommandButton(sx + 55, dy, 40, 12, "EASY", 0xFF55AA00, b -> sendAction(AdminActionPayload.ActionType.SET_DIFFICULTY, "easy", "")));
        adder.add(new CommandButton(sx + 100, dy, 40, 12, "NORM", 0xFFAA5500, b -> sendAction(AdminActionPayload.ActionType.SET_DIFFICULTY, "normal", "")));
        adder.add(new CommandButton(sx + 145, dy, 40, 12, "HARD", 0xFFAA0000, b -> sendAction(AdminActionPayload.ActionType.SET_DIFFICULTY, "hard", "")));
    }

    private void setupSystem(int sx, int sy, int w, int h, WidgetAdder adder) {
        genericInput = new EditBox(font, sx + 20, sy + 125, w - 40, 14, Component.literal("Broadcast Message"));
        genericInput.setMaxLength(100);
        adder.add(genericInput);
    }

    private void setupDiscord(int sx, int sy, int w, int h, WidgetAdder adder) {
        boolean active = discordOnline;
        String btnText = active ? "BRIDGE: ON" : "BRIDGE: OFF";
        int btnCol = active ? 0xFF00AA22 : 0xFF880000;

        genericInput = new EditBox(font, sx + 75, sy + 33, w - 85, 12, Component.literal(""));
        genericInput.setMaxLength(100);
        adder.add(genericInput);
        
        genericInput2 = new EditBox(font, sx + 75, sy + 58, w - 85, 12, Component.literal(""));
        genericInput2.setMaxLength(30);
        adder.add(genericInput2);

        adder.add(new CommandButton(sx + 10, sy + 85, 80, 14, btnText, btnCol, b -> sendAction(AdminActionPayload.ActionType.RELOAD_CONFIG, "TOGGLE_DISCORD", "")));
        adder.add(new CommandButton(sx + w - 90, sy + 85, 80, 14, "RESET CONFIG", 0xFFCC0000, b -> sendAction(AdminActionPayload.ActionType.RESET_DISCORD, "", "")));

        adder.add(new CommandButton(sx + 10, sy + 110, w - 20, 14, "CONFIRM SETTINGS", 0xFF00AA22, b -> {
            boolean hasToken = !genericInput.getValue().isEmpty();
            boolean hasChannel = !genericInput2.getValue().isEmpty();
            if (hasToken) sendAction(AdminActionPayload.ActionType.SET_DISCORD_TOKEN, genericInput.getValue(), "");
            if (hasChannel) sendAction(AdminActionPayload.ActionType.SET_DISCORD_CHANNEL, genericInput2.getValue(), "");
            if (hasToken || hasChannel) {
                genericInput.setValue("");
                genericInput2.setValue("");
                HandheldScreen.showToast("§aConfiguration Sent");
            }
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        g.drawString(font, "> ADMIN: " + currentView.name(), sx + 10, sy + 18, 0xFFAA0000, false);
        
        if (currentView == View.DISCORD) {
            float s = 0.8f;
            org.joml.Matrix3x2f m = new org.joml.Matrix3x2f();
            g.pose().get(m);
            String statusTxt = "BRIDGE: " + (discordOnline ? "CONNECTED" : "OFFLINE");
            g.pose().translate(sx + w - (font.width(statusTxt) * s) - 20, sy + 19);
            g.pose().scale(s, s);
            g.drawString(font, statusTxt, 0, 0, discordOnline ? 0xFF00FF00 : 0xFFCC0000, false);
            g.pose().set(m);
            g.fill(sx + w - 15, sy + 18, sx + w - 10, sy + 23, discordOnline ? 0xFF00FF00 : 0xFFCC0000);
        }

        switch (currentView) {
            case MENU -> g.drawCenteredString(font, "§8Select an admin module", cx, sy + 32, 0xFF888888);
            case PLAYERS -> {
                List<PlayerInfo> otherPlayers = Minecraft.getInstance().getConnection().getOnlinePlayers().stream()
                    .filter(p -> !p.getProfile().name().equals(Minecraft.getInstance().player.getName().getString()))
                    .toList();
                String onlineText = "PLAYERS: " + otherPlayers.size();
                g.drawString(font, onlineText, sx + w - font.width(onlineText) - 10, sy + 18, 0xFF888888, false);

                int listY = sy + 32;
                g.enableScissor(sx + 5, sy + 30, sx + w - 5, sy + 152);
                for (int i = 0; i < otherPlayers.size(); i++) {
                    int rowY = (int)(listY + (i * 55) - scrollPos);
                    if (rowY < sy + 10 || rowY > sy + 152) continue;
                    
                    g.fill(sx + 5, rowY, sx + w - 10, rowY + 50, 0x44000000);
                    g.renderOutline(sx + 5, rowY, w - 10, 50, 0xFF440000);
                    g.drawString(font, "§f" + otherPlayers.get(i).getProfile().name(), sx + 10, rowY + 4, 0xFFFFFFFF, false);
                }
                g.disableScissor();
                drawScrollbar(g, sx, sy, w, h, otherPlayers.size());
            }
            case WORLD -> {
                long time = Minecraft.getInstance().level.getDayTime() % 24000;
                long hour = (time / 1000 + 6) % 24;
                long minute = (time % 1000) * 6 / 100;
                String timeStr = String.format("LOCAL TIME: %02d:%02d", hour, minute);
                g.drawString(font, timeStr, sx + 10, sy + 32, 0xFF888888, false);
                g.fill(sx + 5, sy + 43, sx + w - 5, sy + 44, 0x22FFFFFF);
                g.drawString(font, "ATMOSPHERE:", sx + 10, sy + 75, 0xFFAA0000, false);
                g.drawString(font, "DIFFICULTY:", sx + 10, sy + 105, 0xFFAA0000, false);
                String diff = Minecraft.getInstance().level.getDifficulty().getSerializedName().toUpperCase();
                g.drawString(font, "§7[" + diff + "]", sx + 10 + font.width("DIFFICULTY: "), sy + 105, 0xFFFFFFFF, false);
            }
            case SYSTEM -> {
                g.fill(sx + 15, sy + 35, sx + w - 15, sy + 95, 0x44000000);
                g.renderOutline(sx + 15, sy + 35, w - 30, 60, 0xFF440000);
                g.drawString(font, "Server TPS: §a" + String.format("%.2f", currentTps), sx + 25, sy + 45, 0xFFFFFFFF, false);
                g.drawString(font, "Active Drones: §f" + droneCount, sx + 25, sy + 57, 0xFFFFFFFF, false);
                g.drawString(font, "Mesh Nodes: §f" + nodeCount, sx + 25, sy + 69, 0xFFFFFFFF, false);
                g.drawString(font, "Memory: §7" + ramInfo, sx + 25, sy + 81, 0xFFFFFFFF, false);
                g.drawCenteredString(font, "§eBroadcast + ENTER to Send", cx, sy + 110, 0xFFFFFF00);
            }
            case DISCORD -> {
                g.fill(sx + 5, sy + 28, sx + w - 5, sy + 29, 0x22FFFFFF);
                g.drawString(font, "BOT TOKEN:", sx + 10, sy + 35, 0xFFAAAAAA, false);
                g.drawString(font, "CHANNEL ID:", sx + 10, sy + 60, 0xFFAAAAAA, false);
                
                float s2 = 0.85f;
                org.joml.Matrix3x2f mxt2 = new org.joml.Matrix3x2f();
                g.pose().get(mxt2);
                String secTxt = "§8Security: Tokens are write-only for safety.";
                g.pose().translate(cx - (font.width(secTxt) * s2) / 2, sy + 140);
                g.pose().scale(s2, s2);
                g.drawString(font, secTxt, 0, 0, 0xFF444444, false);
                g.pose().set(mxt2);
            }
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int h, int contentH, int viewH) {
        if (contentH <= viewH) return;
        g.fill(x, y, x + 2, y + h, 0x33FFFFFF);
        int handleH = Math.max(10, (int)((float)viewH / contentH * h));
        int handleY = y + (int)(scrollPos / (contentH - viewH) * (h - handleH));
        g.fill(x - 1, handleY, x + 3, handleY + handleH, 0xAAFFFFFF);
    }

    @Override
    public void tick() {
        if (currentView == View.SYSTEM || currentView == View.DISCORD) {
            requestTimer++;
            if (requestTimer >= 20) {
                ClientPlayNetworking.send(new net.rdv88.redos.network.payload.AdminActionPayload(net.rdv88.redos.network.payload.AdminActionPayload.ActionType.REQUEST_SYSTEM_INFO, "", ""));
                requestTimer = 0;
            }
        }
    }

    public static void handleSystemSync(net.rdv88.redos.network.payload.SyncSystemInfoPayload p) {
        currentTps = p.tps();
        droneCount = p.activeDrones();
        nodeCount = p.meshNodes();
        ramInfo = p.serverRam();
        
        net.rdv88.redos.util.PermissionCache.update(
            net.rdv88.redos.util.PermissionCache.hasMainAccess(),
            net.rdv88.redos.util.PermissionCache.hasHighTechAccess(),
            p.isAdmin()
        );

        if (discordOnline != p.discordOnline()) {
            discordOnline = p.discordOnline();
            if (currentView == View.DISCORD) HandheldScreen.refreshApp();
        }
    }

    @Override public void preRender(int mx, int my, float d, int sx, int sy, int w, int h) {
        if (currentView != View.PLAYERS) return;
        List<PlayerInfo> players = Minecraft.getInstance().getConnection().getOnlinePlayers().stream()
            .filter(p -> !p.getProfile().name().equals(Minecraft.getInstance().player.getName().getString()))
            .toList();
        int totalH = players.size() * 55;
        int viewH = 110;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) {
            scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
            HandheldScreen.refreshApp();
        } else scrollPos = targetScroll;
    }
    
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (genericInput != null && genericInput.isFocused() && genericInput.keyPressed(event)) return true;
        if (genericInput2 != null && genericInput2.isFocused() && genericInput2.keyPressed(event)) return true;
        if (event.key() == 257 || event.key() == 335) { save(); return true; }
        return false; 
    }
    
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int sw, int sh) { 
        if (genericInput != null) genericInput.setFocused(mx >= genericInput.getX() && mx < genericInput.getX() + genericInput.getWidth() && my >= genericInput.getY() && my < genericInput.getY() + genericInput.getHeight());
        if (genericInput2 != null) genericInput2.setFocused(mx >= genericInput2.getX() && mx < genericInput2.getX() + genericInput2.getWidth() && my >= genericInput2.getY() && my < genericInput2.getY() + genericInput2.getHeight());
        
        if (button == 0 && currentView == View.PLAYERS) {
            int barX = sx + sw - 4; int barY = sy + 35; int barH = 106;
            int totalH = (Minecraft.getInstance().getConnection().getOnlinePlayers().size() - 1) * 55;
            int viewH = 110;
            if (totalH > viewH) {
                double maxScroll = Math.max(1, totalH - viewH); 
                int handleH = Math.max(10, (int)((float)viewH / totalH * barH));
                int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));

                if (mx >= barX - 4 && mx <= barX + 6 && my >= handleY - 2 && my <= handleY + handleH + 2) {
                    isDragging = true;
                    return true;
                }
            }
        }
        return false; 
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int sx, int sy, int w, int h) {
        if (isDragging && currentView == View.PLAYERS) {
            int barY = sy + 35; int barH = 106;
            int totalH = (Minecraft.getInstance().getConnection().getOnlinePlayers().size() - 1) * 55;
            int viewH = 110;
            double maxScroll = Math.max(1, totalH - viewH);
            int handleH = Math.max(10, (int)((float)viewH / totalH * barH));
            
            double clickOffset = (my - barY - (handleH / 2.0));
            double percentage = Math.clamp(clickOffset / (double)(barH - handleH), 0.0, 1.0);
            
            targetScroll = percentage * maxScroll;
            HandheldScreen.refreshApp();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button, int sx, int sy, int w, int h) {
        isDragging = false;
        return false;
    }
    
    @Override public boolean mouseScrolled(double mx, double my, double h, double v, int sx, int sy, int sw, int sh) { 
        if (currentView == View.PLAYERS) {
            int totalH = (Minecraft.getInstance().getConnection().getOnlinePlayers().size() - 1) * 55;
            int viewH = 110;
            double maxScroll = Math.max(0, totalH - viewH);
            targetScroll = Math.clamp(targetScroll - (v * 20), 0, maxScroll);
            return true;
        }
        return false; 
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int w, int h, int count) {
        int totalH = count * 55;
        int viewH = 110;
        if (totalH > viewH) {
            int barX = sx + w - 4; int barY = sy + 32; int barH = 106;
            g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
            double maxScroll = Math.max(1, totalH - viewH); 
            int handleH = Math.max(10, (int)((float)viewH / totalH * barH));
            int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
            g.fill(barX - 1, Math.clamp(handleY, barY, barY + barH - handleH), barX + 2, handleY + handleH, 0xAAFFFFFF); 
        }
    }

    @Override public boolean isEditMode() { 
        return (genericInput != null && genericInput.isFocused()) || (genericInput2 != null && genericInput2.isFocused()); 
    }
    @Override public Optional<net.minecraft.client.gui.components.events.GuiEventListener> getInitialFocus() { return Optional.ofNullable(genericInput); }

    public void save() {
        if (currentView == View.SYSTEM && genericInput != null && !genericInput.getValue().isEmpty()) {
            sendAction(AdminActionPayload.ActionType.BROADCAST, genericInput.getValue(), "");
            genericInput.setValue("");
        } else if (currentView == View.DISCORD) {
            if (genericInput != null && !genericInput.getValue().isEmpty()) sendAction(AdminActionPayload.ActionType.SET_DISCORD_TOKEN, genericInput.getValue(), "");
            if (genericInput2 != null && !genericInput2.getValue().isEmpty()) sendAction(AdminActionPayload.ActionType.SET_DISCORD_CHANNEL, genericInput2.getValue(), "");
            if (genericInput != null) genericInput.setValue("");
        }
    }

    private void sendAction(AdminActionPayload.ActionType type, String d1, String d2) {
        ClientPlayNetworking.send(new AdminActionPayload(type, d1, d2));
        HandheldScreen.showToast("§aAction Sent");
    }

    public void back() {
        if (currentView != View.MENU) { currentView = View.MENU; targetScroll = 0; scrollPos = 0; HandheldScreen.refreshApp(); }
        else HandheldScreen.requestAppSwitch("HOME");
    }

    // INTERNAL BUTTON CLASSES
    private class IconButton extends net.minecraft.client.gui.components.Button {
        private final ItemStack icon; private final String label; private final View target;
        public IconButton(int x, int y, int w, int h, ItemStack i, String l, View v) {
            super(x, y, w, h, Component.empty(), b -> { currentView = v; scrollPos = 0; targetScroll = 0; HandheldScreen.refreshApp(); }, DEFAULT_NARRATION);
            this.icon = i; this.label = l; this.target = v;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mx, int my, float d) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xFF441111 : 0xFF220505);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
            g.drawCenteredString(font, label, getX() + width / 2, getY() + height + 2, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }

    private class ActionButton extends net.minecraft.client.gui.components.Button {
        private final int color;
        public ActionButton(int x, int y, int w, int h, String l, int c, OnPress p) {
            super(x, y, w, h, Component.literal(l), p, DEFAULT_NARRATION); this.color = c;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mx, int my, float d) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? color : (color & 0x88FFFFFF));
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 2, 0xFFFFFFFF);
        }
    }

    private class CommandButton extends net.minecraft.client.gui.components.Button {
        private final int color;
        private final int minY, maxY;
        public CommandButton(int x, int y, int w, int h, String l, int c, OnPress p) {
            this(x, y, w, h, l, c, 0, 9999, p);
        }
        public CommandButton(int x, int y, int w, int h, String l, int c, int minY, int maxY, OnPress p) {
            super(x, y, w, h, Component.literal(l), p, DEFAULT_NARRATION); this.color = c;
            this.minY = minY; this.maxY = maxY;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mx, int my, float d) {
            if (getY() + height < minY || getY() > maxY) return;
            g.enableScissor(getX() - 5, minY, getX() + width + 5, maxY);
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? color | 0xFF000000 : (color & 0x66FFFFFF));
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFFFFFF : 0x44FFFFFF);
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 4, 0xFFFFFFFF);
            g.disableScissor();
        }
    }
}
