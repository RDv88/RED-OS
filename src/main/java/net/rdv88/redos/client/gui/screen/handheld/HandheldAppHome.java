package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import java.util.ArrayList;
import java.util.List;

public class HandheldAppHome implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private final List<AppButton> apps = new ArrayList<>();

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        int screenCX = sx + w / 2;
        int size = 36;
        int gap = 30;
        int rowWidth = (3 * size + 2 * gap);
        int startX = screenCX - (rowWidth / 2); 
        
        apps.clear();
        // Row 1: ACTION & SOCIAL
        apps.add(new AppButton(startX, 35, size, size, new ItemStack(Items.WRITABLE_BOOK), "RED-OS Chat", "CHAT", sx, sy, w, h));
        apps.add(new AppButton(startX + size + gap, 35, size, size, new ItemStack(Items.SPYGLASS), "Camera", "CAMERA", sx, sy, w, h));
        apps.add(new AppButton(startX + (size + gap) * 2, 35, size, size, new ItemStack(Items.REDSTONE_TORCH), "Triggers", "TRIGGERS", sx, sy, w, h));
        
        // Row 2: ADVANCED UTILITIES
        int row2Y = 35 + size + 25;
        apps.add(new AppButton(startX, row2Y, size, size, new ItemStack(Items.DAYLIGHT_DETECTOR), "Sensors", "SENSORS", sx, sy, w, h));
        apps.add(new AppButton(startX + size + gap, row2Y, size, size, new ItemStack(Items.ECHO_SHARD), "Quantum Link", "HIGHTECH", sx, sy, w, h));
        apps.add(new AppButton(startX + (size + gap) * 2, row2Y, size, size, new ItemStack(Items.CHEST), "Logistics", "LOGISTICS", sx, sy, w, h));

        // Row 3: SYSTEM FOUNDATION
        int row3Y = row2Y + size + 25;
        apps.add(new AppButton(startX, row3Y, size, size, new ItemStack(Items.LIGHTNING_ROD), "Transmitters", "TRANSMITTERS", sx, sy, w, h));
        apps.add(new AppButton(startX + size + gap, row3Y, size, size, new ItemStack(Items.RECOVERY_COMPASS), "Network ID's", "NETWORK", sx, sy, w, h));
        apps.add(new AppButton(startX + (size + gap) * 2, row3Y, size, size, new ItemStack(Items.REPEATER), "Settings", "SETTINGS", sx, sy, w, h));

        // Row 4: SYSTEM ADMIN (Only for OP)
        if (net.rdv88.redos.util.PermissionCache.isAdmin()) {
            int row4Y = row3Y + size + 25;
            apps.add(new AppButton(startX + size + gap, row4Y, size, size, new ItemStack(Items.COMMAND_BLOCK), "Admin", "ADMIN", sx, sy, w, h));
        }

        for (AppButton app : apps) adder.add(app);
    }

    @Override
    public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        // SMOOTH SCROLL LOGIC
        if (Math.abs(scrollPos - targetScroll) > 0.1) {
            scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
        } else {
            scrollPos = targetScroll;
        }

        int currentScroll = (int)Math.round(scrollPos);
        int minY = sy + 25;
        int maxY = sy + h - 20;

        // Apply positions and visibility BEFORE widgets are rendered
        for (AppButton app : apps) {
            app.setY(sy + app.relY - currentScroll);
            app.visible = (app.getY() + app.getHeight() > minY && app.getY() < maxY);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        g.drawString(font, "> MENU", sx + 5, sy + 18, 0xFFAA0000, false);

        // Draw light-colored scroll indicator
        int barX = sx + w - 4;
        int barY = sy + 30;
        int barH = h - 50;
        g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
        
        double maxScroll = 140; 
        int handleH = 25;
        int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
        g.fill(barX - 1, handleY, barX + 2, handleY + handleH, 0xAAFFFFFF); 
    }

    @Override public void tick() {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { 
        targetScroll = Math.clamp(targetScroll - (verticalAmount * 40), 0, 140);
        return true; 
    }

    private class AppButton extends Button {
        private final ItemStack icon; 
        private final String label;
        private final int sx, sy, sw, sh;
        public final int relY;

        public AppButton(int x, int relY, int w, int h, ItemStack i, String l, String a, int sx, int sy, int sw, int sh) { 
            super(x, sy + relY, w, h, Component.empty(), b -> HandheldScreen.requestAppSwitch(a), DEFAULT_NARRATION); 
            this.icon = i; this.label = l; this.relY = relY; this.sx = sx; this.sy = sy; this.sw = sw; this.sh = sh;
        }

        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { 
            if (this.visible && this.active) this.onPress.onPress(this); 
        }

        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int minY = sy + 25;
            int maxY = sy + sh - 20;
            
            // Apply clipping locally for each button to prevent visual artifacts
            g.enableScissor(sx, minY, sx + sw, maxY);
            
            int color = isHovered() ? 0xFF441111 : 0xFF220505;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
            
            float scale = 0.85f;
            int labelX = getX() + (width - (int)(font.width(label) * scale)) / 2;
            int labelY = getY() + height + 4;
            
            org.joml.Matrix3x2f oldM = new org.joml.Matrix3x2f();
            g.pose().get(oldM);
            g.pose().translate(labelX, labelY);
            g.pose().scale(scale, scale);
            g.drawString(font, label, 0, 0, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA, false);
            g.pose().set(oldM);
            
            g.disableScissor();
        }
    }
}