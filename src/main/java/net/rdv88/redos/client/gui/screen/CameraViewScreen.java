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
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;
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
            targetScroll = 0; scrollPos = 0;
            refreshWidgets();
        }, isSwitching ? 0xFFAA0000 : 0xFF444444));

        // 2. Integrated Chat Input
        this.chatInput = new EditBox(this.font, 82, btnY - 3, w - 100, 12, Component.literal("Chat"));
        this.chatInput.setMaxLength(256);
        this.chatInput.setVisible(isChatting);
        this.addRenderableWidget(this.chatInput);

        // 3. Middle Grid Switch Menu
        if (isSwitching) {
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                .filter(d -> d.type().equals("CAMERA"))
                .collect(Collectors.toList());
            
            int btnW = 100;
            int gap = 6;
            int totalGridW = (2 * btnW) + gap;
            int menuX = (w - totalGridW) / 2;
            int menuY = (h / 2) - 50;
            int viewH = 100;

            for (int i = 0; i < cams.size(); i++) {
                SyncHandheldDataPayload.DeviceEntry device = cams.get(i);
                int col = i % 2;
                int row = i / 2;
                int px = menuX + (col * (btnW + gap));
                int py = (int)(menuY + (row * 16) - scrollPos);
                
                if (py > menuY - 14 && py < menuY + viewH) {
                    this.addRenderableWidget(new NavButton(px, py, btnW, 14, device.name(), b -> {
                        ClientPlayNetworking.send(new RequestCameraViewPayload(device.pos()));
                        this.isSwitching = false;
                        this.refreshWidgets();
                    }, device.name().equals(currentCameraName) ? 0xFFAA0000 : 0xFF222222, menuY, menuY + viewH));
                }
            }
            
            // Close button centered above the menu
            this.addRenderableWidget(new NavButton(menuX + (totalGridW / 2) - 6, menuY - 18, 12, 12, "X", b -> {
                this.isSwitching = false;
                this.refreshWidgets();
            }, 0xFFAA0000));
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

        // Borders and UI (Widescreen Effect)
        guiGraphics.fill(0, 0, w, bSize, 0xFF000000); 
        guiGraphics.fill(0, h - bSize, w, h, 0xFF000000);
        guiGraphics.renderOutline(0, bSize, w, h - (bSize * 2), 0xFF440000);

        guiGraphics.enableScissor(0, bSize, w, h - bSize);
        renderFeedEffects(guiGraphics, 0, bSize, w, h - (bSize * 2));
        guiGraphics.disableScissor();

        guiGraphics.fill(0, bSize + 1, w, bSize + 13, 0xFF220000); 
        guiGraphics.fill(0, h - bSize - 20, w, h - bSize - 1, 0xFF220000); 

        // RENDER SWITCH MENU WINDOW (Background and Border)
        if (isSwitching) {
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                .filter(d -> d.type().equals("CAMERA"))
                .collect(Collectors.toList());
            int totalRows = (int)Math.ceil(cams.size() / 2.0);
            int totalH = totalRows * 16;
            int viewH = 100;
            double maxScroll = Math.max(0, totalH - viewH);
            targetScroll = Math.clamp(targetScroll, 0, maxScroll);
            if (Math.abs(scrollPos - targetScroll) > 0.1) {
                scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
                refreshWidgets();
            } else scrollPos = targetScroll;

            int btnW = 100; int gap = 6; int totalGridW = (2 * btnW) + gap;
            int menuX = (w - totalGridW) / 2;
            int menuY = (h / 2) - 50;
            
            guiGraphics.fill(menuX - 5, menuY - 5, menuX + totalGridW + 12, menuY + viewH + 5, 0xEE110505);
            guiGraphics.renderOutline(menuX - 5, menuY - 5, totalGridW + 17, viewH + 10, 0xFFAA0000);
            
            drawScrollbar(guiGraphics, w, h, cams.size());
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

    private void drawScrollbar(GuiGraphics g, int w, int h, int count) {
        int totalRows = (int)Math.ceil(count / 2.0);
        int totalH = totalRows * 16;
        int viewH = 100;
        int btnW = 100; int gap = 6; int totalGridW = (2 * btnW) + gap;
        int menuX = (w - totalGridW) / 2;
        int menuY = (h / 2) - 50;
        int barX = menuX + totalGridW + 8;
        
        if (totalH > viewH) {
            g.fill(barX, menuY, barX + 1, menuY + viewH, 0x33FFFFFF); 
            double maxScroll = Math.max(1, totalH - viewH); 
            int handleH = Math.max(10, (int)((double)viewH / totalH * viewH));
            int handleY = (int)(menuY + (scrollPos / maxScroll) * (viewH - handleH));
            int y1 = Math.clamp(handleY, menuY, menuY + viewH - handleH);
            int y2 = y1 + handleH;
            if (scrollPos >= maxScroll - 0.1) y2 = menuY + viewH;
            g.fill(barX - 1, y1, barX + 2, y2, 0xAAFFFFFF); 
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isSecondary) {
        if (isSwitching && event.buttonInfo().button() == 0) {
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                    .filter(d -> d.type().equals("CAMERA"))
                    .collect(Collectors.toList());
            int totalRows = (int)Math.ceil(cams.size() / 2.0);
            int totalH = totalRows * 16;
            int viewH = 100;
            int btnW = 100; int gap = 6; int totalGridW = (2 * btnW) + gap;
            int menuX = (this.width - totalGridW) / 2;
            int menuY = (this.height / 2) - 50;
            int barX = menuX + totalGridW + 8;

            if (totalH > viewH) {
                double maxScroll = Math.max(1, totalH - viewH); 
                int handleH = Math.max(10, (int)((double)viewH / totalH * viewH));
                int handleY = (int)(menuY + (scrollPos / maxScroll) * (viewH - handleH));

                if (event.x() >= barX - 4 && event.x() <= barX + 6 && event.y() >= handleY - 2 && event.y() <= handleY + handleH + 2) {
                    isDragging = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isSecondary);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (isDragging) {
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                    .filter(d -> d.type().equals("CAMERA"))
                    .collect(Collectors.toList());
            int totalRows = (int)Math.ceil(cams.size() / 2.0);
            int totalH = totalRows * 16;
            int viewH = 100;
            int menuY = (this.height / 2) - 50;
            double maxScroll = Math.max(1, totalH - viewH);
            int handleH = Math.max(10, (int)((double)viewH / totalH * viewH));
            
            double clickOffset = (event.y() - menuY - (handleH / 2.0));
            double percentage = clickOffset / (double)(viewH - handleH);
            
            targetScroll = Math.clamp(percentage * maxScroll, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        isDragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isSwitching) {
            List<SyncHandheldDataPayload.DeviceEntry> cams = cachedDevices.stream()
                .filter(d -> d.type().equals("CAMERA"))
                .collect(Collectors.toList());
            int totalRows = (int)Math.ceil(cams.size() / 2.0);
            int totalH = totalRows * 16;
            int viewH = 100;
            double maxScroll = Math.max(0, totalH - viewH);
            targetScroll = Math.clamp(targetScroll - (verticalAmount * 16), 0, maxScroll);
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
        private final int minY, maxY;
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col) {
            this(x, y, w, h, txt, p, col, 0, 9999);
        }
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col, int minY, int maxY) {
            super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION);
            this.color = col;
            this.minY = minY;
            this.maxY = maxY;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            if (this.getY() + this.height < minY || this.getY() > maxY) return;
            g.enableScissor(this.getX() - 5, minY, this.getX() + this.width + 5, maxY);
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, this.isHovered() ? color : color - 0x22000000);
            g.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + 3, 0xFFFFFFFF);
            g.disableScissor();
        }
    }
}