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
    
    private static boolean isDragging = false;
    private static double dragStartY = 0;
    private static double scrollStart = 0;

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        int screenCX = sx + w / 2;
        int size = 38;
        int gap = 28;
        int rowWidth = (3 * size + 2 * gap);
        int startX = screenCX - (rowWidth / 2) - 2; 
        
        apps.clear();
        // Start py at 20 instead of 25 since we raised the scissor boundary
        apps.add(new AppButton(startX, 20, size, size, new ItemStack(Items.WRITABLE_BOOK), "RED-OS Chat", "CHAT", sx, sy, w, h));
        apps.add(new AppButton(startX + size + gap, 20, size, size, new ItemStack(Items.SPYGLASS), "Camera", "CAMERA", sx, sy, w, h));
        apps.add(new AppButton(startX + (size + gap) * 2, 20, size, size, new ItemStack(Items.REDSTONE_TORCH), "Triggers", "TRIGGERS", sx, sy, w, h));
        
        int row2Y = 20 + size + 25;
        apps.add(new AppButton(startX, row2Y, size, size, new ItemStack(Items.DAYLIGHT_DETECTOR), "Sensors", "SENSORS", sx, sy, w, h));
        apps.add(new AppButton(startX + size + gap, row2Y, size, size, new ItemStack(Items.ECHO_SHARD), "Quantum Link", "HIGHTECH", sx, sy, w, h));
        apps.add(new AppButton(startX + (size + gap) * 2, row2Y, size, size, new ItemStack(Items.CHEST), "Logistics", "LOGISTICS", sx, sy, w, h));

        int row3Y = row2Y + size + 25;
        apps.add(new AppButton(startX, row3Y, size, size, new ItemStack(Items.LIGHTNING_ROD), "Transmitters", "TRANSMITTERS", sx, sy, w, h));
        apps.add(new AppButton(startX + size + gap, row3Y, size, size, new ItemStack(Items.RECOVERY_COMPASS), "Network ID's", "NETWORK", sx, sy, w, h));
        apps.add(new AppButton(startX + (size + gap) * 2, row3Y, size, size, new ItemStack(Items.REPEATER), "Settings", "SETTINGS", sx, sy, w, h));

        if (net.rdv88.redos.util.PermissionCache.isAdmin()) {
            int row4Y = row3Y + size + 25;
            apps.add(new AppButton(startX + size + gap, row4Y, size, size, new ItemStack(Items.COMMAND_BLOCK), "Admin", "ADMIN", sx, sy, w, h));
        }

        for (AppButton app : apps) adder.add(app);
    }

    @Override
    public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        if (Math.abs(scrollPos - targetScroll) > 0.1) {
            scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
        } else {
            scrollPos = targetScroll;
        }

        int currentScroll = (int)Math.round(scrollPos);
        int minY = sy + 15;
        int maxY = sy + 152;

        for (AppButton app : apps) {
            app.setY(sy + app.relY - currentScroll);
            app.visible = (app.getY() + app.getHeight() > minY - 20 && app.getY() < maxY + 20);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        // MENU Text removed for more space

        int barX = sx + w - 4;
        int barY = sy + 13;
        int barH = 139;
        g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
        
        double maxScroll = 160; 
        int handleH = 25;
        int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
        
        int y1 = Math.clamp(handleY, barY, barY + barH - handleH);
        int y2 = y1 + handleH;
        if (scrollPos >= maxScroll - 0.1) y2 = barY + barH;
        
        g.fill(barX - 1, y1, barX + 2, y2, 0xAAFFFFFF); 
    }

    @Override public void tick() {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    
    @Override 
    public boolean mouseClicked(double mouseX, double mouseY, int button, int sx, int sy, int w, int h) { 
        if (button == 0) {
            int barX = sx + w - 4;
            int barY = sy + 15;
            int barH = 137;
            int handleH = 25;
            double maxScroll = 160;
            int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));

            if (mouseX >= barX - 4 && mouseX <= barX + 6 && mouseY >= handleY - 2 && mouseY <= handleY + handleH + 2) {
                isDragging = true;
                dragStartY = mouseY;
                scrollStart = targetScroll;
                return true;
            }
        }
        return false; 
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int sx, int sy, int w, int h) {
        if (isDragging) {
            int barY = sy + 15;
            int barH = 137;
            int handleH = 25;
            double maxScroll = 160;
            
            double clickOffset = (mouseY - barY - (handleH / 2.0));
            double percentage = Math.clamp(clickOffset / (double)(barH - handleH), 0.0, 1.0);
            
            targetScroll = percentage * maxScroll;
            HandheldScreen.refreshApp();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button, int sx, int sy, int w, int h) {
        isDragging = false;
        return false;
    }
    
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int sx, int sy, int w, int h) { 
        targetScroll = Math.clamp(targetScroll - (verticalAmount * 40), 0, 160);
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
            int minY = sy + 13;
            int maxY = sy + 152;
            
            if (getY() + height < minY || getY() > maxY) return;
            g.enableScissor(sx, minY, sx + sw, maxY);
            
            int color = isHovered() ? 0xFF441111 : 0xFF220505;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            
            // Adjust item rendering for larger 42x42 button
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