package net.rdv88.redos.client.gui.screen.handheld;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public class HandheldShell {
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 200;

    public static void render(GuiGraphics g, int x, int y) {
        // 1. Main Physical Body (A shade darker: 1E1E1E)
        g.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF1E1E1E);
        
        // 2. Industrial Bevel (Light & Shadow for 3D depth)
        g.fill(x + 1, y + 1, x + GUI_WIDTH - 1, y + 2, 0x44FFFFFF); 
        g.fill(x + 1, y + 1, x + 2, y + GUI_HEIGHT - 1, 0x44FFFFFF); 
        g.fill(x + 1, y + GUI_HEIGHT - 2, x + GUI_WIDTH - 1, y + GUI_HEIGHT - 1, 0xAA000000); 
        g.fill(x + GUI_WIDTH - 2, y + 1, x + GUI_WIDTH - 1, y + GUI_HEIGHT - 1, 0xAA000000); 

        // 3. Corner Screws
        drawScrew(g, x + 3, y + 3);
        drawScrew(g, x + GUI_WIDTH - 5, y + 3);
        drawScrew(g, x + 3, y + GUI_HEIGHT - 5);
        drawScrew(g, x + GUI_WIDTH - 5, y + GUI_HEIGHT - 5);

        // 4. Power LED (Green, constant)
        g.fill(x + 8, y + 8, x + 12, y + 12, 0xFF00AA00); // Fixed Green Power LED
        
        // 5. Status LED (Red, blinking)
        int led = (System.currentTimeMillis() % 2000 < 1000) ? 0xFFFF0000 : 0xFF440000;
        g.fill(x + GUI_WIDTH - 12, y + 8, x + GUI_WIDTH - 8, y + 12, led);
        
        // 6. Aligned Speaker Slots (Moved 1px right for perfect symmetry)
        drawSpeakerSlots(g, x + 14, y + 75); 
        drawSpeakerSlots(g, x + GUI_WIDTH - 30, y + 75); 
        
        // 7. Detailed 3D D-Pad (INTACT - MIC CHECK)
        drawDPad(g, x + 20, y + 160); 
        
        // 8. Detailed Action Buttons (INTACT - MIC CHECK)
        drawActionButtons(g, x + GUI_WIDTH - 24, y + 160);
    }

    private static void drawScrew(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0xFF151515);
        g.fill(x, y, x + 1, y + 1, 0xFF444444);
    }

    private static void drawSpeakerSlots(GuiGraphics g, int x, int y) {
        for (int i = 0; i < 3; i++) {
            int sx = x + (i * 4);
            g.fill(sx, y, sx + 2, y + 40, 0xFF111111);
            g.fill(sx + 2, y, sx + 3, y + 40, 0x22FFFFFF);
        }
    }

    private static void drawDPad(GuiGraphics g, int x, int y) {
        g.fill(x - 14, y - 14, x + 14, y + 14, 0xAA000000); 
        g.fill(x - 6, y - 17, x + 6, y + 17, 0xAA000000); 
        g.fill(x - 17, y - 6, x + 17, y + 6, 0xAA000000);
        int bodyColor = 0xFF2A2A28;
        g.fill(x - 5, y - 16, x + 5, y + 16, bodyColor); 
        g.fill(x - 16, y - 5, x + 16, y + 5, bodyColor);
        g.fill(x - 5, y - 16, x + 5, y - 15, 0x44FFFFFF);
        g.fill(x - 16, y - 5, x - 5, y - 4, 0x44FFFFFF);
        g.fill(x + 5, y - 5, x + 16, y - 4, 0x44FFFFFF);
        g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF151515);
        g.fill(x - 1, y - 1, x + 1, y + 1, 0xFF000000);
        int arrow = 0xFF666666;
        g.fill(x - 1, y - 14, x + 1, y - 12, arrow); 
        g.fill(x - 1, y + 12, x + 1, y + 14, arrow);
        g.fill(x - 14, y - 1, x - 12, y + 1, arrow); 
        g.fill(x + 12, y - 1, x + 14, y + 1, arrow);
    }
    
    private static void drawActionButtons(GuiGraphics g, int x, int y) {
        int bright = 0xFFCC0000;
        drawSingleActionButton(g, x, y - 12, "Y", bright);
        drawSingleActionButton(g, x, y + 12, "A", bright);
        drawSingleActionButton(g, x - 12, y, "X", bright);
        drawSingleActionButton(g, x + 12, y, "B", bright);
    }
    
    private static void drawSingleActionButton(GuiGraphics g, int x, int y, String label, int color) {
        g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF111111); 
        g.fill(x - 3, y - 3, x + 4, y + 4, color); 
        g.fill(x - 1, y - 1, x + 2, y + 2, 0x44FFFFFF);
    }
}
