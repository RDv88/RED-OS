package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;
import net.rdv88.redos.network.payload.ConfigureDevicePayload;
import net.rdv88.redos.network.payload.ConfigureSensorSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppSensor implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;
    private static BlockPos selectedPos = null;
    private static BlockPos setupPos = null;
    private EditBox nameInput;
    private EditBox idInput;

    public HandheldAppSensor(List<SyncHandheldDataPayload.DeviceEntry> devices) { this.devices = devices; }

    private SyncHandheldDataPayload.DeviceEntry getDeviceAt(BlockPos pos) {
        if (pos == null) return null;
        return devices.stream().filter(d -> d.pos().equals(pos)).findFirst().orElse(null);
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        SyncHandheldDataPayload.DeviceEntry selected = getDeviceAt(selectedPos);
        SyncHandheldDataPayload.DeviceEntry setupTarget = getDeviceAt(setupPos);

        if (selected != null) { setupConfigFields(sx, sy, adder, selected); }
        else if (setupTarget != null) { setupSensorFields(sx, sy, w, adder, setupTarget); }
        else { populateTable(sx, sy, w, adder); }
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

    private void setupSensorFields(int sx, int sy, int w, WidgetAdder adder, SyncHandheldDataPayload.DeviceEntry setupTarget) {
        int ty = sy + 40;
        int btnW = 80;
        int bx = sx + (w / 2) - 90;

        adder.add(new SettingToggle(bx, ty, btnW, 14, "PLAYERS", setupTarget.detectPlayers(), b -> {
            saveSettings(setupTarget, !setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), setupTarget.range(), setupTarget.holdTime());
        }));
        adder.add(new SettingToggle(bx, ty + 16, btnW, 14, "ENEMIES", setupTarget.detectMobs(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), !setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), setupTarget.range(), setupTarget.holdTime());
        }));
        adder.add(new SettingToggle(bx, ty + 32, btnW, 14, "NEUTRALS", setupTarget.detectAnimals(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), !setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), setupTarget.range(), setupTarget.holdTime());
        }));
        adder.add(new SettingToggle(bx, ty + 48, btnW, 14, "VILLAGERS", setupTarget.detectVillagers(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), !setupTarget.detectVillagers(), setupTarget.alertsEnabled(), setupTarget.range(), setupTarget.holdTime());
        }));

        int ax = sx + (w / 2) + 10;
        adder.add(new SettingToggle(ax, ty + 64, btnW, 14, "ALERTS", setupTarget.alertsEnabled(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), !setupTarget.alertsEnabled(), setupTarget.range(), setupTarget.holdTime());
        }));

        adder.add(new HandheldScreen.NavButton(ax, ty + 10, 20, 14, "-", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), Math.max(1, setupTarget.range() - 1), setupTarget.holdTime());
        }, 0xFF444444));
        adder.add(new HandheldScreen.NavButton(ax + 50, ty + 10, 20, 14, "+", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), Math.min(15, setupTarget.range() + 1), setupTarget.holdTime());
        }, 0xFF444444));

        adder.add(new HandheldScreen.NavButton(ax, ty + 42, 20, 14, "-", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), setupTarget.range(), Math.max(0, setupTarget.holdTime() - 10));
        }, 0xFF444444));
        adder.add(new HandheldScreen.NavButton(ax + 50, ty + 42, 20, 14, "+", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.alertsEnabled(), setupTarget.range(), Math.min(100, setupTarget.holdTime() + 10));
        }, 0xFF444444));
    }

    private void saveSettings(SyncHandheldDataPayload.DeviceEntry target, boolean p, boolean m, boolean a, boolean v, boolean alerts, int range, int hold) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).pos().equals(target.pos())) {
                devices.set(i, new SyncHandheldDataPayload.DeviceEntry(
                    target.pos(), target.id(), target.name(), target.type(), target.signalStrength(), target.connectionMode(),
                    p, m, a, v, alerts, range, hold, target.itemCount(), target.freeSpace()
                ));
                break;
            }
        }
        ClientPlayNetworking.send(new ConfigureSensorSettingsPayload(target.pos(), p, m, a, v, alerts, range, hold));
        HandheldScreen.refreshApp();
    }

    private void populateTable(int sx, int sy, int w, WidgetAdder adder) {
        int listY = sy + 35;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
        
        for (int i = 0; i < filtered.size(); i++) {
            SyncHandheldDataPayload.DeviceEntry device = filtered.get(i);
            int rowY = (int)(listY + (i * 18) - scrollPos);
            
            if (rowY > sy + 15 && rowY < sy + 152) {
                adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 68, 16, device, sy + 30, sy + 152, b -> { 
                    selectedPos = device.pos(); 
                    HandheldScreen.refreshApp(); 
                }));

                adder.add(new HandheldScreen.NavButton(sx + w - 62, rowY + 1, 14, 14, "E", b -> {
                    selectedPos = device.pos();
                    HandheldScreen.refreshApp();
                }, 0xFF0055AA, sy + 30, sy + 152));

                adder.add(new HandheldScreen.NavButton(sx + w - 45, rowY + 1, 38, 14, "SETUP", b -> { 
                    setupPos = device.pos(); 
                    HandheldScreen.refreshApp(); 
                }, 0xFF880000, sy + 30, sy + 152));
            }
        }
    }

    @Override public boolean isEditMode() { 
        return (nameInput != null && nameInput.isFocused()) || (idInput != null && idInput.isFocused()); 
    }
    
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        SyncHandheldDataPayload.DeviceEntry selected = getDeviceAt(selectedPos);
        SyncHandheldDataPayload.DeviceEntry setupTarget = getDeviceAt(setupPos);

        if (selected != null) { renderDetailView(g, sx, sy, cx, selected); }
        else if (setupTarget != null) { renderSetupView(g, sx, sy, cx, w, setupTarget); }
        else {
            g.drawString(font, "> SENSORS", sx + 5, sy + 22, 0xFFAA0000, false);
            List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
            String stat = String.format("%02d ACTIVE", filtered.size());
            g.drawString(font, stat, cx - (font.width(stat) / 2), sy + 22, 0xFF888888, false);
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

    private void renderSetupView(GuiGraphics g, int sx, int sy, int cx, int w, SyncHandheldDataPayload.DeviceEntry setupTarget) {
        g.drawCenteredString(font, "> SENSOR SETUP", cx, sy + 18, 0xFFAA0000);
        g.drawCenteredString(font, setupTarget.name(), cx, sy + 28, 0xFF888888);
        int ax = sx + (w / 2) + 10;
        int ty = sy + 40;
        g.drawString(font, "FILTERS:", sx + 20, ty - 10, 0xFFAAAAAA, false);
        g.drawString(font, "RANGE:", ax, ty, 0xFFAAAAAA, false);
        g.drawCenteredString(font, setupTarget.range() + "m", ax + 35, ty + 13, 0xFFFFFFFF);
        g.drawString(font, "HOLD TIME:", ax, ty + 32, 0xFFAAAAAA, false);
        g.drawCenteredString(font, (setupTarget.holdTime() / 20.0) + "s", ax + 35, ty + 45, 0xFFFFFFFF);
    }

    private void renderDetailView(GuiGraphics g, int sx, int sy, int cx, SyncHandheldDataPayload.DeviceEntry selected) {
        g.drawCenteredString(font, "> SENSOR DETAILS", cx, sy + 18, 0xFFAA0000);
        int ty = sy + 35; g.drawString(font, "TYPE: BIOMETRIC SENSOR", sx + 10, ty, 0xFFAAAAAA, false);
        g.drawString(font, "MODE: " + selected.connectionMode(), sx + 10, ty + 12, 0xFFAAAAAA, false);
        int barW = 140; int bx = cx - 70; int by = ty + 37;
        g.fill(bx-1, by-1, bx+barW+1, by+13, 0xFF444444); g.fill(bx, by, bx+barW, by+12, 0xFF111111);
        int fill = (int)(barW * (selected.signalStrength() / 100.0));
        g.fill(bx, by, bx+fill, by+12, 0xFFAA0000); g.drawCenteredString(font, selected.signalStrength() + "% SIGNAL", cx, by+2, 0xFFFFFFFF);
        g.drawString(font, "EDIT:", sx+10, sy+90, 0xFFAA0000, false);
        g.drawString(font, "Name:", sx+10, sy+105, 0xFFAAAAAA, false);
        g.drawString(font, "NW-ID:", sx+10, sy+130, 0xFFAAAAAA, false);
    }

    @Override public void tick() {}

    @Override
    public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        if (selectedPos != null || setupPos != null) return;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
        int totalH = filtered.size() * 18;
        int viewH = 117;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
        else scrollPos = targetScroll;
    }
    
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (selectedPos != null) {
            if (event.key() == 257 || event.key() == 335) { save(); return true; }
            if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
            if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        }
        return false; 
    }
    
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int w, int h) { 
        if (selectedPos != null) {
            if (nameInput != null) nameInput.setFocused(mx >= nameInput.getX() && mx < nameInput.getX() + nameInput.getWidth() && my >= nameInput.getY() && my < nameInput.getY() + nameInput.getHeight());
            if (idInput != null) idInput.setFocused(mx >= idInput.getX() && mx < idInput.getX() + idInput.getWidth() && my >= idInput.getY() && my < idInput.getY() + idInput.getHeight());
            return false;
        }
        if (setupPos != null) return false;
        if (button == 0) {
            int barX = sx + w - 4; int barY = sy + 35; int barH = 117;
            List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
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
            List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
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
        if (selectedPos != null || setupPos != null) return false;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
        int totalH = filtered.size() * 18;
        int viewH = 117;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll - (v * 20), 0, maxScroll);
        HandheldScreen.refreshApp(); return true;
    }
    public void back() { 
        if (selectedPos != null) { selectedPos = null; HandheldScreen.refreshApp(); } 
        else if (setupPos != null) { setupPos = null; HandheldScreen.refreshApp(); }
        else { HandheldScreen.requestAppSwitch("HOME"); } 
    }
    public void save() {
        SyncHandheldDataPayload.DeviceEntry selected = getDeviceAt(selectedPos);
        if (selected != null && nameInput != null && idInput != null) {
            String n = nameInput.getValue().trim(); String i = idInput.getValue().trim();
            if (i.length() == 5) {
                ClientPlayNetworking.send(new ConfigureDevicePayload(selected.pos(), n, i));
                HandheldScreen.showToast("§aConfiguration Saved");
                selectedPos = null; HandheldScreen.refreshApp();
            } else { HandheldScreen.showToast("§cID must be 5 digits"); }
        }
    }
    public static void clearState() { selectedPos = null; setupPos = null; scrollPos = 0; targetScroll = 0; }

    private class SettingToggle extends Button {
        private final boolean active;
        public SettingToggle(int x, int y, int w, int h, String txt, boolean active, OnPress p) {
            super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION);
            this.active = active;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int color = active ? 0xFFAA0000 : 0xFF333333;
            if (this.isHovered()) color += 0x22000000;
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
            g.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + 3, active ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }
}
