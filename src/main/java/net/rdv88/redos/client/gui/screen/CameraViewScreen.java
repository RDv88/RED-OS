package net.rdv88.redos.client.gui.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.network.payload.RequestCameraViewPayload;
import net.rdv88.redos.network.payload.StopCameraViewPayload;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class CameraViewScreen extends Screen {
    private final String currentCameraName;
    private final Random random = new Random();
    
    private EditBox chatInput;
    private boolean isChatting = false;
    private boolean isSwitching = false;
    private int switchScrollOffset = 0;
    private static List<SyncHandheldDataPayload.DeviceEntry> cachedDevices = new ArrayList<>();

    public CameraViewScreen(String cameraName) {
        super(Component.literal("Camera View"));
        this.currentCameraName = cameraName;
        HandheldScreen.setSavedApp("CAMERA");
    }

    public static void setDeviceCache(List<SyncHandheldDataPayload.DeviceEntry> devices) {
        cachedDevices = devices;
    }

    @Override
    protected void init() {
        refreshWidgets();
    }

    private void refreshWidgets() {
        this.clearWidgets();
        int w = this.width;
        int h = this.height;
        int btnY = h - 22;

        // 1. Bottom Navigation
        this.addRenderableWidget(new NavButton(12, btnY - 3, 20, 14, "<", b -> this.onClose(), 0xFF444444));
        this.addRenderableWidget(new NavButton(34, btnY - 3, 45, 14, "SWITCH", b -> {
            this.isSwitching = !this.isSwitching;
            this.isChatting = false;
            this.switchScrollOffset = 0;
            refreshWidgets();
        }, isSwitching ? 0xFFAA0000 : 0xFF444444));

        // 2. Integrated Chat Input
        this.chatInput = new EditBox(this.font, 82, btnY - 3, w - 100, 12, Component.literal("Chat"));
        this.chatInput.setMaxLength(256);
        this.chatInput.setVisible(isChatting);
        this.addRenderableWidget(this.chatInput);

        // 3. Scrollable Switch Menu
        if (isSwitching) {
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                .filter(d -> d.type().equals("CAMERA"))
                .collect(Collectors.toList());
            
            int menuX = 34;
            int visibleCount = 10; // INCREASED: Show up to 10 cameras
            int menuHeight = (visibleCount * 16) + 4;
            int menuY = h - 25 - menuHeight; // Positioned perfectly above the bar
            
            // Ensure menuY isn't negative on small screens
            menuY = Math.max(25, menuY);
            
            // Scroll Buttons (Smaller, at the top and bottom of the menu)
            if (switchScrollOffset > 0) {
                this.addRenderableWidget(new NavButton(menuX + 102, menuY + 2, 15, 12, "▲", b -> {
                    switchScrollOffset--;
                    refreshWidgets();
                }, 0xFF444444));
            }
            if (cams.size() > switchScrollOffset + visibleCount) {
                this.addRenderableWidget(new NavButton(menuX + 102, menuY + menuHeight - 14, 15, 12, "▼", b -> {
                    switchScrollOffset++;
                    refreshWidgets();
                }, 0xFF444444));
            }

            for (int i = 0; i < visibleCount; i++) {
                int index = i + switchScrollOffset;
                if (index >= cams.size()) break;
                
                SyncHandheldDataPayload.DeviceEntry device = cams.get(index);
                String label = device.name();
                this.addRenderableWidget(new NavButton(menuX + 2, menuY + 2 + (i * 16), 100, 14, label, b -> {
                    ClientPlayNetworking.send(new RequestCameraViewPayload(device.pos()));
                    this.isSwitching = false;
                }, device.name().equals(currentCameraName) ? 0xFFAA0000 : 0xFF222222));
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        int bSize = 8; 

        // Borders and UI
        guiGraphics.fill(0, 0, w, bSize, 0xFF000000); 
        guiGraphics.fill(0, h - bSize, w, h, 0xFF000000);
        guiGraphics.fill(0, bSize, bSize, h - bSize, 0xFF000000);
        guiGraphics.fill(w - bSize, bSize, w, h - bSize, 0xFF000000);
        guiGraphics.renderOutline(bSize, bSize, w - (bSize * 2), h - (bSize * 2), 0xFF440000);

        guiGraphics.enableScissor(bSize, bSize, w - bSize, h - bSize);
        renderFeedEffects(guiGraphics, bSize, bSize, w - (bSize * 2), h - (bSize * 2));
        guiGraphics.disableScissor();

        guiGraphics.fill(bSize + 1, bSize + 1, w - bSize - 1, bSize + 13, 0xFF220000); 
        guiGraphics.fill(bSize + 1, h - bSize - 20, w - bSize - 1, h - bSize - 1, 0xFF220000); 

        // RENDER SWITCH MENU WINDOW (Background and Border)
        if (isSwitching) {
            int menuX = 34;
            int visibleCount = 10;
            int menuHeight = (visibleCount * 16) + 4;
            int menuY = Math.max(25, h - 25 - menuHeight);
            
            guiGraphics.fill(menuX, menuY, menuX + 120, menuY + menuHeight, 0xFF110505);
            guiGraphics.renderOutline(menuX, menuY, 120, menuHeight, 0xFFAA0000);
        }

        guiGraphics.drawString(this.font, "RED-OS", bSize + 4, bSize + 3, 0xFFFF0000, false);
        drawAntennaIcon(guiGraphics, (w / 2) - 5, bSize + 3);
        
        long worldTime = Minecraft.getInstance().level.getDayTime() % 24000;
        String timeStr = (worldTime < 13000) ? "DAY" : "NIGHT";
        guiGraphics.drawString(this.font, timeStr, w - bSize - font.width(timeStr) - 4, bSize + 3, (worldTime < 13000 ? 0xFFFFFFFF : 0xFF8888FF), false);

        if (System.currentTimeMillis() % 2000 < 1000) {
            guiGraphics.fill(bSize + 6, bSize + 18, bSize + 12, bSize + 24, 0xFFFF0000);
        }
        guiGraphics.drawString(this.font, "LIVE FEED: " + currentCameraName, bSize + 16, bSize + 17, 0xFFFF5555, false);
        
        if (!isChatting) {
            guiGraphics.drawString(this.font, "[T] CHAT | [ESC] EXIT", 85, h - bSize - 14, 0xFFAAAAAA, false);
        }

        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isSwitching) {
            if (verticalAmount < 0) switchScrollOffset++;
            else if (verticalAmount > 0) switchScrollOffset = Math.max(0, switchScrollOffset - 1);
            
            // Limit scroll offset
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                .filter(d -> d.type().equals("CAMERA"))
                .collect(Collectors.toList());
            int maxScroll = Math.max(0, cams.size() - 10);
            switchScrollOffset = Math.min(switchScrollOffset, maxScroll);
            
            refreshWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawAntennaIcon(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 2, x + 5, y + 8, 0xFFFFFFFF); 
        g.fill(x + 3, y + 1, x + 6, y + 2, 0xFFFFFFFF); 
        if (System.currentTimeMillis() % 1000 < 500) {
            g.fill(x + 1, y + 3, x + 2, y + 5, 0xFFFF0000); g.fill(x + 7, y + 3, x + 8, y + 5, 0xFFFF0000);
        }
    }

    private void renderFeedEffects(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 0; i < 40; i++) {
            int nx = x + random.nextInt(w); int ny = y + random.nextInt(h);
            g.fill(nx, ny, nx + 1, ny + 1, 0x11FFFFFF);
        }
        int scan = (int)((System.currentTimeMillis() / 80) % 30);
        for (int i = y + scan; i < y + h; i += 30) g.fill(x, i, x + w, i + 1, 0x06FFFFFF);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (isChatting) {
            if (event.key() == 257 || event.key() == 335) {
                String msg = chatInput.getValue();
                if (!msg.isEmpty()) Minecraft.getInstance().player.connection.sendChat(msg);
                chatInput.setValue(""); isChatting = false; refreshWidgets();
                return true;
            }
            if (event.key() == 256) { isChatting = false; refreshWidgets(); return true; }
            return chatInput.keyPressed(event);
        }
        if (event.key() == 84) { isChatting = true; isSwitching = false; refreshWidgets(); this.setFocused(chatInput); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ClientPlayNetworking.send(new StopCameraViewPayload());
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private class NavButton extends Button {
        private final int color;
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col) {
            super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION);
            this.color = col;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, this.isHovered() ? color : color - 0x22000000);
            g.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + 3, 0xFFFFFFFF);
        }
    }
}