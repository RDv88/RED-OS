package net.rdv88.redos.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.network.payload.RequestTeleportPayload;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuantumPorterScreen extends Screen {
    private final BlockPos porterPos;
    private final String porterName;
    private final int status; // 0: OK, 1: ID_MISMATCH, 2: NO_HANDHELD
    private static List<SyncHandheldDataPayload.DeviceEntry> cachedDevices = new ArrayList<>();
    private static QuantumPorterScreen currentInstance;
    
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;

    public QuantumPorterScreen(BlockPos pos, String name, int status) {
        super(Component.literal("Quantum Porter"));
        this.porterPos = pos;
        this.porterName = name;
        this.status = status;
        currentInstance = this;
    }

    public static void setDeviceCache(List<SyncHandheldDataPayload.DeviceEntry> devices) {
        cachedDevices = devices;
        if (currentInstance != null) {
            currentInstance.refreshWidgets();
        }
    }

    private void refreshWidgets() {
        Minecraft.getInstance().execute(this::clearAndInit);
    }

    private void clearAndInit() {
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        if (status != 0) {
            this.addRenderableWidget(new NavButton(this.width / 2 - 50, this.height / 2 + 20, 100, 20, "ABORT", b -> this.onClose(), 0xFF440000));
            return;
        }

        int w = this.width;
        int btnW = 140;
        int gap = 10;
        int totalGridW = (2 * btnW) + gap;
        int startX = (w - totalGridW) / 2;
        int startY = 60;

        List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                .collect(Collectors.toList());

        if (!otherPorters.isEmpty()) {
            for (int i = 0; i < otherPorters.size(); i++) {
                SyncHandheldDataPayload.DeviceEntry target = otherPorters.get(i);
                int column = i % 2;
                int row = i / 2;
                int px = startX + (column * (btnW + gap));
                int py = (int)(startY + (row * 22) - scrollPos);
                
                if (py > startY - 20 && py < startY + 110 + 20) {
                    this.addRenderableWidget(new NavButton(px, py, btnW, 20, target.name(), b -> {
                        ClientPlayNetworking.send(new RequestTeleportPayload(porterPos, target.pos()));
                        this.onClose();
                    }, 0xFF008888, startY, startY + 110));
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        int bSize = 20;

        List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                .collect(Collectors.toList());
        int totalRows = (int)Math.ceil(otherPorters.size() / 2.0);
        int totalH = totalRows * 22;
        int viewH = 110;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) {
            scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
            this.clearAndInit();
        } else scrollPos = targetScroll;

        g.fill(0, 0, w, h, 0xFF000000); 
        g.fill(bSize, bSize, w - bSize, h - bSize, 0xFF001515); 
        g.renderOutline(bSize, bSize, w - (bSize * 2), h - (bSize * 2), status != 0 ? 0xFF440000 : 0xFF004444);

        int scan = (int)((System.currentTimeMillis() / 80) % 30);
        for (int i = bSize + scan; i < h - bSize; i += 30) g.fill(bSize, i, w - bSize, i + 1, status != 0 ? 0x0A440000 : 0x0A00FFFF);

        g.drawCenteredString(this.font, status != 0 ? "§cACCESS DENIED" : "QUANTUM LINK ESTABLISHED", w / 2, bSize + 10, 0xFFFFFFFF);
        g.drawCenteredString(this.font, "STATION: " + porterName, w / 2, bSize + 22, 0xFFAAAAAA);

        if (status == 1) {
            g.drawCenteredString(this.font, "§cID VERIFICATION FAULT", w / 2, h / 2 - 10, 0xFFFF0000);
            g.drawCenteredString(this.font, "§8PLEASE CONFIGURE HANDHELD NW-ID", w / 2, h / 2 + 2, 0xFF888888);
        } else if (status == 2) {
            g.drawCenteredString(this.font, "§cHANDHELD REQUIRED", w / 2, h / 2 - 10, 0xFFFF0000);
            g.drawCenteredString(this.font, "§8QUANTUM LINK REQUIRES BIOMETRIC DEVICE", w / 2, h / 2 + 2, 0xFF888888);
        } else {
            if (otherPorters.isEmpty()) {
                g.drawCenteredString(this.font, "SCANNING FOR DESTINATIONS...", w / 2, h / 2, 0xFF888888);
            } else {
                drawScrollbar(g, w, h, otherPorters.size());
            }
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private void drawScrollbar(GuiGraphics g, int w, int h, int count) {
        int totalRows = (int)Math.ceil(count / 2.0);
        int totalH = totalRows * 22;
        int viewH = 110;
        int startY = 60;
        int btnW = 140;
        int totalGridW = (2 * btnW) + 10;
        int barX = (w + totalGridW) / 2 + 5;
        
        if (totalH > viewH) {
            int barH = viewH;
            g.fill(barX, startY, barX + 1, startY + barH, 0x33FFFFFF); 
            double maxScroll = Math.max(1, totalH - viewH); 
            int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
            int handleY = (int)(startY + (scrollPos / maxScroll) * (barH - handleH));
            int y1 = Math.clamp(handleY, startY, startY + barH - handleH);
            int y2 = y1 + handleH;
            if (scrollPos >= maxScroll - 0.1) y2 = startY + barH;
            g.fill(barX - 1, y1, barX + 2, y2, 0xAAFFFFFF); 
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                .collect(Collectors.toList());
        int totalRows = (int)Math.ceil(otherPorters.size() / 2.0);
        int totalH = totalRows * 22;
        int viewH = 110;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll - (verticalAmount * 22), 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isSecondary) {
        if (event.buttonInfo().button() == 0) {
            List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                    .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                    .collect(Collectors.toList());
            int totalRows = (int)Math.ceil(otherPorters.size() / 2.0);
            int totalH = totalRows * 22;
            int viewH = 110;
            int startY = 60;
            int btnW = 140;
            int totalGridW = (2 * btnW) + 10;
            int barX = (this.width + totalGridW) / 2 + 5;

            if (totalH > viewH) {
                double maxScroll = Math.max(1, totalH - viewH); 
                int handleH = Math.max(10, (int)((double)viewH / totalH * viewH));
                int handleY = (int)(startY + (scrollPos / maxScroll) * (viewH - handleH));

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
            List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                    .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                    .collect(Collectors.toList());
            int totalRows = (int)Math.ceil(otherPorters.size() / 2.0);
            int totalH = totalRows * 22;
            int viewH = 110;
            int startY = 60;
            double maxScroll = Math.max(1, totalH - viewH);
            int handleH = Math.max(10, (int)((double)viewH / totalH * viewH));
            
            double clickOffset = (event.y() - startY - (handleH / 2.0));
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

    @Override public void onClose() {
        currentInstance = null;
        super.onClose();
    }

    // MANDATORY: Never pause the game, especially in Singleplayer/Creative
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
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? color : color - 0x22000000);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFF00FFFF : 0xFF004444);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
            g.disableScissor();
        }
    }
}