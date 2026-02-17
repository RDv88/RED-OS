package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;

public class HandheldAppHome implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private static int pageOffset = 0; // 0 for first 6 apps, 1 for next set

    @Override
    public void init(int screenX, int screenY, int width, int height, WidgetAdder adder) {
        int screenCX = screenX + width / 2;
        int size = 36;
        int gap = 30;
        int rowWidth = (3 * size + 2 * gap);
        int startX = screenCX - (rowWidth / 2); 
        int row1Y = screenY + 35;
        int row2Y = row1Y + size + 25;

        if (pageOffset == 0) {
            adder.add(new AppButton(startX, row1Y, size, size, new ItemStack(Items.RECOVERY_COMPASS), "Network ID's", "NETWORK"));
            adder.add(new AppButton(startX + size + gap, row1Y, size, size, new ItemStack(Items.LIGHTNING_ROD), "Transmitters", "TRANSMITTERS"));
            adder.add(new AppButton(startX + (size + gap) * 2, row1Y, size, size, new ItemStack(Items.SPYGLASS), "Camera", "CAMERA"));
            
            adder.add(new AppButton(startX, row2Y, size, size, new ItemStack(Items.DAYLIGHT_DETECTOR), "Sensors", "SENSORS"));
            adder.add(new AppButton(startX + size + gap, row2Y, size, size, new ItemStack(Items.REDSTONE_TORCH), "Triggers", "TRIGGERS"));
            adder.add(new AppButton(startX + (size + gap) * 2, row2Y, size, size, new ItemStack(Items.REPEATER), "Settings", "SETTINGS"));
            
            // NEXT PAGE BUTTON (DOWN)
            adder.add(new HandheldScreen.NavButton(screenX + width - 20, screenY + height - 35, 15, 15, "▼", b -> { pageOffset = 1; HandheldScreen.refreshApp(); }, 0xFF444444));
        } else {
            // High-Tech Quantum Link App
            adder.add(new AppButton(startX, row1Y, size, size, new ItemStack(Items.ECHO_SHARD), "Quantum Link", "HIGHTECH"));
            
            // Logistic Hub App
            adder.add(new AppButton(startX + size + gap, row1Y, size, size, new ItemStack(Items.CHEST), "Logistic Hub", "LOGISTICS"));
            
            // PREVIOUS PAGE BUTTON (UP)
            adder.add(new HandheldScreen.NavButton(screenX + width - 20, screenY + 25, 15, 15, "▲", b -> { pageOffset = 0; HandheldScreen.refreshApp(); }, 0xFF444444));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int screenX, int screenY, int width, int height) {
        g.drawString(font, "> MENU " + (pageOffset + 1) + "/2", screenX + 5, screenY + 18, 0xFFAA0000, false);
    }

    @Override public void tick() {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { 
        if (verticalAmount < 0 && pageOffset == 0) { pageOffset = 1; HandheldScreen.refreshApp(); return true; }
        if (verticalAmount > 0 && pageOffset == 1) { pageOffset = 0; HandheldScreen.refreshApp(); return true; }
        return false; 
    }

    private void drawMarqueeText(GuiGraphics g, String text, int x, int y, int width, int color, float scale) {
        int textWidth = (int)(font.width(text) * scale);
        double time = System.currentTimeMillis() / 1500.0;
        int offset = 0;
        
        if (textWidth > width) {
            int maxOffset = textWidth - width;
            offset = (int) ((Math.sin(time * 2.0) * 0.5 + 0.5) * maxOffset);
        } else {
            x = x + (width - textWidth) / 2;
        }

        org.joml.Matrix3x2f oldMatrix = new org.joml.Matrix3x2f();
        g.pose().get(oldMatrix);
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        
        if (textWidth > width) g.enableScissor(x, y, x + width, y + 10);
        g.drawString(font, text, (int)(- (offset / scale)), 0, color, false);
        if (textWidth > width) g.disableScissor();
        
        g.pose().set(oldMatrix);
    }

    private class AppButton extends Button {
        private final ItemStack icon; 
        private final String label;
        private final String action;

        public AppButton(int x, int y, int w, int h, ItemStack i, String l, String a) { 
            super(x, y, w, h, Component.empty(), b -> HandheldScreen.requestAppSwitch(a), DEFAULT_NARRATION); 
            this.icon = i; this.label = l; this.action = a;
        }

        @Override public void onPress(net.minecraft.client.input.InputWithModifiers modifiers) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xFF441111 : 0xFF220505);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
            drawMarqueeText(g, label, getX() - 10, getY() + height + 4, width + 20, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA, 0.85f);
        }
    }
}
