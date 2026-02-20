package net.rdv88.redos.client.gui.screen.handheld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;
import net.rdv88.redos.network.payload.ConfigureDevicePayload;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppLogistics implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    
    private enum View { MENU, TAG_LIST, HUB_LIST, FLEET_STATUS, CONFIG }
    private static View currentView = View.MENU;
    
    private static int scrollOffset = 0;
    private static BlockPos selectedPos = null; // Changed to BlockPos for real-time
    private EditBox nameInput;
    private EditBox idInput;

    public HandheldAppLogistics(List<SyncHandheldDataPayload.DeviceEntry> devices) {
        this.devices = devices;
    }

    private SyncHandheldDataPayload.DeviceEntry getSelectedDevice() {
        if (selectedPos == null) return null;
        return devices.stream().filter(d -> d.pos().equals(selectedPos)).findFirst().orElse(null);
    }

    @Override
    public void init(int screenX, int screenY, int width, int height, WidgetAdder adder) {
        if (!net.rdv88.redos.util.PermissionCache.hasHighTechAccess()) return;

        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (selected == null && currentView == View.CONFIG) currentView = View.MENU;

        switch (currentView) {
            case MENU -> setupMenu(screenX, screenY, width, adder);
            case TAG_LIST -> populateList(screenX, screenY, width, adder, "IO_TAG");
            case HUB_LIST -> populateList(screenX, screenY, width, adder, "DRONE_STATION");
            case FLEET_STATUS -> { }
            case CONFIG -> setupConfigFields(screenX, screenY, adder, selected);
        }
    }

    private void setupMenu(int sx, int sy, int w, WidgetAdder adder) {
        int screenCX = sx + w / 2;
        int size = 36;
        int gap = 20;
        int rowWidth = (3 * size + 2 * gap);
        int startX = screenCX - (rowWidth / 2);
        int rowY = sy + 60;

        adder.add(new AppButton(startX, rowY, size, size, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NAME_TAG), "IO Tags", View.TAG_LIST));
        adder.add(new AppButton(startX + size + gap, rowY, size, size, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST_MINECART), "Drone Hubs", View.HUB_LIST));
        adder.add(new AppButton(startX + (size + gap) * 2, rowY, size, size, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAP), "Fleet Status", View.FLEET_STATUS));
    }

    private void populateList(int sx, int sy, int w, WidgetAdder adder, String typeFilter) {
        int listY = sy + 35;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream()
            .filter(d -> d.type().equals(typeFilter))
            .collect(Collectors.toList());

        int maxScroll = Math.max(0, filtered.size() - 6);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        if (scrollOffset > 0) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY - 12, 15, 10, "▲", b -> { scrollOffset--; HandheldScreen.refreshApp(); }, 0xFF444444));
        if (filtered.size() > scrollOffset + 6) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY + 110, 15, 10, "▼", b -> { scrollOffset++; HandheldScreen.refreshApp(); }, 0xFF444444));

        for (int i = 0; i < 6; i++) {
            int index = i + scrollOffset;
            if (index >= filtered.size()) break;
            SyncHandheldDataPayload.DeviceEntry device = filtered.get(index);
            int rowY = listY + (i * 18);
            
            // 1. Device Row
            adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 28, 16, device, b -> {
                selectedPos = device.pos(); 
                currentView = View.CONFIG;
                HandheldScreen.refreshApp();
            }));

            // 2. NEW: "E" (Edit Config) Button
            adder.add(new HandheldScreen.NavButton(sx + w - 22, rowY + 1, 14, 14, "E", b -> {
                selectedPos = device.pos();
                currentView = View.CONFIG;
                HandheldScreen.refreshApp();
            }, 0xFF0055AA));
        }
    }

    private void setupConfigFields(int sx, int sy, WidgetAdder adder, SyncHandheldDataPayload.DeviceEntry selected) {
        this.nameInput = new EditBox(font, sx + 55, sy + 80, 160, 18, Component.literal("Name"));
        this.nameInput.setMaxLength(20); 
        this.nameInput.setValue(selected != null ? selected.name() : "");
        adder.add(nameInput);

        this.idInput = new EditBox(font, sx + 55, sy + 110, 160, 18, Component.literal("NW-ID"));
        this.idInput.setMaxLength(5); 
        this.idInput.setValue(selected != null ? selected.id() : "");
        adder.add(idInput);
        
        nameInput.setFocused(true);
    }

    @Override
    public Optional<GuiEventListener> getInitialFocus() {
        return (net.rdv88.redos.util.PermissionCache.hasHighTechAccess() && currentView == View.CONFIG) ? Optional.ofNullable(nameInput) : Optional.empty();
    }

    @Override
    public boolean isEditMode() { return net.rdv88.redos.util.PermissionCache.hasHighTechAccess() && currentView == View.CONFIG; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        int cy = sy + h / 2;

        if (!net.rdv88.redos.util.PermissionCache.hasHighTechAccess()) {
            g.drawCenteredString(font, "§c§lACCESS DENIED", cx, cy - 30, 0xFFFFFFFF);
            g.fill(cx - 60, cy - 18, cx + 60, cy - 17, 0xFFAA0000);
            
            String[] message = {
                "§7Fleet protocols are",
                "§7currently §4LOCKED§7.",
                "",
                "§7Unlock the achievement",
                "§e'High-Tech' §7to initialize."
            };
            
            int ty = cy - 5;
            for (String line : message) {
                g.drawCenteredString(font, line, cx, ty, 0xFFFFFFFF);
                ty += 10;
            }
            return;
        }

        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        
        switch (currentView) {
            case MENU -> {
                g.drawCenteredString(font, "LOGISTICS HUB", cx, sy + 18, 0xFFAA0000);
                g.drawCenteredString(font, "Select Category", cx, sy + 35, 0xFF888888);
            }
            case TAG_LIST -> g.drawString(font, "> IO TAGS", sx + 10, sy + 18, 0xFFAA0000, false);
            case HUB_LIST -> g.drawString(font, "> DRONE HUBS", sx + 10, sy + 18, 0xFFAA0000, false);
            case FLEET_STATUS -> {
                g.drawCenteredString(font, "FLEET STATUS", cx, sy + 18, 0xFFAA0000);
                int hubs = (int)devices.stream().filter(d -> d.type().equals("DRONE_STATION")).count();
                int tags = (int)devices.stream().filter(d -> d.type().equals("IO_TAG")).count();
                g.drawString(font, "Active Hubs: " + hubs, sx + 20, sy + 60, 0xFFAAAAAA, false);
                g.drawString(font, "Connected Tags: " + tags, sx + 20, sy + 75, 0xFFAAAAAA, false);
                g.drawString(font, "Network: ONLINE", sx + 20, sy + 100, 0xFF00FF00, false);
            }
            case CONFIG -> {
                if (selected != null) {
                    String type = selected.type().equals("IO_TAG") ? "TAG" : "HUB";
                    g.drawCenteredString(font, "> " + type + " CONFIG", cx, sy + 18, 0xFFAA0000);
                    
                    if (selected.type().equals("IO_TAG")) {
                        g.drawString(font, "INVENTORY: §b" + selected.itemCount() + " items", sx + 10, sy + 45, 0xFFAAAAAA, false);
                        g.drawString(font, "FREE SPACE: §a" + selected.freeSpace(), sx + 10, sy + 57, 0xFFAAAAAA, false);
                    }

                    g.drawString(font, "Name:", sx + 10, sy + 85, 0xFFAAAAAA, false);
                    g.drawString(font, "NW-ID:", sx + 10, sy + 115, 0xFFAAAAAA, false);
                    g.drawString(font, "Pos: " + selected.pos().getX() + "," + selected.pos().getY() + "," + selected.pos().getZ(), sx + 10, sy + 140, 0xFF666666, false);
                }
            }
        }
    }

    @Override public void tick() {}
    
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (currentView == View.CONFIG) {
            if (event.key() == 257 || event.key() == 335) { save(); return true; }
            if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
            if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        }
        return false; 
    }
    
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (currentView == View.HUB_LIST || currentView == View.TAG_LIST) {
            if (v < 0) scrollOffset++; else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            HandheldScreen.refreshApp();
            return true;
        }
        return false;
    }
    
    public void back() {
        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (currentView == View.CONFIG && selected != null) { 
            currentView = selected.type().equals("IO_TAG") ? View.TAG_LIST : View.HUB_LIST; 
            selectedPos = null; 
        } else if (currentView != View.MENU) { 
            currentView = View.MENU; 
        } else { 
            HandheldScreen.requestAppSwitch("HOME"); 
        }
        HandheldScreen.refreshApp();
    }
    
    public void save() {
        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (selected != null && nameInput != null && idInput != null) {
            String n = nameInput.getValue(); 
            String i = idInput.getValue();
            if (i.length() == 5) {
                ClientPlayNetworking.send(new ConfigureDevicePayload(selected.pos(), n, i));
                currentView = selected.type().equals("IO_TAG") ? View.TAG_LIST : View.HUB_LIST;
                selectedPos = null; 
                HandheldScreen.refreshApp();
            }
        }
    }
    
    public static void clearState() { currentView = View.MENU; selectedPos = null; scrollOffset = 0; }

    private void drawMarqueeText(GuiGraphics g, String text, int x, int y, int width, int color, float scale) {
        int textWidth = (int)(font.width(text) * scale);
        double time = System.currentTimeMillis() / 1500.0;
        int offset = 0;
        if (textWidth > width) {
            int maxOffset = textWidth - width;
            offset = (int) ((Math.sin(time * 2.0) * 0.5 + 0.5) * maxOffset);
        } else { x = x + (width - textWidth) / 2; }
        org.joml.Matrix3x2f oldMatrix = new org.joml.Matrix3x2f();
        g.pose().get(oldMatrix); g.pose().translate(x, y); g.pose().scale(scale, scale);
        if (textWidth > width) g.enableScissor(x, y, x + width, y + 10);
        g.drawString(font, text, (int)(- (offset / scale)), 0, color, false);
        if (textWidth > width) g.disableScissor();
        g.pose().set(oldMatrix);
    }

    private class AppButton extends net.minecraft.client.gui.components.Button {
        private final net.minecraft.world.item.ItemStack icon; 
        private final String label;
        private final View targetView;

        public AppButton(int x, int y, int w, int h, net.minecraft.world.item.ItemStack i, String l, View v) { 
            super(x, y, w, h, Component.empty(), b -> { 
                currentView = v; scrollOffset = 0; HandheldScreen.refreshApp(); 
            }, DEFAULT_NARRATION); 
            this.icon = i; this.label = l; this.targetView = v;
        }

        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xFF441111 : 0xFF220505);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
            drawMarqueeText(g, label, getX() - 10, getY() + height + 4, width + 20, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA, 0.85f);
        }
    }
}
