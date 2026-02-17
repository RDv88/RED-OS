package net.rdv88.redos.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CameraLoadingScreen extends Screen {
    private final String cameraName;
    private float progress = 0;
    private final long startTime;
    private static final long DURATION = 1500; // 1.5 seconds

    public CameraLoadingScreen(String cameraName) {
        super(Component.literal("Connecting..."));
        this.cameraName = cameraName;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // NOTE: Solid black background during buffering to hide teleportation "glitches"
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        long elapsed = System.currentTimeMillis() - startTime;
        this.progress = Math.min(1.0f, (float) elapsed / DURATION);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.drawCenteredString(this.font, "ESTABLISHING SECURE LINK...", centerX, centerY - 30, 0xFF00AA22);
        guiGraphics.drawCenteredString(this.font, "SOURCE: " + cameraName, centerX, centerY - 15, 0xFFAAAAAA);

        // Progress Bar
        int barW = 120;
        int barH = 4;
        int barX = centerX - (barW / 2);
        int barY = centerY + 10;
        
        guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF444444);
        guiGraphics.fill(barX, barY, barX + (int)(barW * progress), barY + barH, 0xFF00FF00);

        String pct = (int)(progress * 100) + "%";
        guiGraphics.drawCenteredString(this.font, pct, centerX, barY + 10, 0xFFFFFFFF);

        if (progress >= 1.0f) {
            // NOTE: Seamless transition to the actual Camera View HUD
            Minecraft.getInstance().setScreen(new CameraViewScreen(cameraName));
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void onClose() {
        // NOTE: If the user cancels or closes the screen during buffering, 
        // immediately notify the server to return the player to their body.
        if (progress < 1.0f) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.StopCameraViewPayload());
        }
        super.onClose();
    }
}
