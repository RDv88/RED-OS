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
    private static int scrollOffset = 0;

    public HandheldAppNWID(List<SyncProfilesPayload.ProfileData> profiles, Set<String> activeIds) {
        this.profiles = profiles;
        this.activeIds = activeIds;
    }

    @Override
    public void init(int screenX, int screenY, int width, int height, WidgetAdder adder) {
        int screenCX = screenX + width / 2;
        int btnY = screenY + height - 14;

        if (isAdding || editingProfile != null) {
            setupInputFields(screenX, screenY, adder);
        } else {
            populateTable(screenX, screenY, width, adder);
            adder.add(new HandheldScreen.NavButton(screenCX - 10, screenY + 16, 20, 12, "+", b -> { 
                isAdding = true; 
                HandheldScreen.refreshApp(); 
            }, 0xFF00AA22));
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
        
        // Internal focus
        nameInput.setFocused(true);
    }

    @Override
    public Optional<GuiEventListener> getInitialFocus() {
        return (isAdding || editingProfile != null) ? Optional.ofNullable(nameInput) : Optional.empty();
    }

    private void populateTable(int sx, int sy, int w, WidgetAdder adder) {
        int listY = sy + 33;
        int maxScroll = Math.max(0, profiles.size() - 6);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        if (scrollOffset > 0) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY - 12, 15, 10, "▲", b -> { scrollOffset--; HandheldScreen.refreshApp(); }, 0xFF444444));
        if (profiles.size() > scrollOffset + 6) adder.add(new HandheldScreen.NavButton(sx + w - 20, listY + 123, 15, 10, "▼", b -> { scrollOffset++; HandheldScreen.refreshApp(); }, 0xFF444444));

        for (int i = 0; i < 6; i++) {
            int index = i + scrollOffset;
            if (index >= profiles.size()) break;
            SyncProfilesPayload.ProfileData p = profiles.get(index);
            int rowY = listY + (i * 20);
            boolean isActive = activeIds.contains(p.id());
            boolean isDefault = p.id().equals("00000");

            adder.add(new HandheldScreen.RowButton(sx + 5, rowY, w - 50, 18, 
                new SyncHandheldDataPayload.DeviceEntry(BlockPos.ZERO, p.id(), p.name(), "PROFILE", 100, isActive ? "Enabled" : "Disabled", false, false, false, false, true, 10, 20, 0, 0), b -> {
                    if (activeIds.contains(p.id())) activeIds.remove(p.id()); else activeIds.add(p.id());
                    HandheldScreen.updateNetworkIds(); HandheldScreen.refreshApp();
                }));

            if (!isDefault) {
                // Perfect height alignment rowY + 2
                adder.add(new HandheldScreen.NavButton(sx + w - 37, rowY + 2, 14, 14, "E", b -> { editingProfile = p; HandheldScreen.refreshApp(); }, 0xFF0055AA));
                adder.add(new HandheldScreen.NavButton(sx + w - 20, rowY + 2, 14, 14, "X", b -> {
                    profiles.remove(p); activeIds.remove(p.id());
                    ClientPlayNetworking.send(new SyncProfilesPayload(new ArrayList<>(profiles)));
                    HandheldScreen.updateNetworkIds(); HandheldScreen.refreshApp();
                }, 0xFFAA0000));
            }
        }
    }

    @Override public boolean isEditMode() { return isAdding || editingProfile != null; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int screenX, int screenY, int width, int height) {
        int screenCX = screenX + width / 2;
        if (isAdding || editingProfile != null) {
            g.drawCenteredString(font, editingProfile != null ? "> EDIT PROFILE" : "> ADD PROFILE", screenCX, screenY + 18, 0xFFAA0000);
            g.drawString(font, "Name:", screenX + 10, screenY + 70, 0xFFAAAAAA, false);
            g.drawString(font, "NW-ID:", screenX + 10, screenY + 100, 0xFFAAAAAA, false);
        } else {
            g.drawString(font, "> NETWORK ID'S", screenX + 5, screenY + 18, 0xFFAA0000, false);
            // Statistiek op x + 139 zoals gevraagd
            String stat = String.format("%02d SAVED", profiles.size());
            g.drawString(font, stat, screenX + 139, screenY + 18, 0xFF888888, false);
        }
    }

    @Override public void tick() {}
    @Override 
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { 
        if (isEditMode()) {
            if (event.key() == 257 || event.key() == 335) { save(); return true; }
            if (nameInput != null && nameInput.isFocused()) return nameInput.keyPressed(event);
            if (idInput != null && idInput.isFocused()) return idInput.keyPressed(event);
        }
        return false; 
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (isEditMode()) return false;
        int maxScroll = Math.max(0, profiles.size() - 6);
        if (v < 0) scrollOffset = Math.min(scrollOffset + 1, maxScroll);
        else if (v > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        HandheldScreen.refreshApp();
        return true;
    }
    public void back() {
        if (isAdding || editingProfile != null) { isAdding = false; editingProfile = null; HandheldScreen.refreshApp(); }
        else { HandheldScreen.requestAppSwitch("HOME"); }
    }
    public void save() {
        String id = idInput.getValue(); String name = nameInput.getValue();
        if (!name.isEmpty() && id.length() == 5 && !id.equals("00000")) {
            if (editingProfile != null) { profiles.remove(editingProfile); activeIds.remove(editingProfile.id()); }
            profiles.add(new SyncProfilesPayload.ProfileData(name, id));
            ClientPlayNetworking.send(new SyncProfilesPayload(new ArrayList<>(profiles)));
            clearState(); HandheldScreen.refreshApp();
        } else if (id.equals("00000")) { HandheldScreen.showToast("ID 00000 RESERVED"); }
    }
    public static void clearState() { isAdding = false; editingProfile = null; }
}