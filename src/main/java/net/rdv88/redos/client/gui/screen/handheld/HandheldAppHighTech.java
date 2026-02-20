package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;
import net.rdv88.redos.network.payload.ConfigureDevicePayload;
import net.rdv88.redos.util.PermissionCache;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppHighTech implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    
    private static int scrollOffset = 0;
    private static BlockPos selectedPos = null; // Changed to BlockPos for real-time
    private EditBox nameInput;
    private EditBox idInput;

    public HandheldAppHighTech(List<SyncHandheldDataPayload.DeviceEntry> devices) { this.devices = devices; }

    private SyncHandheldDataPayload.DeviceEntry getSelectedDevice() {
        if (selectedPos == null) return null;
        return devices.stream().filter(d -> d.pos().equals(selectedPos)).findFirst().orElse(null);
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        if (!PermissionCache.hasHighTechAccess()) return;

        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (selected != null) { setupConfigFields(sx, sy, adder, selected); }
        else { populateTable(sx, sy, w, adder); }
    }

    private void setupConfigFields(int sx, int sy, WidgetAdder adder, SyncHandheldDataPayload.DeviceEntry selected) {
        this.nameInput = new EditBox(font, sx + 55, sy + 100, 160, 18, Component.literal("PorterName"));
        this.nameInput.setMaxLength(20); this.nameInput.setValue(selected.name());
        adder.add(nameInput);
        
        this.idInput = new EditBox(font, sx + 55, sy + 125, 160, 18, Component.literal("PorterID"));
        this.idInput.setMaxLength(5); this.idInput.setValue(selected.id());
        adder.add(idInput);
        
        nameInput.setFocused(true);
    }

    @Override
    public Optional<GuiEventListener> getInitialFocus() {
        return (PermissionCache.hasHighTechAccess() && selectedPos != null) ? Optional.ofNullable(nameInput) : Optional.empty();
    }

    private void populateTable(int sx, int sy, int w, WidgetAdder adder) {
        int listY = sy + 35;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream()
            .filter(d -> d.type().equals("PORTER"))
            .collect(Collectors.toList());
        
        int maxScroll = Math.max(0, filtered.size() - 6);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        if (scrollOffset > 0) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY - 12, 15, 10, "▲", b -> { scrollOffset--; HandheldScreen.refreshApp(); }, 0xFF444444));
        if (filtered.size() > scrollOffset + 6) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY + 110, 15, 10, "▼", b -> { scrollOffset++; HandheldScreen.refreshApp(); }, 0xFF444444));

        for (int i = 0; i < 6; i++) {
            int index = i + scrollOffset; if (index >= filtered.size()) break;
            SyncHandheldDataPayload.DeviceEntry device = filtered.get(index);
            int rowY = listY + (i * 18);
            
            // 1. Device Row
            adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 28, 16, device, b -> { 
                selectedPos = device.pos(); 
                HandheldScreen.refreshApp(); 
            }));

            // 2. NEW: "E" (Edit Config) Button
            adder.add(new HandheldScreen.NavButton(sx + w - 22, rowY + 1, 14, 14, "E", b -> {
                selectedPos = device.pos();
                HandheldScreen.refreshApp();
            }, 0xFF0055AA));
        }
    }

    @Override public boolean isEditMode() { return PermissionCache.hasHighTechAccess() && selectedPos != null; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        int cy = sy + h / 2;

        if (!PermissionCache.hasHighTechAccess()) {
            g.drawCenteredString(font, "§c§lACCESS DENIED", cx, cy - 30, 0xFFFFFFFF);
            g.fill(cx - 60, cy - 18, cx + 60, cy - 17, 0xFFAA0000);
            
            String[] message = {
                "§7Quantum protocols are",
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
        if (selected != null) {
            renderDetailView(g, sx, sy, cx, selected);
        } else {
            g.drawString(font, "> QUANTUM LINK", sx + 5, sy + 22, 0xFF00FFFF, false);
            long count = devices.stream().filter(d -> d.type().equals("PORTER")).count();
            String stat = String.format("%02d ONLINE", count);
            g.drawString(font, stat, (cx - (font.width(stat) / 2)) + 10, sy + 22, 0xFF888888, false);
        }
    }

    private void renderDetailView(GuiGraphics g, int sx, int sy, int cx, SyncHandheldDataPayload.DeviceEntry selected) {
        g.drawCenteredString(font, "> PORTER DETAILS", cx, sy + 18, 0xFF00FFFF);
        
        int ty = sy + 35; 
        g.drawString(font, "TYPE: QUANTUM PORTER", sx + 10, ty, 0xFFAAAAAA, false);
        g.drawString(font, "MODE: " + selected.connectionMode(), sx + 10, ty + 12, 0xFFAAAAAA, false);
        
        int barW = 140; int bx = cx - 70; int by = ty + 37;
        g.fill(bx-1, by-1, bx+barW+1, by+13, 0xFF444444); 
        g.fill(bx, by, bx+barW, by+12, 0xFF111111);
        int fill = (int)(barW * (selected.signalStrength() / 100.0));
        g.fill(bx, by, bx+fill, by+12, 0xFF00FFFF); 
        g.drawCenteredString(font, selected.signalStrength() + "% SIGNAL", cx, by+2, 0xFFFFFFFF);
        
        g.drawString(font, "EDIT:", sx+10, sy+90, 0xFF00FFFF, false);
        g.drawString(font, "Name:", sx+10, sy+105, 0xFFAAAAAA, false);
        g.drawString(font, "NW-ID:", sx+10, sy+130, 0xFFAAAAAA, false);
    }

    @Override public void tick() {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (PermissionCache.hasHighTechAccess() && selectedPos != null) {
            if (event.key() == 257 || event.key() == 335) { save(); return true; }
            if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
            if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        }
        return false; 
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (!PermissionCache.hasHighTechAccess() || selectedPos != null) return false;
        if (v < 0) scrollOffset++; else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        HandheldScreen.refreshApp(); return true;
    }
    
    public void back() { 
        if (selectedPos != null) { 
            selectedPos = null; 
            HandheldScreen.refreshApp(); 
        } else { 
            HandheldScreen.requestAppSwitch("HOME"); 
        } 
    }
    
    public void save() {
        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (PermissionCache.hasHighTechAccess() && selected != null && nameInput != null && idInput != null) {
            String n = nameInput.getValue(); 
            String i = idInput.getValue();
            if (i.length() == 5) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ConfigureDevicePayload(selected.pos(), n, i));
                selectedPos = null; 
                HandheldScreen.refreshApp();
            }
        }
    }
    public static void clearState() { selectedPos = null; scrollOffset = 0; }
}
