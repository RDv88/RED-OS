package net.rdv88.redos.client.gui.screen.handheld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.SyncProfilesPayload;
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;

public class HandheldAppNWID implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private final List<SyncProfilesPayload.ProfileData> profiles;
    private final Set<String> activeIds;
    
    private static boolean isAdding = false;
    private static SyncProfilesPayload.ProfileData editingProfile = null;
    private EditBox nameInput;
    private EditBox idInput;
    private static double scrollPos = 0;
    private static double targetScroll = 0;
    private static boolean isDragging = false;

    public HandheldAppNWID(List<SyncProfilesPayload.ProfileData> profiles, Set<String> activeIds) {
        this.profiles = profiles;
        this.activeIds = activeIds;
    }

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        int screenCX = sx + w / 2;
        if (isAdding || editingProfile != null) {
            setupInputFields(sx, sy, adder);
        } else {
            populateTable(sx, sy, w, adder);
            adder.add(new HandheldScreen.NavButton(screenCX - 10, sy + 16, 20, 12, "+", b -> { isAdding = true; HandheldScreen.refreshApp(); }, 0xFF00AA22));
        }
    }

    private void setupInputFields(int sx, int sy, WidgetAdder adder) {
        this.nameInput = new EditBox(font, sx + 55, sy + 65, 160, 18, Component.literal("Name"));
        this.nameInput.setMaxLength(10);
        if (editingProfile != null) nameInput.setValue(editingProfile.name());
        adder.add(nameInput);
        this.idInput = new EditBox(font, sx + 55, sy + 95, 160, 18, Component.literal("ID"));
        this.idInput.setMaxLength(5);
        if (editingProfile != null) idInput.setValue(editingProfile.id());
        adder.add(idInput);
        nameInput.setFocused(true);
    }

    @Override public Optional<GuiEventListener> getInitialFocus() {
        return (isAdding || editingProfile != null) ? Optional.ofNullable(nameInput) : Optional.empty();
    }

    private void populateTable(int sx, int sy, int w, WidgetAdder adder) {
        int listY = sy + 33;
        for (int i = 0; i < profiles.size(); i++) {
            SyncProfilesPayload.ProfileData p = profiles.get(i);
            int rowY = (int)(listY + (i * 20) - scrollPos);
            if (rowY > sy + 15 && rowY < sy + 152) {
                boolean isActive = activeIds.contains(p.id());
                boolean isDefault = p.id().equals("00000");
                adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 50, 18, 
                    new SyncHandheldDataPayload.DeviceEntry(BlockPos.ZERO, p.id(), p.name(), "PROFILE", 100, isActive ? "Enabled" : "Disabled", false, false, false, false, true, 10, 20, 0, 0), sy + 33, sy + 152, b -> {
                        if (activeIds.contains(p.id())) activeIds.remove(p.id()); else activeIds.add(p.id());
                        HandheldScreen.updateNetworkIds(); HandheldScreen.refreshApp();
                    }));
                if (!isDefault) {
                    adder.add(new HandheldScreen.NavButton(sx + w - 39, rowY + 2, 14, 14, "E", b -> { editingProfile = p; HandheldScreen.refreshApp(); }, 0xFF0055AA, sy + 33, sy + 152));
                    adder.add(new HandheldScreen.NavButton(sx + w - 22, rowY + 2, 14, 14, "X", b -> {
                        profiles.remove(p); activeIds.remove(p.id());
                        ClientPlayNetworking.send(new SyncProfilesPayload(new ArrayList<>(profiles)));
                        HandheldScreen.updateNetworkIds(); HandheldScreen.refreshApp();
                    }, 0xFFAA0000, sy + 33, sy + 152));
                }
            }
        }
    }

    @Override public boolean isEditMode() { 
        return (nameInput != null && nameInput.isFocused()) || (idInput != null && idInput.isFocused()); 
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        int cx = sx + w / 2;
        if (isAdding || editingProfile != null) {
            g.drawCenteredString(font, editingProfile != null ? "> EDIT PROFILE" : "> ADD PROFILE", cx, sy + 18, 0xFFAA0000);
            g.drawString(font, "Name:", sx + 10, sy + 70, 0xFFAAAAAA, false);
            g.drawString(font, "NW-ID:", sx + 10, sy + 100, 0xFFAAAAAA, false);
        } else {
            g.drawString(font, "> NETWORK ID'S", sx + 5, sy + 18, 0xFFAA0000, false);
            String stat = String.format("%02d SAVED", profiles.size());
            g.drawString(font, stat, sx + 139, sy + 18, 0xFF888888, false);
            drawScrollbar(g, sx, sy, w, h, profiles.size());
        }
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy, int w, int h, int count) {
        int totalH = count * 20;
        int viewH = 119; // sy+33 tot sy+152
        if (totalH > viewH) {
            int barX = sx + w - 4; int barY = sy + 33; int barH = 119;
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
        if (isAdding || editingProfile != null) return;
        int totalH = profiles.size() * 20;
        int viewH = 119;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll, 0, maxScroll);
        if (Math.abs(scrollPos - targetScroll) > 0.1) scrollPos = scrollPos + (targetScroll - scrollPos) * 0.3;
        else scrollPos = targetScroll;
    }
    
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
        if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        if (isAdding || editingProfile != null) { if (event.key() == 257 || event.key() == 335) { save(); return true; } }
        return false; 
    }
    
    @Override public boolean mouseClicked(double mx, double my, int button, int sx, int sy, int w, int h) { 
        if (isAdding || editingProfile != null) {
            if (nameInput != null) nameInput.setFocused(mx >= nameInput.getX() && mx < nameInput.getX() + nameInput.getWidth() && my >= nameInput.getY() && my < nameInput.getY() + nameInput.getHeight());
            if (idInput != null) idInput.setFocused(mx >= idInput.getX() && mx < idInput.getX() + idInput.getWidth() && my >= idInput.getY() && my < idInput.getY() + idInput.getHeight());
            return false;
        }
        if (button == 0) {
            int barX = sx + w - 4; int barY = sy + 33; int barH = 119;
            int totalH = profiles.size() * 20;
            int viewH = 119;
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
            int barY = sy + 33; int barH = 119;
            int totalH = profiles.size() * 20;
            int viewH = 119;
            double maxScroll = Math.max(1, totalH - viewH);
            int handleH = Math.max(10, (int)((double)viewH / totalH * barH));
            double clickOffset = (my - barY - (handleH / 2.0));
            double percentage = Math.clamp(clickOffset / (double)(barH - handleH), 0.0, 1.0);
            targetScroll = percentage * maxScroll;
            HandheldScreen.refreshApp(); return true;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int b, int sx, int sy, int w, int h) { isDragging = false; return false; }
    
    @Override public boolean mouseScrolled(double mx, double my, double h, double v, int sx, int sy, int sw, int sh) {
        if (isAdding || editingProfile != null) return false;
        int totalH = profiles.size() * 20;
        int viewH = 119;
        double maxScroll = Math.max(0, totalH - viewH);
        targetScroll = Math.clamp(targetScroll - (v * 20), 0, maxScroll);
        HandheldScreen.refreshApp(); return true;
    }
    public void back() {
        if (isAdding || editingProfile != null) { isAdding = false; editingProfile = null; HandheldScreen.refreshApp(); }
        else { HandheldScreen.requestAppSwitch("HOME"); }
    }
    public void save() {
        String id = idInput.getValue().trim(); String name = nameInput.getValue().trim();
        if (!name.isEmpty() && id.length() == 5 && !id.equals("00000")) {
            if (editingProfile != null) { profiles.remove(editingProfile); activeIds.remove(editingProfile.id()); }
            profiles.add(new SyncProfilesPayload.ProfileData(name, id));
            ClientPlayNetworking.send(new SyncProfilesPayload(new ArrayList<>(profiles)));
            HandheldScreen.showToast("§aProfile Saved");
            clearState(); HandheldScreen.refreshApp();
        } else if (id.equals("00000")) { HandheldScreen.showToast("§cID 00000 RESERVED"); }
    }
    public static void clearState() { isAdding = false; editingProfile = null; scrollPos = 0; targetScroll = 0; }
}
