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
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppSensor implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    
    private static int scrollOffset = 0;
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
    public void init(int screenX, int screenY, int width, int height, WidgetAdder adder) {
        SyncHandheldDataPayload.DeviceEntry selected = getDeviceAt(selectedPos);
        SyncHandheldDataPayload.DeviceEntry setupTarget = getDeviceAt(setupPos);

        if (selected != null) { setupConfigFields(screenX, screenY, adder, selected); }
        else if (setupTarget != null) { setupSensorFields(screenX, screenY, width, adder, setupTarget); }
        else { populateTable(screenX, screenY, width, adder); }
    }

    private void setupConfigFields(int sx, int sy, WidgetAdder adder, SyncHandheldDataPayload.DeviceEntry selected) {
        this.nameInput = new EditBox(font, sx + 55, sy + 100, 160, 18, Component.literal("SensorName"));
        this.nameInput.setMaxLength(20); this.nameInput.setValue(selected.name());
        adder.add(nameInput);
        this.idInput = new EditBox(font, sx + 55, sy + 125, 160, 18, Component.literal("SensorID"));
        this.idInput.setMaxLength(5); this.idInput.setValue(selected.id());
        adder.add(idInput);
        nameInput.setFocused(true);
    }

    private void setupSensorFields(int sx, int sy, int w, WidgetAdder adder, SyncHandheldDataPayload.DeviceEntry setupTarget) {
        int ty = sy + 40;
        int btnW = 80;
        int bx = sx + (w / 2) - 90;

        adder.add(new SettingToggle(bx, ty, btnW, 14, "PLAYERS", setupTarget.detectPlayers(), b -> {
            saveSettings(setupTarget, !setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.range(), setupTarget.holdTime());
        }));
        adder.add(new SettingToggle(bx, ty + 16, btnW, 14, "ENEMIES", setupTarget.detectMobs(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), !setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.range(), setupTarget.holdTime());
        }));
        adder.add(new SettingToggle(bx, ty + 32, btnW, 14, "NEUTRALS", setupTarget.detectAnimals(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), !setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.range(), setupTarget.holdTime());
        }));
        adder.add(new SettingToggle(bx, ty + 48, btnW, 14, "VILLAGERS", setupTarget.detectVillagers(), b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), !setupTarget.detectVillagers(), setupTarget.range(), setupTarget.holdTime());
        }));

        int ax = sx + (w / 2) + 10;
        adder.add(new HandheldScreen.NavButton(ax, ty + 10, 20, 14, "-", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), Math.max(1, setupTarget.range() - 1), setupTarget.holdTime());
        }, 0xFF444444));
        adder.add(new HandheldScreen.NavButton(ax + 50, ty + 10, 20, 14, "+", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), Math.min(15, setupTarget.range() + 1), setupTarget.holdTime());
        }, 0xFF444444));

        adder.add(new HandheldScreen.NavButton(ax, ty + 42, 20, 14, "-", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.range(), Math.max(0, setupTarget.holdTime() - 10));
        }, 0xFF444444));
        adder.add(new HandheldScreen.NavButton(ax + 50, ty + 42, 20, 14, "+", b -> {
            saveSettings(setupTarget, setupTarget.detectPlayers(), setupTarget.detectMobs(), setupTarget.detectAnimals(), setupTarget.detectVillagers(), setupTarget.range(), Math.min(100, setupTarget.holdTime() + 10));
        }, 0xFF444444));
    }

    private void saveSettings(SyncHandheldDataPayload.DeviceEntry target, boolean p, boolean m, boolean a, boolean v, int range, int hold) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ConfigureSensorSettingsPayload(target.pos(), p, m, a, v, range, hold));
        HandheldScreen.refreshApp();
    }

    private void populateTable(int sx, int sy, int w, WidgetAdder adder) {
        int listY = sy + 35;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream().filter(d -> d.type().equals("SENSOR")).collect(Collectors.toList());
        int maxScroll = Math.max(0, filtered.size() - 6);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        
        if (scrollOffset > 0) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY - 12, 15, 10, "▲", b -> { scrollOffset--; HandheldScreen.refreshApp(); }, 0xFF444444));
        if (filtered.size() > scrollOffset + 6) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY + 110, 15, 10, "▼", b -> { scrollOffset++; HandheldScreen.refreshApp(); }, 0xFF444444));
        
        for (int i = 0; i < 6; i++) {
            int index = i + scrollOffset; if (index >= filtered.size()) break;
            SyncHandheldDataPayload.DeviceEntry device = filtered.get(index);
            int rowY = listY + (i * 18);
            adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 50, 16, device, b -> { selectedPos = device.pos(); HandheldScreen.refreshApp(); }));
            adder.add(new HandheldScreen.NavButton(sx + w - 42, rowY + 1, 35, 14, "SETUP", b -> { setupPos = device.pos(); HandheldScreen.refreshApp(); }, 0xFF880000));
        }
    }

    @Override public Optional<GuiEventListener> getInitialFocus() { return selectedPos != null ? Optional.ofNullable(nameInput) : Optional.empty(); }
    @Override public boolean isEditMode() { return selectedPos != null || setupPos != null; }
    
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        SyncHandheldDataPayload.DeviceEntry selected = getDeviceAt(selectedPos);
        SyncHandheldDataPayload.DeviceEntry setupTarget = getDeviceAt(setupPos);

        if (selected != null) { renderDetailView(g, sx, sy, cx, selected); }
        else if (setupTarget != null) { renderSetupView(g, sx, sy, cx, w, setupTarget); }
        else {
            g.drawString(font, "> SENSORS", sx + 5, sy + 22, 0xFFAA0000, false);
            long count = devices.stream().filter(d -> d.type().equals("SENSOR")).count();
            String stat = String.format("%02d ACTIVE", count);
            g.drawString(font, stat, cx - (font.width(stat) / 2), sy + 22, 0xFF888888, false);
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
        int ty = sy + 35; g.drawString(font, "TYPE: ENVIRONMENT SENSOR", sx + 10, ty, 0xFFAAAAAA, false);
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
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (selectedPos != null) {
            if (event.key() == 257 || event.key() == 335) { save(); return true; }
            if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
            if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        }
        return false; 
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (selectedPos != null || setupPos != null) return false;
        if (v < 0) scrollOffset++; else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
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
            String n = nameInput.getValue(); String i = idInput.getValue();
            if (i.length() == 5) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.ConfigureDevicePayload(selected.pos(), n, i));
                selectedPos = null; HandheldScreen.refreshApp();
            }
        }
    }
    public static void clearState() { selectedPos = null; setupPos = null; scrollOffset = 0; }

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
