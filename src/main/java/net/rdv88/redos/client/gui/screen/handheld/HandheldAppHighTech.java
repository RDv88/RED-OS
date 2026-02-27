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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppHighTech implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;
    private static BlockPos selectedPos = null;
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
        if (selected != null) setupConfigFields(sx, sy, adder, selected);
        else populateTable(sx, sy, w, adder);
    }

    private void setupConfigFields(int sx, int sy, WidgetAdder adder, SyncHandheldDataPayload.DeviceEntry selected) {
        this.nameInput = new EditBox(font, sx + 55, sy + 100, 160, 18, Component.literal("Name"));
        this.nameInput.setMaxLength(20); this.nameInput.setValue(selected.name());
        adder.add(nameInput);
        this.idInput = new EditBox(font, sx + 55, sy + 125, 160, 18, Component.literal("ID"));
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
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("PORTER")).collect(Collectors.toList());
        
        for (int i = 0; i < filtered.size(); i++) {
            SyncHandheldDataPayload.DeviceEntry device = filtered.get(i);
            int rowY = (int)(listY + (i * 18) - scrollPos);
            
            if (rowY > sy + 15 && rowY < sy + 152) {
                adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 28, 16, device, sy + 30, sy + 152, b -> { 
                    selectedPos = device.pos(); 
                    HandheldScreen.refreshApp(); 
                }));

                adder.add(new HandheldScreen.NavButton(sx + w - 22, rowY + 1, 14, 14, "E", b -> {
                    selectedPos = device.pos();
                    HandheldScreen.refreshApp();
                }, 0xFF0055AA, sy + 30, sy + 152));
            }
        }
    }

    @Override public boolean isEditMode() { 
        return PermissionCache.hasHighTechAccess() && ((nameInput != null && nameInput.isFocused()) || (idInput != null && idInput.isFocused())); 
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        int cy = sy + h / 2;

        if (!PermissionCache.hasHighTechAccess()) {
            g.drawCenteredString(font, "§c§lACCESS DENIED", cx, cy - 30, 0xFFFFFFFF);
            g.fill(cx - 60, cy - 18, cx + 60, cy - 17, 0xFFAA0000);
            String[] message = {"§7Quantum protocols are", "§7currently §4LOCKED§7.", "", "§7Unlock the achievement", "§e'High-Tech' §7to initialize."};
            int ty = cy - 5;
            for (String line : message) { g.drawCenteredString(font, line, cx, ty, 0xFFFFFFFF); ty += 10; }
            return;
        }

        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (selected != null) renderDetailView(g, sx, sy, cx, selected);
        else {
            g.drawString(font, "> QUANTUM LINK", sx + 5, sy + 22, 0xFF00FFFF, false);
            List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("PORTER")).collect(Collectors.toList());
            String stat = String.format("%02d ONLINE", filtered.size());
            g.drawString(font, stat, (cx - (font.width(stat) / 2)) + 10, sy + 22, 0xFF888888, false);
            drawScrollbar(g, sx, sy, w, h, filtered.size());
        }
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int w, int h, int count) {
        int totalH = count * 18;
        int viewH = 117;
        if (totalH > viewH) {
            int barX = sx + w - 4; int barY = sy + 35; int barH = 117;
            g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
            double maxScroll = Math.max(1, totalH - viewH); 
            int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
            int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
            int y1 = Math.clamp(handleY, barY, barY + barH - handleH);
            int y2 = y1 + handleH;
            if (scrollPos >= maxScroll - 0.1) y2 = barY + barH;
            g.fill(barX - 1, y1, barX + 2, y2, 0xAAFFFFFF); 
        }
    }

    private void renderDetailView(GuiGraphics g, int sx, int sy, int cx, SyncHandheldDataPayload.DeviceEntry selected) {
        g.drawCenteredString(font, "> PORTER DETAILS", cx, sy + 18, 0xFF00FFFF);
        int ty = sy + 35; g.drawString(font, "TYPE: QUANTUM PORTER", sx + 10, ty, 0xFFAAAAAA, false);
        g.drawString(font, "MODE: " + selected.connectionMode(), sx + 10, ty + 12, 0xFFAAAAAA, false);
        int barW = 140; int bx = cx - 70; int by = ty + 37;
        g.fill(bx-1, by-1, bx+barW+1, by+13, 0xFF444444); g.fill(bx, by, bx+barW, by+12, 0xFF111111);
        int fill = (int)(barW * (selected.signalStrength() / 100.0));
        g.fill(bx, by, bx+fill, by+12, 0xFF00FFFF); g.drawCenteredString(font, selected.signalStrength() + "% SIGNAL", cx, by+2, 0xFFFFFFFF);
        g.drawString(font, "EDIT:", sx+10, sy+90, 0xFF00FFFF, false);
        g.drawString(font, "Name:", sx+10, sy+105, 0xFFAAAAAA, false);
        g.drawString(font, "NW-ID:", sx+10, sy+130, 0xFFAAAAAA, false);
    }

    @Override public void tick() {}

    @Override
    public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        if (!PermissionCache.hasHighTechAccess() || selectedPos != null) return;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("PORTER")).collect(Collectors.toList());
        int totalH = filtered.size() * 18;
        int viewH = 117;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
        else scrollPos = targetScroll;
    }
    
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (PermissionCache.hasHighTechAccess() && selectedPos != null) {
            if (event.key() == 257 || event.key() == 335) { save(); return true; }
            if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
            if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        }
        return false; 
    }
    
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int w, int h) { 
        if (!PermissionCache.hasHighTechAccess() || selectedPos != null) {
            if (selectedPos != null) {
                if (nameInput != null) nameInput.setFocused(mx >= nameInput.getX() && mx < nameInput.getX() + nameInput.getWidth() && my >= nameInput.getY() && my < nameInput.getY() + nameInput.getHeight());
                if (idInput != null) idInput.setFocused(mx >= idInput.getX() && mx < idInput.getX() + idInput.getWidth() && my >= idInput.getY() && my < idInput.getY() + idInput.getHeight());
            }
            return false;
        }
        if (button == 0) {
            int barX = sx + w - 4; int barY = sy + 35; int barH = 117;
            List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("PORTER")).collect(Collectors.toList());
            int totalH = filtered.size() * 18;
            int viewH = 117;
            if (totalH > viewH) {
                double maxScroll = Math.max(1, totalH - viewH); 
                int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
                int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
                if (mx >= barX - 4 && mx <= barX + 6 && my >= handleY - 2 && my <= handleY + handleH + 2) { isDragging = true; return true; }
            }
        }
        return false; 
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int sx, int sy, int w, int h) {
        if (isDragging) {
            int barY = sy + 35; int barH = 117;
            List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("PORTER")).collect(Collectors.toList());
            int totalH = filtered.size() * 18;
            int viewH = 117;
            double maxScroll = Math.max(1, totalH - viewH);
            int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
            double clickOffset = (my - barY - (handleH / 2.0));
            double percentage = Math.clamp(clickOffset / (double)(barH - handleH), 0.0, 1.0);
            targetScroll = percentage * maxScroll;
            HandheldScreen.refreshApp(); return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int b, int sx, int sy, int w, int h) { isDragging = false; return false; }
    
    @Override public boolean mouseScrolled(double mx, double my, double h, double v, int sx, int sy, int sw, int sh) {
        if (!PermissionCache.hasHighTechAccess() || selectedPos != null) return false;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("PORTER")).collect(Collectors.toList());
        int totalH = filtered.size() * 18;
        int viewH = 117;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll - (v * 20), 0, maxScroll);
        HandheldScreen.refreshApp(); return true;
    }
    
    public void back() { if (selectedPos != null) { selectedPos = null; HandheldScreen.refreshApp(); } else { HandheldScreen.requestAppSwitch("HOME"); } }
    
    public void save() {
        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (PermissionCache.hasHighTechAccess() && selected != null && nameInput != null && idInput != null) {
            String n = nameInput.getValue().trim(); String i = idInput.getValue().trim();
            if (i.length() == 5) {
                ClientPlayNetworking.send(new ConfigureDevicePayload(selected.pos(), n, i));
                HandheldScreen.showToast("§aConfiguration Saved");
                selectedPos = null; HandheldScreen.refreshApp();
            } else { HandheldScreen.showToast("§cID must be 5 digits"); }
        }
    }
    public static void clearState() { selectedPos = null; scrollPos = 0; targetScroll = 0; }
}
