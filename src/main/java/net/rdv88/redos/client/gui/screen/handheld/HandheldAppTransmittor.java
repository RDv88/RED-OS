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
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppTransmittor implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    
    private static int currentTab = 0; 
    private static int scrollOffset = 0;
    private static BlockPos selectedPos = null; // Changed from DeviceEntry to BlockPos
    private EditBox nameInput;
    private EditBox idInput;

    public HandheldAppTransmittor(List<SyncHandheldDataPayload.DeviceEntry> devices) {
        this.devices = devices;
    }

    private SyncHandheldDataPayload.DeviceEntry getSelectedDevice() {
        if (selectedPos == null) return null;
        return devices.stream().filter(d -> d.pos().equals(selectedPos)).findFirst().orElse(null);
    }

    @Override
    public void init(int screenX, int screenY, int width, int height, WidgetAdder adder) {
        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (selected != null) {
            setupConfigFields(screenX, screenY, adder, selected);
        } else {
            adder.add(new HandheldScreen.NavButton(screenX + 2, screenY + 14, (width / 2) - 3, 14, "DIRECT", b -> { currentTab = 0; scrollOffset = 0; HandheldScreen.refreshApp(); }, currentTab == 0 ? 0xFF880000 : 0xFF222222));
            adder.add(new HandheldScreen.NavButton(screenX + (width / 2) + 1, screenY + 14, (width / 2) - 3, 14, "MESH", b -> { currentTab = 1; scrollOffset = 0; HandheldScreen.refreshApp(); }, currentTab == 1 ? 0xFF880000 : 0xFF222222));
            populateTable(screenX, screenY, width, adder);
        }
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
        return selectedPos != null ? Optional.ofNullable(nameInput) : Optional.empty();
    }

    private void populateTable(int sx, int sy, int w, WidgetAdder adder) {
        int listY = sy + 45;
        List<SyncHandheldDataPayload.DeviceEntry> filtered = devices.stream()
            .filter(d -> (d.type().contains("RANGE") || d.type().equals("SERVER")))
            .filter(d -> (currentTab == 0) == d.connectionMode().equals("Direct"))
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
                selectedPos = device.pos(); HandheldScreen.refreshApp();
            }));

            // 2. NEW: "E" (Edit Config) Button
            adder.add(new HandheldScreen.NavButton(sx + w - 22, rowY + 1, 14, 14, "E", b -> {
                selectedPos = device.pos();
                HandheldScreen.refreshApp();
            }, 0xFF0055AA));
        }
    }

    @Override
    public boolean isEditMode() { return selectedPos != null; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        SyncHandheldDataPayload.DeviceEntry selected = getSelectedDevice();
        if (selected != null) {
            renderDetailView(g, sx, sy, cx, selected);
        } else {
            g.drawString(font, "> TRANSMITTERS", sx + 5, sy + 32, 0xFFAA0000, false);
            long count = devices.stream().filter(d -> (currentTab == 0) == d.connectionMode().equals("Direct") && (d.type().contains("RANGE") || d.type().equals("SERVER"))).count();
            String stat = String.format("%02d %s", count, currentTab == 0 ? "DIRECT" : "MESH");
            g.drawString(font, stat, cx - (font.width(stat) / 2), sy + 32, 0xFF888888, false);
        }
    }

    private void renderDetailView(GuiGraphics g, int sx, int sy, int cx, SyncHandheldDataPayload.DeviceEntry selected) {
        g.drawCenteredString(font, "> DEVICE DETAILS", cx, sy + 18, 0xFFAA0000);
        int ty = sy + 35;
        g.drawString(font, "TYPE: " + selected.type(), sx + 10, ty, 0xFFAAAAAA, false);
        g.drawString(font, "MODE: " + selected.connectionMode(), sx + 10, ty + 12, 0xFFAAAAAA, false);
        int barW = 140; int bx = cx - 70; int by = ty + 37;
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 13, 0xFF444444);
        g.fill(bx, by, bx + barW, by + 12, 0xFF111111);
        int fill = (int)(barW * (selected.signalStrength() / 100.0));
        g.fill(bx, by, bx + fill, by + 12, 0xFFAA0000);
        g.drawCenteredString(font, selected.signalStrength() + "% POWER", cx, by + 2, 0xFFFFFFFF);
        
        g.drawString(font, "EDIT:", sx + 10, sy + 90, 0xFFAA0000, false);
        g.drawString(font, "Name:", sx + 10, sy + 105, 0xFFAAAAAA, false);
        g.drawString(font, "NW-ID:", sx + 10, sy + 130, 0xFFAAAAAA, false);
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
        if (selectedPos != null) return false;
        if (v < 0) scrollOffset++; else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        HandheldScreen.refreshApp();
        return true;
    }
    
    public void back() {
        if (selectedPos != null) { selectedPos = null; HandheldScreen.refreshApp(); }
        else { HandheldScreen.requestAppSwitch("HOME"); }
    }
    
    public void save() {
        if (selectedPos != null && nameInput != null && idInput != null) {
            String n = nameInput.getValue(); String i = idInput.getValue();
            if (i.length() == 5) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.ConfigureDevicePayload(selectedPos, n, i));
                selectedPos = null; HandheldScreen.refreshApp();
            }
        }
    }
    
    public static void clearState() { selectedPos = null; currentTab = 0; scrollOffset = 0; }
}
