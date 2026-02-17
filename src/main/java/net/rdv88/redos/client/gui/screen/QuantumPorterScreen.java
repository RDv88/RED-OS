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
        int listWidth = 200;
        int startX = (w - listWidth) / 2;
        int startY = 60;

        List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                .collect(Collectors.toList());

        if (!otherPorters.isEmpty()) {
            for (int i = 0; i < otherPorters.size(); i++) {
                SyncHandheldDataPayload.DeviceEntry target = otherPorters.get(i);
                this.addRenderableWidget(new NavButton(startX, startY + (i * 22), listWidth, 20, target.name(), b -> {
                    ClientPlayNetworking.send(new RequestTeleportPayload(porterPos, target.pos()));
                    this.onClose();
                }, 0xFF008888));
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        int bSize = 20;

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
            List<SyncHandheldDataPayload.DeviceEntry> otherPorters = cachedDevices.stream()
                    .filter(d -> d.type().equals("PORTER") && !d.pos().equals(porterPos))
                    .collect(Collectors.toList());

            if (otherPorters.isEmpty()) {
                g.drawCenteredString(this.font, "SCANNING FOR DESTINATIONS...", w / 2, h / 2, 0xFF888888);
            }
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override public void onClose() {
        currentInstance = null;
        super.onClose();
    }

    // MANDATORY: Never pause the game, especially in Singleplayer/Creative
    @Override public boolean isPauseScreen() { return false; }

    private class NavButton extends Button {
        private final int color;
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col) {
            super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION);
            this.color = col;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? color : color - 0x22000000);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFF00FFFF : 0xFF004444);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
        }
    }
}