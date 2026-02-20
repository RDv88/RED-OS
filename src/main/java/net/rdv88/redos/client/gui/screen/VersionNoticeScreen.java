package net.rdv88.redos.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public class VersionNoticeScreen extends Screen {
    private final Screen parent;
    private final String infoMsg;
    private final boolean isMandatory;
    private final Consumer<Boolean> callback;

    public VersionNoticeScreen(Screen parent, String title, String info, boolean mandatory, Consumer<Boolean> callback) {
        super(Component.literal("RED-OS System Notice"));
        this.parent = parent;
        this.infoMsg = info;
        this.isMandatory = mandatory;
        this.callback = callback;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Smaller, more elegant button at the bottom to avoid text overlap
        String btnText = "RETURN TO MENU";
        this.addRenderableWidget(new AlertButton(cx - 60, cy + 85, 120, 16, btnText, b -> {
            if (callback != null) {
                callback.accept(false);
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }, 0xFF444444)); 
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Deep background
        g.fill(0, 0, this.width, this.height, 0x99050000);
        
        int cx = this.width / 2;
        int cy = this.height / 2;
        int boxW = 300;
        int boxH = 220; 

        // Terminal Box
        g.fill(cx - boxW/2, cy - boxH/2, cx + boxW/2, cy + boxH/2, 0xEE0A0505);
        g.renderOutline(cx - boxW/2, cy - boxH/2, boxW, boxH, 0xFF660000); 
        g.renderOutline(cx - boxW/2 + 2, cy - boxH/2 + 2, boxW - 4, boxH - 4, 0x22AA0000);

        // Header Title: Updated to UPDATE INFORMATION
        g.drawCenteredString(this.font, "§c§l[ RED-OS ] §f- §cUPDATE INFORMATION", cx, cy - boxH/2 + 12, 0xFFFFFFFF);
        g.fill(cx - 130, cy - boxH/2 + 25, cx + 130, cy - boxH/2 + 26, 0x44AAAAAA);

        // Briefing Text
        String[] lines = infoMsg.split("\n");
        int textY = cy - 75; // Shifted up slightly for better clearance
        for (String line : lines) {
            g.drawCenteredString(this.font, line, cx, textY, 0xFFFFFFFF);
            textY += 12;
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isMandatory;
    }

    private class AlertButton extends Button {
        private final int hoverColor;

        public AlertButton(int x, int y, int w, int h, String text, OnPress press, int hColor) {
            super(x, y, w, h, Component.literal(text), press, DEFAULT_NARRATION);
            this.hoverColor = hColor;
        }

        @Override
        protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int color = isHovered() ? hoverColor : 0xFF220505;
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFF440000;
            g.fill(getX(), getY(), getX() + width, getY() + height, color);
            g.renderOutline(getX(), getY(), width, height, borderColor);
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
        }
    }
}
