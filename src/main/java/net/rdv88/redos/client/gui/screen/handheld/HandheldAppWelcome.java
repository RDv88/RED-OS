package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.item.HandheldDeviceItem;
import java.util.Optional;

public class HandheldAppWelcome implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final long appStartTime;

    public HandheldAppWelcome() {
        this.appStartTime = System.currentTimeMillis();
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        int screenCX = sx + w / 2;
        int btnY = sy + h - 16;
        
        adder.add(new HandheldScreen.NavButton(screenCX - 50, btnY, 100, 14, "ACKNOWLEDGE", b -> {
            ItemStack handheld = HandheldDeviceItem.findHandheld(Minecraft.getInstance().player);
            if (!handheld.isEmpty()) {
                HandheldDeviceItem.setInitialized(handheld);
                String serial = HandheldDeviceItem.getSerial(handheld);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new net.rdv88.redos.network.payload.ConfigureHandheldPayload("INITIALIZE", serial));
            }
            HandheldScreen.requestAppSwitch("HOME");
        }, 0xFF00AA22));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        long elapsed = System.currentTimeMillis() - appStartTime;

        // 1. Draw "Beautiful Font" Logo (Slim, Spaced, High-Tech)
        String logoText = "RED-OS";
        float logoScale = 2.1f;
        int letterSpacing = 3; 
        
        org.joml.Matrix3x2f originalMatrix = new org.joml.Matrix3x2f(g.pose());
        
        int totalWidth = 0;
        for (int i = 0; i < logoText.length(); i++) {
            totalWidth += (int)(font.width(String.valueOf(logoText.charAt(i))) * logoScale);
            if (i < logoText.length() - 1) totalWidth += (int)(letterSpacing * logoScale);
        }
        
        int startX = cx - (totalWidth / 2);
        int currentX = startX;

        for (int i = 0; i < logoText.length(); i++) {
            float charDelay = i * 150;
            float alpha = Math.clamp((elapsed - 200 - charDelay) / 500f, 0.0f, 1.0f);
            if (alpha > 0) {
                int aVal = (int)(alpha * 255);
                String c = String.valueOf(logoText.charAt(i));
                g.pose().translate(currentX, sy + 33);
                g.pose().scale(logoScale, logoScale);
                g.drawString(font, c, 1, 1, (aVal/3 << 24) | 0x880000, false);
                g.drawString(font, c, 0, 0, (aVal << 24) | 0xFFFFFF, false);
                g.pose().set(originalMatrix);
            }
            currentX += (int)(font.width(String.valueOf(logoText.charAt(i))) * logoScale) + (int)(letterSpacing * logoScale);
        }

        // 2. Tech Decorations (Lines)
        if (elapsed > 1500) {
            float lineAlpha = Math.clamp((elapsed - 1500) / 500f, 0.0f, 1.0f);
            int lCol = ((int)(lineAlpha * 180) << 24) | 0xFF0000;
            g.fill(cx - 60, sy + 25, cx + 60, sy + 26, lCol); 
            g.fill(cx - 60, sy + 55, cx + 60, sy + 56, lCol); 
        }

        // 3. Welcome & Instructions
        if (elapsed > 1800) {
            int ty = sy + 75;
            float textAlpha = Math.clamp((elapsed - 1800) / 600f, 0.0f, 1.0f);
            int aVal = (int)(textAlpha * 255);
            
            g.drawCenteredString(font, "§fThank you for choosing RED-OS", cx, ty, (aVal << 24) | 0xFFFFFFFF);
            g.drawCenteredString(font, "§8System Link: §aESTABLISHED", cx, ty + 12, (aVal << 24) | 0xAAAAAA);
            
            if (elapsed > 2500) {
                float subAlpha = Math.clamp((elapsed - 2500) / 600f, 0.0f, 1.0f);
                int sVal = (int)(subAlpha * 255);
                g.drawCenteredString(font, "Shortcut Key: §b[H]", cx, ty + 35, (sVal << 24) | 0xFFFFFFFF);
                g.drawCenteredString(font, "§8Change in SETTINGS > KEYS", cx, ty + 45, (sVal << 24) | 0x666666);
            }
        }
    }

    @Override public void tick() {}
    @Override public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int w, int h) { return false; }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int sx, int sy, int w, int h) { return false; }
    @Override public boolean mouseReleased(double mx, double my, int b, int sx, int sy, int w, int h) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v, int sx, int sy, int sw, int sh) { return false; }
    @Override public boolean isEditMode() { return false; }
    @Override public Optional<GuiEventListener> getInitialFocus() { return Optional.empty(); }
    public void back() {}
    public static void clearState() {}
}
