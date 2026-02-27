package net.rdv88.redos.client.gui.screen.handheld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.*;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.SyncProfilesPayload;
import net.rdv88.redos.network.payload.PurgeGhostLightsPayload;
import net.rdv88.redos.network.payload.PurgeZombieDronesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HandheldAppSettings implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncProfilesPayload.ProfileData> profiles;
    private final Set<String> activeIds;
    
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;
    private static boolean isConfirmingReset = false;
    private static boolean isTroubleshooting = false;

    public HandheldAppSettings(List<SyncProfilesPayload.ProfileData> profiles, Set<String> activeIds) {
        this.profiles = profiles;
        this.activeIds = activeIds;
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        int cx = sx + w / 2;
        int listY = (int)(sy + 32 - scrollPos);
        Minecraft mc = Minecraft.getInstance();

        if (isConfirmingReset) {
            setupConfirmReset(sx, sy, cx, adder);
        } else if (isTroubleshooting) {
            setupTroubleshooting(sx, sy, cx, adder);
        } else {
            setupMainSettings(sx, sy, cx, h, listY, adder, mc);
        }
    }

    private void setupConfirmReset(int sx, int sy, int cx, WidgetAdder adder) {
        adder.add(new HandheldScreen.NavButton(cx - 65, sy + 80, 60, 16, "YES", b -> { performReset(); isConfirmingReset = false; HandheldScreen.requestAppSwitch("HOME"); }, 0xFF880000));
        adder.add(new HandheldScreen.NavButton(cx + 5, sy + 80, 60, 16, "NO", b -> { isConfirmingReset = false; HandheldScreen.refreshApp(); }, 0xFF444444));
    }

    private void setupTroubleshooting(int sx, int sy, int cx, WidgetAdder adder) {
        adder.add(new HandheldScreen.NavButton(cx - 75, sy + 50, 150, 20, "PURGE GHOST LIGHTS", b -> { ClientPlayNetworking.send(new net.rdv88.redos.network.payload.PurgeGhostLightsPayload()); HandheldScreen.showToast("SIGNAL SENT"); }, 0xFFAA0000));
        adder.add(new HandheldScreen.NavButton(cx - 75, sy + 75, 150, 20, "PURGE ZOMBIE DRONES", b -> { ClientPlayNetworking.send(new net.rdv88.redos.network.payload.PurgeZombieDronesPayload()); HandheldScreen.showToast("SIGNAL SENT"); }, 0xFFAA5500));
    }

    private void setupMainSettings(int sx, int sy, int cx, int h, int listY, WidgetAdder adder, Minecraft mc) {
        int resetBtnY = listY + 12;
        if (resetBtnY > sy + 15 && resetBtnY < sy + 152) {
            adder.add(new HandheldScreen.NavButton(cx - 75, resetBtnY, 150, 16, "RESET HANDHELD DATA", b -> { isConfirmingReset = true; HandheldScreen.refreshApp(); }, 0xFFAA0000, sy + 30, sy + 152));
        }

        int problemsBtnY = resetBtnY + 20;
        if (problemsBtnY > sy + 15 && problemsBtnY < sy + 152) {
            adder.add(new HandheldScreen.NavButton(cx - 75, problemsBtnY, 150, 16, "PROBLEMS?", b -> { isTroubleshooting = true; HandheldScreen.refreshApp(); }, 0xFF555555, sy + 30, sy + 152));
        }

        int row1Y = resetBtnY + 65;
        int row2Y = row1Y + 20;

        if (row1Y > sy + 15 && row1Y < sy + 152) {
            adder.add(new HandheldScreen.NavButton(cx - 75, row1Y, 70, 16, "VIDEO", b -> mc.setScreen(new VideoSettingsScreen(mc.screen, mc, mc.options)), 0xFF0055AA, sy + 30, sy + 152));
            adder.add(new HandheldScreen.NavButton(cx + 5, row1Y, 70, 16, "AUDIO", b -> mc.setScreen(new SoundOptionsScreen(mc.screen, mc.options)), 0xFF0055AA, sy + 30, sy + 152));
        }
        
        if (row2Y > sy + 15 && row2Y < sy + 152) {
            adder.add(new HandheldScreen.NavButton(cx - 75, row2Y, 70, 16, "PACKS", b -> {
                Screen current = mc.screen;
                mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), repository -> {
                    mc.options.resourcePacks.clear(); mc.options.resourcePacks.addAll(repository.getSelectedIds()); mc.options.save(); mc.setScreen(current); 
                }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
            }, 0xFF0055AA, sy + 30, sy + 152));
            adder.add(new HandheldScreen.NavButton(cx + 5, row2Y, 70, 16, "KEYS", b -> mc.setScreen(new KeyBindsScreen(mc.screen, mc.options)), 0xFF0055AA, sy + 30, sy + 152));
        }
    }

    private void performReset() {
        profiles.clear(); profiles.add(new SyncProfilesPayload.ProfileData("Default", "00000"));
        activeIds.clear(); activeIds.add("00000");
        HandheldScreen.updateNetworkIds(); 
        ClientPlayNetworking.send(new SyncProfilesPayload(new ArrayList<>(profiles)));
        HandheldScreen.showToast("OS REINSTALLED");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        int listY = (int)(sy + 32 - scrollPos);
        
        if (isConfirmingReset) {
            g.drawString(font, "> SETTINGS", sx + 5, sy + 18, 0xFFAA0000, false);
            g.drawCenteredString(font, "RESET HANDHELD DATA?", cx, sy + 50, 0xFFFFFFFF);
            g.drawCenteredString(font, "All local profiles will be lost.", cx, sy + 62, 0xFFAAAAAA);
        } else if (isTroubleshooting) {
            g.drawString(font, "> TROUBLESHOOTING", sx + 5, sy + 18, 0xFFAA0000, false);
            g.drawCenteredString(font, "System Diagnosis Tools", cx, sy + 35, 0xFF888888);
            g.drawCenteredString(font, "Use these tools to fix common", cx, sy + 100, 0xFF666666);
            g.drawCenteredString(font, "environment issues.", cx, sy + 112, 0xFF666666);
        } else {
            g.drawString(font, "> SETTINGS", sx + 5, sy + 18, 0xFFAA0000, false);
            g.enableScissor(sx + 5, sy + 30, sx + w - 5, sy + 152);
            g.drawCenteredString(font, "RED-OS CORE SYSTEM", cx, listY, 0xFFAAAAAA);
            g.drawCenteredString(font, "GAME SHORTCUTS", cx, listY + 62, 0xFFAAAAAA);
            g.disableScissor();
            drawScrollbar(g, sx, sy, w, h);
        }
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int w, int h) {
        int totalH = 150; 
        int viewH = 122; // sy+30 tot sy+152
        if (totalH > viewH) {
            int barX = sx + w - 4; int barY = sy + 30; int barH = 122;
            g.fill(barX, barY, barX + 1, barY + barH, 0x33FFFFFF); 
            double maxScroll = Math.max(1, totalH - viewH); 
            int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
            int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
            int y1 = Math.clamp(handleY, barY, barY + barH - handleH);
            int y2 = y1 + handleH;
            if (scrollPos >= maxScroll - 0.1) y2 = barY + barH;
            g.fill(barX - 1, y1, barX + 2, y2, 0xAAFFFFFF); 
        }
    }

    @Override public void tick() {}

    @Override
    public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        if (isConfirmingReset || isTroubleshooting) return;
        int totalH = 150;
        int viewH = 122;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) {
            scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
            HandheldScreen.refreshApp();
        }
        else scrollPos = targetScroll;
    }
    
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int w, int h) { 
        if (isConfirmingReset || isTroubleshooting) return false;
        if (button == 0) {
            int barX = sx + w - 4; int barY = sy + 30; int barH = 122;
            int totalH = 150; int viewH = 122;
            if (totalH > viewH) {
                double maxScroll = Math.max(1, totalH - viewH); 
                int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
                int handleY = (int)(barY + (scrollPos / maxScroll) * (barH - handleH));
                if (mx >= barX - 4 && mx <= barX + 6 && my >= handleY - 2 && my <= handleY + handleH + 2) { isDragging = true; return true; }
            }
        }
        return false; 
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int sx, int sy, int w, int h) {
        if (isDragging) {
            int barY = sy + 30; int barH = 122;
            int totalH = 150; int viewH = 122;
            double maxScroll = Math.max(1, totalH - viewH);
            int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
            double clickOffset = (my - barY - (handleH / 2.0));
            double percentage = Math.clamp(clickOffset / (double)(barH - handleH), 0.0, 1.0);
            targetScroll = percentage * maxScroll;
            HandheldScreen.refreshApp(); return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int button, int sx, int sy, int w, int h) { isDragging = false; return false; }
    
    @Override public boolean mouseScrolled(double mx, double my, double h, double v, int sx, int sy, int sw, int sh) {
        if (isConfirmingReset || isTroubleshooting) return false;
        int totalH = 150; int viewH = 122;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll - (v * 20), 0, maxScroll);
        HandheldScreen.refreshApp(); return true;
    }

    public void back() { if (isConfirmingReset || isTroubleshooting) { isConfirmingReset = false; isTroubleshooting = false; HandheldScreen.refreshApp(); } else { HandheldScreen.requestAppSwitch("HOME"); } }
    public static void clearState() { isConfirmingReset = false; isTroubleshooting = false; scrollPos = 0; targetScroll = 0; }
}
