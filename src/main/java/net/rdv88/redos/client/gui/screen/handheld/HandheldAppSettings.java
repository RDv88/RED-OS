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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HandheldAppSettings implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncProfilesPayload.ProfileData> profiles;
    private final Set<String> activeIds;
    
    private static int scrollOffset = 0;
    private static boolean isConfirmingReset = false;
    private static boolean isTroubleshooting = false;

    public HandheldAppSettings(List<SyncProfilesPayload.ProfileData> profiles, Set<String> activeIds) {
        this.profiles = profiles;
        this.activeIds = activeIds;
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        int cx = sx + w / 2;
        int listY = sy + 32 - (scrollOffset * 20);
        Minecraft mc = Minecraft.getInstance();

        if (scrollOffset > 0) {
            adder.add(new HandheldScreen.NavButton(sx + w - 20, sy + 15, 15, 10, "▲", b -> { scrollOffset--; HandheldScreen.refreshApp(); }, 0xFF444444));
        }
        if (scrollOffset < 2) {
            adder.add(new HandheldScreen.NavButton(sx + w - 20, sy + h - 28, 15, 10, "▼", b -> { scrollOffset++; HandheldScreen.refreshApp(); }, 0xFF444444));
        }

        if (isConfirmingReset) {
            setupConfirmReset(sx, sy, cx, adder);
        } else if (isTroubleshooting) {
            setupTroubleshooting(sx, sy, cx, adder);
        } else {
            setupMainSettings(sx, sy, cx, h, listY, adder, mc);
        }
    }

    private void setupConfirmReset(int sx, int sy, int cx, WidgetAdder adder) {
        adder.add(new HandheldScreen.NavButton(cx - 65, sy + 80, 60, 16, "YES", b -> {
            performReset();
            isConfirmingReset = false;
            HandheldScreen.requestAppSwitch("HOME");
        }, 0xFF880000));
        adder.add(new HandheldScreen.NavButton(cx + 5, sy + 80, 60, 16, "NO", b -> {
            isConfirmingReset = false;
            HandheldScreen.refreshApp();
        }, 0xFF444444));
    }

    private void setupTroubleshooting(int sx, int sy, int cx, WidgetAdder adder) {
        adder.add(new HandheldScreen.NavButton(cx - 75, sy + 60, 150, 20, "PURGE GHOST LIGHTS", b -> {
            ClientPlayNetworking.send(new PurgeGhostLightsPayload());
            HandheldScreen.showToast("SIGNAL SENT");
        }, 0xFFAA0000));
    }

    private void setupMainSettings(int sx, int sy, int cx, int h, int listY, WidgetAdder adder, Minecraft mc) {
        int resetBtnY = listY + 12;
        if (resetBtnY > sy + 12 && resetBtnY < sy + h - 20) {
            adder.add(new HandheldScreen.NavButton(cx - 75, resetBtnY, 150, 16, "RESET HANDHELD DATA", b -> {
                isConfirmingReset = true;
                HandheldScreen.refreshApp();
            }, 0xFFAA0000));
        }

        int problemsBtnY = resetBtnY + 20;
        if (problemsBtnY > sy + 12 && problemsBtnY < sy + h - 20) {
            adder.add(new HandheldScreen.NavButton(cx - 75, problemsBtnY, 150, 16, "PROBLEMS?", b -> {
                isTroubleshooting = true;
                HandheldScreen.refreshApp();
            }, 0xFF555555));
        }

        int linkTitleY = resetBtnY + 50; 
        int row1Y = linkTitleY + 15;
        int row2Y = row1Y + 20;

        if (row1Y > sy + 12 && row1Y < sy + h - 20) {
            adder.add(new HandheldScreen.NavButton(cx - 75, row1Y, 70, 16, "VIDEO", b -> mc.setScreen(new VideoSettingsScreen(mc.screen, mc, mc.options)), 0xFF0055AA));
            adder.add(new HandheldScreen.NavButton(cx + 5, row1Y, 70, 16, "AUDIO", b -> mc.setScreen(new SoundOptionsScreen(mc.screen, mc.options)), 0xFF0055AA));
        }
        
        if (row2Y > sy + 12 && row2Y < sy + h - 20) {
            adder.add(new HandheldScreen.NavButton(cx - 75, row2Y, 70, 16, "PACKS", b -> {
                Screen current = mc.screen;
                mc.setScreen(new PackSelectionScreen(mc.getResourcePackRepository(), repository -> {
                    mc.options.resourcePacks.clear();
                    mc.options.resourcePacks.addAll(repository.getSelectedIds());
                    mc.options.save();
                    mc.setScreen(current); 
                }, mc.getResourcePackDirectory(), Component.translatable("resourcePack.title")));
            }, 0xFF0055AA));
            adder.add(new HandheldScreen.NavButton(cx + 5, row2Y, 70, 16, "KEYS", b -> mc.setScreen(new KeyBindsScreen(mc.screen, mc.options)), 0xFF0055AA));
        }
    }

    private void performReset() {
        profiles.clear(); 
        profiles.add(new SyncProfilesPayload.ProfileData("Default", "00000"));
        activeIds.clear(); 
        activeIds.add("00000");
        HandheldScreen.updateNetworkIds(); 
        ClientPlayNetworking.send(new SyncProfilesPayload(new ArrayList<>(profiles)));
        HandheldScreen.showToast("OS REINSTALLED");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        int listY = sy + 32 - (scrollOffset * 20);
        
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
            g.enableScissor(sx, sy + 28, sx + w, sy + h - 18);
            g.drawCenteredString(font, "RED-OS CORE SYSTEM", cx, listY, 0xFFAAAAAA);
            g.drawCenteredString(font, "GAME SHORTCUTS", cx, listY + 62, 0xFFAAAAAA);
            g.disableScissor();
        }
    }

    @Override public void tick() {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (isConfirmingReset || isTroubleshooting) return false;
        if (v < 0) scrollOffset = Math.min(2, scrollOffset + 1); 
        else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        HandheldScreen.refreshApp(); return true;
    }

    public void back() {
        if (isConfirmingReset || isTroubleshooting) {
            isConfirmingReset = false;
            isTroubleshooting = false;
            HandheldScreen.refreshApp();
        } else {
            HandheldScreen.requestAppSwitch("HOME");
        }
    }
    
    public static void clearState() { isConfirmingReset = false; isTroubleshooting = false; scrollOffset = 0; }
}
