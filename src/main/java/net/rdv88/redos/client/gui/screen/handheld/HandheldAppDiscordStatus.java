package net.rdv88.redos.client.gui.screen.handheld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.AdminActionPayload;
import java.util.Optional;

public class HandheldAppDiscordStatus implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private int requestTimer = 0;

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        // Request status immediately when opening
        ClientPlayNetworking.send(new AdminActionPayload(AdminActionPayload.ActionType.REQUEST_SYSTEM_INFO, "", ""));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        g.drawString(font, "> DISCORD SERVICE", sx + 10, sy + 18, 0xFF5865F2, false);

        boolean online = HandheldAppAdmin.isDiscordOnline();
        String statusText = online ? "§aONLINE" : "§cOFFLINE";
        g.drawCenteredString(font, "§fSTATUS: " + statusText, cx, sy + 50, 0xFFFFFFFF);
        
        if (!online) {
            var lines = font.split(Component.literal("This server has not yet configured for Discord or the connection is lost. Please contact Admin."), w - 40);
            int ly = sy + 80;
            for (var line : lines) {
                g.drawCenteredString(font, line, cx, ly, 0xFF888888);
                ly += 10;
            }
        } else {
            g.drawCenteredString(font, "§7Connection secured with Gateway", cx, sy + 80, 0xFF888888);
            g.drawCenteredString(font, "§7Message content intent active", cx, sy + 90, 0xFF888888);
        }

        g.drawCenteredString(font, "§8Discord messages flow into", cx, sy + 130, 0xFF444444);
        g.drawCenteredString(font, "§8the GENERAL chat tab.", cx, sy + 140, 0xFF444444);
    }

    @Override
    public void tick() {
        requestTimer++;
        if (requestTimer >= 40) { // Sync status every 2 seconds
            ClientPlayNetworking.send(new AdminActionPayload(AdminActionPayload.ActionType.REQUEST_SYSTEM_INFO, "", ""));
            requestTimer = 0;
        }
    }

    @Override public void preRender(int mx, int my, float d, int sx, int sy, int w, int h) {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int sw, int sh) { return false; }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v, int sx, int sy, int sw, int sh) { return false; }
    public void save() {}
    public void back() { HandheldScreen.requestAppSwitch("CHAT"); }
}
