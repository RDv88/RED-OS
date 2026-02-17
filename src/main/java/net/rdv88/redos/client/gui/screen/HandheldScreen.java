package net.rdv88.redos.client.gui.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.rdv88.redos.client.gui.screen.handheld.*;
import net.rdv88.redos.item.HandheldDeviceItem;
import net.rdv88.redos.network.payload.*;

import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class HandheldScreen extends Screen {
    private final ItemStack stack;
    private final Player player;
    private final Set<String> activeIds;
    private final List<SyncProfilesPayload.ProfileData> savedProfiles = new ArrayList<>();

    private static List<SyncHandheldDataPayload.DeviceEntry> VISIBLE_DEVICES = new ArrayList<>();
    private static HandheldScreen currentInstance = null;
    private static String selectedDeviceName = "Unknown";

    private HandheldApp currentApp;
    private String currentAppName = "HOME";
    private static String savedAppName = "HOME";

    private String toastMessage = null;
    private int toastTimer = 0;

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 200;
    private static final int SCREEN_X_OFFSET = 45;
    private static final int SCREEN_Y_OFFSET = 15;
    private static final int SCREEN_WIDTH = 230;
    private static final int SCREEN_HEIGHT = 170;

    public static void setSavedApp(String name) { savedAppName = name; }

    public HandheldScreen(ItemStack stack, Player player) {
        super(Component.literal("RED-OS Handheld"));
        this.stack = stack;
        this.player = player;
        this.activeIds = parseIds(HandheldDeviceItem.getNetworkId(stack));
        this.savedProfiles.addAll(HandheldDeviceItem.getProfiles(stack));
        currentInstance = this;
        this.currentAppName = savedAppName;
        ClientPlayNetworking.send(new ConfigureHandheldPayload(HandheldDeviceItem.getNetworkId(stack)));
    }

    public static void requestAppSwitch(String appName) {
        if (currentInstance != null) {
            HandheldAppNWID.clearState();
            HandheldAppTransmittor.clearState();
            HandheldAppCamera.clearState();
            HandheldAppSensor.clearState();
            HandheldAppTriggers.clearState();
            HandheldAppSettings.clearState();
            currentInstance.currentAppName = appName;
            currentInstance.rebuildWidgets();
        }
    }

    public static void refreshApp() {
        if (currentInstance != null) {
            currentInstance.rebuildWidgets();
        }
    }

    public static void showToast(String message) {
        if (currentInstance != null) {
            currentInstance.toastMessage = message;
            currentInstance.toastTimer = 10;
        }
    }

    public static void updateNetworkIds() {
        if (currentInstance != null) {
            String raw = String.join(",", currentInstance.activeIds);
            if (raw.isEmpty()) raw = "00000";
            ClientPlayNetworking.send(new ConfigureHandheldPayload(raw));
        }
    }

    public static void setSelectedDeviceName(String name) { selectedDeviceName = name; }

    public static void updateDeviceList(List<SyncHandheldDataPayload.DeviceEntry> list) {
        BlockPos playerPos = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.blockPosition() : BlockPos.ZERO;
        list.sort(Comparator.comparingDouble(d -> d.pos().distSqr(playerPos)));
        
        VISIBLE_DEVICES = list;
        CameraViewScreen.setDeviceCache(list);
        QuantumPorterScreen.setDeviceCache(list);
        if (currentInstance != null) {
            if (currentInstance.isEditModeActive()) return;
            refreshApp(); 
        }
    }

    private Set<String> parseIds(String raw) {
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        return Arrays.stream(raw.split(",")).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    @Override
    protected void init() {
        int x = this.width / 2 - GUI_WIDTH / 2;
        int y = this.height / 2 - GUI_HEIGHT / 2;
        int screenX = x + SCREEN_X_OFFSET;
        int screenY = y + SCREEN_Y_OFFSET;

        int btnY = screenY + SCREEN_HEIGHT - 14; 
        
        if (currentAppName.equals("HOME")) {
            this.addRenderableWidget(new NavButton(screenX + 2, btnY - 2, 20, 14, "S", b -> this.onClose(), 0xFFDDAA00));
        } else {
            this.addRenderableWidget(new NavButton(screenX + 2, btnY - 2, 20, 14, "H", b -> requestAppSwitch("HOME"), 0xFF0055AA));
            this.addRenderableWidget(new NavButton(screenX + 24, btnY - 2, 20, 14, "<", b -> handleBackNavigation(), 0xFF444444));
            
            if (isEditModeActive()) {
                this.addRenderableWidget(new NavButton(screenX + (SCREEN_WIDTH / 2) - 17, btnY - 2, 35, 14, "SAVE", b -> handleSaveAction(), 0xFF00AA22));
            }
        }

        this.currentApp = switch (currentAppName) {
            case "NETWORK" -> new HandheldAppNWID(savedProfiles, activeIds);
            case "TRANSMITTERS" -> new HandheldAppTransmittor(VISIBLE_DEVICES);
            case "CAMERA" -> new HandheldAppCamera(VISIBLE_DEVICES);
            case "SENSORS" -> new HandheldAppSensor(VISIBLE_DEVICES);
            case "TRIGGERS" -> new HandheldAppTriggers(VISIBLE_DEVICES);
            case "SETTINGS" -> new HandheldAppSettings(savedProfiles, activeIds);
            case "HIGHTECH" -> new HandheldAppHighTech(VISIBLE_DEVICES);
            case "LOGISTICS" -> new HandheldAppLogistics(VISIBLE_DEVICES);
            default -> new HandheldAppHome();
        };

        this.currentApp.init(screenX, screenY, SCREEN_WIDTH, SCREEN_HEIGHT, new HandheldApp.WidgetAdder() {
            @Override
            public <T extends GuiEventListener & Renderable & NarratableEntry> T add(T widget) {
                return HandheldScreen.this.addRenderableWidget(widget);
            }
        });
        
        this.currentApp.getInitialFocus().ifPresent(this::setFocused);
    }

    private boolean isEditModeActive() {
        if (currentApp != null) return currentApp.isEditMode();
        return false;
    }

    private void handleBackNavigation() {
        if (currentApp instanceof HandheldAppNWID app) app.back();
        else if (currentApp instanceof HandheldAppTransmittor app) app.back();
        else if (currentApp instanceof HandheldAppCamera app) app.back();
        else if (currentApp instanceof HandheldAppSensor app) app.back();
        else if (currentApp instanceof HandheldAppTriggers app) app.back();
        else if (currentApp instanceof HandheldAppHighTech app) app.back();
        else if (currentApp instanceof HandheldAppSettings app) app.back();
        else if (currentApp instanceof HandheldAppLogistics app) app.back();
        else requestAppSwitch("HOME");
    }

    private void handleSaveAction() {
        if (currentApp instanceof HandheldAppNWID app) app.save();
        else if (currentApp instanceof HandheldAppTransmittor app) app.save();
        else if (currentApp instanceof HandheldAppCamera app) app.save();
        else if (currentApp instanceof HandheldAppSensor app) app.save();
        else if (currentApp instanceof HandheldAppTriggers app) app.save();
        else if (currentApp instanceof HandheldAppHighTech app) app.save();
        else if (currentApp instanceof HandheldAppLogistics app) app.save();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int x = this.width / 2 - GUI_WIDTH / 2;
        int y = this.height / 2 - GUI_HEIGHT / 2;
        int screenX = x + SCREEN_X_OFFSET;
        int screenY = y + SCREEN_Y_OFFSET;
        int screenCX = screenX + SCREEN_WIDTH / 2;

        HandheldShell.render(g, x, y);

        g.fill(screenX - 2, screenY - 2, screenX + SCREEN_WIDTH + 2, screenY + SCREEN_HEIGHT + 2, 0xFF000000);
        g.fill(screenX, screenY, screenX + SCREEN_WIDTH, screenY + SCREEN_HEIGHT, 0xFF100505); 
        g.fill(screenX, screenY, screenX + SCREEN_WIDTH, screenY + 12, 0xFF220000); 
        g.fill(screenX, screenY + SCREEN_HEIGHT - 17, screenX + SCREEN_WIDTH, screenY + SCREEN_HEIGHT, 0xFF220000); 
        g.renderOutline(screenX, screenY, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFF440000); 

        super.render(g, mouseX, mouseY, delta);

        org.joml.Matrix3x2f m1 = new org.joml.Matrix3x2f();
        g.pose().get(m1);
        g.pose().translate(screenX + 4, screenY + 3);
        g.pose().scale(0.85f, 0.85f);
        g.drawString(this.font, "RED-OS V" + net.rdv88.redos.Redos.VERSION, 0, 0, 0xFFFF0000, false);
        g.pose().set(m1);

        drawAntennaIcon(g, screenCX - 5, screenY + 2);
        renderPrecisionClock(g, screenX + SCREEN_WIDTH - 2, screenY + 3);

        if (currentApp != null) {
            currentApp.render(g, mouseX, mouseY, delta, screenX, screenY, SCREEN_WIDTH, SCREEN_HEIGHT);
        }

        if (toastMessage != null && toastTimer > 0) {
            g.drawString(this.font, toastMessage, screenX + 50, screenY + SCREEN_HEIGHT - 12, 0xFFFFFFFF, false);
        }
    }

    private void renderPrecisionClock(GuiGraphics g, int rightX, int y) {
        long time = this.player.level().getDayTime() % 24000;
        String phase; int color;
        if (time < 2000) { phase = "DAWN"; color = 0xFFFFAA00; }
        else if (time < 11000) { phase = "DAY"; color = 0xFFFFFF00; }
        else if (time < 12000) { phase = "NOON"; color = 0xFFFFFFFF; }
        else if (time < 14000) { phase = "SUNSET"; color = 0xFFFF5500; }
        else if (time < 22000) { phase = "NIGHT"; color = 0xFF5555FF; }
        else { phase = "MIDNIGHT"; color = 0xFFAA00FF; }

        float scale = 0.8f;
        int textW = (int)(font.width(phase) * scale);
        int textX = rightX - textW - 2;
        int winX = textX - 10;

        org.joml.Matrix3x2f m2 = new org.joml.Matrix3x2f();
        g.pose().get(m2);
        g.pose().translate(textX, y + 1);
        g.pose().scale(scale, scale);
        g.drawString(this.font, phase, 0, 0, color, false);
        g.pose().set(m2);

        float bodyY = 6;
        boolean showingSun = false;
        if (time >= 22500 || time < 13500) {
            showingSun = true;
            if (time >= 22500 || time < 1000) { float p = (time >= 22500 ? (time-22500) : (time+1500))/2500f; bodyY = 6-(p*6); }
            else if (time < 11000) { bodyY = 0; }
            else { float p = (time-11000)/2500f; bodyY = (p*6); }
        } else {
            if (time >= 13500 && time < 15500) { float p = (time-13500)/2000f; bodyY = 6-(p*6); }
            else if (time < 21000) { bodyY = 0; }
            else { float p = (time-21000)/1500f; bodyY = (p*6); }
        }
        g.enableScissor(winX, y + 1, winX + 6, y + 7);
        drawCelestialBody(g, showingSun, winX, y + 1 + (int)bodyY, showingSun ? 0xFFFFFF00 : 0xFFFFFFFF);
        g.disableScissor();
    }

    private void drawCelestialBody(GuiGraphics g, boolean isSun, int x, int y, int color) {
        if (isSun) {
            g.fill(x+1, y+1, x+5, y+5, color); g.fill(x+2, y, x+4, y+1, color); g.fill(x+2, y+5, x+4, y+6, color);
            g.fill(x, y+2, x+1, y+4, color); g.fill(x+5, y+2, x+6, y+4, color);
        } else {
            g.fill(x+1, y+1, x+5, y+5, color); g.fill(x+2, y, x+4, y+1, color); g.fill(x+2, y+5, x+4, y+6, color);
            g.fill(x+4, y+1, x+6, y+5, 0x00000000); 
        }
    }

    private void drawAntennaIcon(GuiGraphics g, int x, int y) {
        g.fill(x+4, y+2, x+5, y+8, 0xFFFFFFFF); g.fill(x+3, y+1, x+6, y+2, 0xFFFFFFFF); 
        if (System.currentTimeMillis() % 1000 < 500) { g.fill(x+1, y+3, x+2, y+5, 0xFF00AA22); g.fill(x+7, y+3, x+8, y+5, 0xFF00AA22); }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (currentApp != null) return currentApp.mouseScrolled(mouseX, mouseY, h, v);
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (currentApp != null && currentApp.keyPressed(event)) return true;
        if (!isEditModeActive()) {
            if (Minecraft.getInstance().options.keyUp.matches(event) || Minecraft.getInstance().options.keyDown.matches(event) ||
                Minecraft.getInstance().options.keyLeft.matches(event) || Minecraft.getInstance().options.keyRight.matches(event) ||
                Minecraft.getInstance().options.keyJump.matches(event)) {
                this.onClose(); return false; 
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ClientPlayNetworking.send(new ConfigureHandheldPayload("STOP_TRACKING"));
        savedAppName = currentAppName;
        currentInstance = null;
        super.onClose();
    }

    @Override public void tick() { if (toastTimer > 0) toastTimer--; if (currentApp != null) currentApp.tick(); super.tick(); }

    public static class RowButton extends Button {
        private final SyncHandheldDataPayload.DeviceEntry device;
        public RowButton(int x, int y, int w, int h, SyncHandheldDataPayload.DeviceEntry d, OnPress p) { super(x, y, w, h, Component.empty(), p, DEFAULT_NARRATION); this.device = d; }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            if (isHovered()) g.fill(getX(), getY(), getX() + width, getY() + height, 0x22FF0000);
            
            String displayName = formatDeviceName(device);
            drawMarqueeText(g, displayName, getX() + 4, getY() + 4, 105, isHovered() ? 0xFFFFFFFF : 0xFFAA0000, true);
            
            if (!device.type().equals("PROFILE")) {
                String dist = String.format("[ %dm ]", (int)Math.sqrt(Minecraft.getInstance().player.blockPosition().distSqr(device.pos())));
                g.drawString(Minecraft.getInstance().font, dist, getX() + width - 65, getY() + 4, 0xFF666666, false);
                int blocks = (device.signalStrength() / 20) + 1;
                for (int i = 0; i < 5; i++) g.fill(getX() + width - 30 + (i * 5), getY() + 5, getX() + width - 27 + (i * 5), getY() + 11, (i < blocks) ? 0xFFAA0000 : 0xFF220000);
            } else {
                g.drawString(Minecraft.getInstance().font, "(" + device.id() + ")", getX() + width - 85, getY() + 4, 0xFF666666, false);
                g.drawString(Minecraft.getInstance().font, device.connectionMode(), getX() + width - 40, getY() + 4, device.connectionMode().equals("Enabled") ? 0xFF00FF00 : 0xFF666666, false);
            }
        }

        private String formatDeviceName(SyncHandheldDataPayload.DeviceEntry d) {
            String name = d.name();
            boolean isDefault = false;
            
            // Flexibele check voor standaardnamen (ongeacht hoofdletters of exacte match)
            String low = name.toLowerCase();
            if (low.contains("transmitter") || low.contains("camera") || low.contains("porter") || low.contains("sensor") || low.contains("trigger")) {
                // Als de naam een van de standaardnamen is, kort hem af
                if (low.equals("long range transmitter") || low.equals("lr transmitter")) { name = "LR Transmitter"; isDefault = true; }
                else if (low.equals("short range transmitter") || low.equals("sr transmitter")) { name = "SR Transmitter"; isDefault = true; }
                else if (low.equals("wireless ip camera") || low.equals("wireless camera")) { name = "Wireless Camera"; isDefault = true; }
                else if (low.equals("quantum porter") || low.equals("porter")) { name = "Porter"; isDefault = true; }
                else if (low.equals("smart motion sensor") || low.equals("sensor")) { name = "Sensor"; isDefault = true; }
                else if (low.equals("remote redstone trigger") || low.equals("trigger")) { name = "Trigger"; isDefault = true; }
            }

            // Toon ALTIJD coördinaten als het een standaardnaam is
            if (isDefault) {
                BlockPos p = d.pos();
                return String.format("%s (%d,%d,%d)", name, p.getX(), p.getY(), p.getZ());
            }
            return name;
        }

        private void drawMarqueeText(GuiGraphics g, String text, int x, int y, int width, int color, boolean leftAlign) {
            int textWidth = Minecraft.getInstance().font.width(text);
            if (textWidth <= width) { g.drawString(Minecraft.getInstance().font, text, leftAlign ? x : x + (width - textWidth) / 2, y, color, false); return; }
            double time = System.currentTimeMillis() / 1500.0;
            int offset = (int) ((Math.sin(time * 2.0) * 0.5 + 0.5) * (textWidth - width));
            g.enableScissor(x, y, x + width, y + 10); g.drawString(Minecraft.getInstance().font, text, x - offset, y, color, false); g.disableScissor();
        }
    }

    public static class NavButton extends Button {
        private final int color;
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col) { super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION); this.color = col; }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, this.isHovered() ? color : color - 0x22000000);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), this.getX() + this.width / 2, this.getY() + 3, 0xFFFFFFFF);
        }
    }
}