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
import net.rdv88.redos.network.payload.RequestFleetStatusPayload;
import net.rdv88.redos.network.payload.SyncFleetStatusPayload;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Optional;

public class HandheldAppLogistics implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncHandheldDataPayload.DeviceEntry> devices;
    private static List<SyncFleetStatusPayload.DroneTaskStatus> activeMissions = new ArrayList<>();
    
    private enum View { MENU, TAG_LIST, HUB_LIST, FLEET_STATUS, CONFIG }
    private static View currentView = View.MENU;
    
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static int scrollOffset = 0;
    private static BlockPos selectedPos = null; 
    private EditBox nameInput;
    private EditBox idInput;
    private int lastSx = 0, lastSy = 0;

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

        adder.add(new AppButton(startX, rowY, size, size, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NAME_TAG), "IO Tag Settings", View.TAG_LIST));
        adder.add(new AppButton(startX + size + gap, rowY, size, size, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST_MINECART), "Hub Settings", View.HUB_LIST));
        adder.add(new AppButton(startX + (size + gap) * 2, rowY, size, size, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAP), "Logistics Status", View.FLEET_STATUS));
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
            adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 28, 16, device, b -> { selectedPos = device.pos(); currentView = View.CONFIG; HandheldScreen.refreshApp(); }));
            adder.add(new HandheldScreen.NavButton(sx + w - 22, rowY + 1, 14, 14, "E", b -> { selectedPos = device.pos(); currentView = View.CONFIG; HandheldScreen.refreshApp(); }, 0xFF0055AA));
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

    @Override public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        if (Math.abs(scrollPos - targetScroll) > 0.1) { scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3; }
        else { scrollPos = targetScroll; }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        lastSx = sx; lastSy = sy;
        int cx = sx + w / 2;
        int cy = sy + h / 2;
        int currentScroll = (int)Math.round(scrollPos);

        if (!net.rdv88.redos.util.PermissionCache.hasHighTechAccess()) {
            g.drawCenteredString(font, "§c§lACCESS DENIED", cx, cy - 30, 0xFFFFFFFF);
            return;
        }

        switch (currentView) {
            case MENU -> g.drawCenteredString(font, "LOGISTICS", cx, sy + 18, 0xFFAA0000);
            case TAG_LIST -> g.drawString(font, "> IO TAG SETTINGS", sx + 10, sy + 18, 0xFFAA0000, false);
            case HUB_LIST -> g.drawString(font, "> HUB SETTINGS", sx + 10, sy + 18, 0xFFAA0000, false);
            case FLEET_STATUS -> {
                g.drawString(font, "> LOGISTICS STATUS", sx + 10, sy + 18, 0xFFAA0000, false);
                int hY = sy + 35;
                g.drawString(font, "§7CTR", sx + 10, hY, 0xFFFFFFFF, false);
                g.drawString(font, "§bSRC", sx + 40, hY, 0xFFFFFFFF, false);
                g.drawString(font, "§aDST", sx + 100, hY, 0xFFFFFFFF, false);
                g.drawString(font, "§6P", sx + 160, hY, 0xFFFFFFFF, false);
                g.drawString(font, "§7STATUS", sx + 180, hY, 0xFFFFFFFF, false);
                g.fill(sx + 5, hY + 10, sx + w - 5, hY + 11, 0xFF440000);

                if (activeMissions.isEmpty()) {
                    g.drawCenteredString(font, "NO ACTIVE MISSIONS", cx, sy + 80, 0xFF444444);
                } else {
                    g.enableScissor(sx, sy + 46, sx + w, sy + h - 20);
                    for (int i = 0; i < activeMissions.size(); i++) {
                        SyncFleetStatusPayload.DroneTaskStatus m = activeMissions.get(i);
                        int rY = hY + 15 + (i * 18) - currentScroll;
                        
                        if (rY < hY + 5 || rY > sy + h - 25) continue;
                        
                        // MANUAL BUTTON RENDERING
                        boolean hovered = mouseX >= sx + 10 && mouseX <= sx + 35 && mouseY >= rY && mouseY <= rY + 10;
                        g.fill(sx + 10, rY, sx + 35, rY + 10, hovered ? 0xFF441111 : 0xFF220505);
                        g.renderOutline(sx + 10, rY, 25, 10, hovered ? 0xFFFF0000 : 0xFF660000);
                        int ledCol = m.enabled() ? 0xFF00FF00 : 0xFF444444;
                        g.fill(sx + 12, rY + 2, sx + 16, rY + 6, ledCol);
                        g.drawString(font, m.enabled() ? "ON" : "OFF", sx + 18, rY + 1, m.enabled() ? 0xFFFFFFFF : 0xFF888888, false);
                        
                        // Labels
                        String sN = m.sourceName().length() > 8 ? m.sourceName().substring(0, 6) + ".." : m.sourceName();
                        String dN = m.destName().length() > 8 ? m.destName().substring(0, 6) + ".." : m.destName();
                        g.drawString(font, "§7" + sN, sx + 40, rY, 0xFFFFFFFF, false);
                        g.drawString(font, "§7" + dN, sx + 100, rY, 0xFFFFFFFF, false);
                        g.drawString(font, "§6" + m.priority(), sx + 160, rY, 0xFFFFFFFF, false);
                        
                        // DYNAMIC STATUS with 0.8 Scale using safe Matrix3x2f
                        String st;
                        int stColor = 0xFFFFFFFF;
                        if (m.etaTicks() > 10) {
                            st = (m.etaTicks()/20) + "s";
                            stColor = 0xFF55FFFF;
                        } else {
                            st = switch (m.droneState()) {
                                case "STEP2_GOING_TO_SOURCE" -> { stColor = 0xFFFFFF55; yield "LOADING"; }
                                case "STEP3_GOING_TO_TARGET" -> { stColor = 0xFF55FF55; yield "UNLOADING"; }
                                case "STEP4_RETURNING_HOME" -> { stColor = 0xFFAAAAAA; yield "DOCKING"; }
                                case "STEP_RETURNING_ITEMS_TO_SOURCE" -> { stColor = 0xFFFFAA00; yield "REJECTED"; }
                                default -> { stColor = 0xFF55FFFF; yield "ARRIVED"; }
                            };
                        }
                        
                        org.joml.Matrix3x2f mxt = new org.joml.Matrix3x2f();
                        g.pose().get(mxt);
                        g.pose().translate(sx + 180, rY + 1);
                        g.pose().scale(0.8f, 0.8f);
                        g.drawString(font, st, 0, 0, stColor, false);
                        g.pose().set(mxt);
                    }
                    g.disableScissor();
                }
            }
            case CONFIG -> {
                SyncHandheldDataPayload.DeviceEntry s = getSelectedDevice();
                if (s != null) g.drawCenteredString(font, "> CONFIG: " + s.name(), cx, sy + 18, 0xFFAA0000);
            }
        }
    }

    @Override public void tick() {
        if (currentView == View.FLEET_STATUS && Minecraft.getInstance().player != null && Minecraft.getInstance().player.tickCount % 20 == 0) {
            ClientPlayNetworking.send(new RequestFleetStatusPayload());
        }
    }

    public static void updateMissionList(List<SyncFleetStatusPayload.DroneTaskStatus> missions) { 
        boolean sizeChanged = activeMissions.size() != missions.size();
        activeMissions = missions; 
        if (sizeChanged) {
            HandheldScreen.refreshApp(); 
        }
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (currentView == View.CONFIG) { if (event.key() == 257 || event.key() == 335) { save(); return true; } if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event); if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event); }
        return false; 
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentView == View.FLEET_STATUS && button == 0) {
            int hY = lastSy + 35; 
            int currentScroll = (int)Math.round(scrollPos);
            for (int i = 0; i < activeMissions.size(); i++) {
                int rY = hY + 15 + (i * 18) - currentScroll;
                if (rY < hY + 5 || rY > lastSy + 170 - 25) continue; // 170 is SCREEN_HEIGHT
                
                if (mouseX >= lastSx + 10 && mouseX <= lastSx + 35 && mouseY >= rY && mouseY <= rY + 10) {
                    SyncFleetStatusPayload.DroneTaskStatus m = activeMissions.get(i);
                    ClientPlayNetworking.send(new net.rdv88.redos.network.payload.ConfigureDroneHubPayload(m.hubPos(), "TOGGLE_TASK", m.taskIndex(), BlockPos.ZERO, BlockPos.ZERO, 0));
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (currentView == View.HUB_LIST || currentView == View.TAG_LIST || currentView == View.FLEET_STATUS) {
            int itemCount = currentView == View.FLEET_STATUS ? activeMissions.size() : (int)devices.stream().filter(d -> d.type().equals(currentView == View.TAG_LIST ? "IO_TAG" : "DRONE_STATION")).count();
            int maxScroll = Math.max(0, (itemCount * 18) - 100);
            targetScroll = Math.clamp(targetScroll - (v * 40), 0, maxScroll);
            return true;
        }
        return false;
    }

    public void back() {
        if (currentView == View.CONFIG) { currentView = getSelectedDevice().type().equals("IO_TAG") ? View.TAG_LIST : View.HUB_LIST; selectedPos = null; }
        else if (currentView != View.MENU) { currentView = View.MENU; targetScroll = 0; scrollPos = 0; }
        else { HandheldScreen.requestAppSwitch("HOME"); }
        HandheldScreen.refreshApp();
    }

    public void save() {
        SyncHandheldDataPayload.DeviceEntry s = getSelectedDevice();
        if (s != null && nameInput != null && idInput != null) {
            String n = nameInput.getValue(), i = idInput.getValue();
            if (i.length() == 5) { ClientPlayNetworking.send(new ConfigureDevicePayload(s.pos(), n, i)); currentView = s.type().equals("IO_TAG") ? View.TAG_LIST : View.HUB_LIST; selectedPos = null; HandheldScreen.refreshApp(); }
        }
    }

    public static void clearState() { currentView = View.MENU; selectedPos = null; scrollOffset = 0; targetScroll = 0; scrollPos = 0; }

    private void drawMarqueeText(GuiGraphics g, String text, int x, int y, int width, int color, float scale) {
        int textWidth = (int)(font.width(text) * scale);
        double time = System.currentTimeMillis() / 1500.0;
        int offset = 0;
        if (textWidth > width) { int maxOffset = textWidth - width; offset = (int) ((Math.sin(time * 2.0) * 0.5 + 0.5) * maxOffset); }
        else { x = x + (width - textWidth) / 2; }
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
            super(x, y, w, h, Component.empty(), b -> { currentView = v; targetScroll = 0; scrollPos = 0; HandheldScreen.refreshApp(); }, DEFAULT_NARRATION); 
            this.icon = i; this.label = l; this.targetView = v;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xFF441111 : 0xFF220505);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
            String line1 = label, line2 = "";
            if (label.contains(" Settings")) { line1 = label.replace(" Settings", ""); line2 = "Settings"; }
            else if (label.contains(" Status")) { line1 = label.replace(" Status", ""); line2 = "Status"; }
            int labelY = getY() + height + 4;
            int color = isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA;
            g.drawString(font, line1, getX() + (width - font.width(line1)) / 2, labelY, color, false);
            if (!line2.isEmpty()) g.drawString(font, line2, getX() + (width - font.width(line2)) / 2, labelY + 10, color, false);
        }
    }
}