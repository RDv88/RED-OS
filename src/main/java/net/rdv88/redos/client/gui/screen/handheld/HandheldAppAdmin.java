package net.rdv88.redos.client.gui.screen.handheld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.network.payload.AdminActionPayload;
import java.util.Optional;

public class HandheldAppAdmin implements HandheldApp {
    private final Font font = Minecraft.getInstance().font;
    private EditBox tokenInput;
    private EditBox channelInput;
    private EditBox kickPlayerInput;
    private EditBox kickReasonInput;

    @Override
    public void init(int sx, int sy, int w, int h, WidgetAdder adder) {
        // Discord Section
        tokenInput = new EditBox(font, sx + 20, sy + 45, w - 40, 12, Component.literal("Bot Token"));
        tokenInput.setMaxLength(100);
        adder.add(tokenInput);

        channelInput = new EditBox(font, sx + 20, sy + 70, w - 40, 12, Component.literal("Channel ID"));
        channelInput.setMaxLength(30);
        adder.add(channelInput);

        // Server Management Section
        kickPlayerInput = new EditBox(font, sx + 20, sy + 110, 80, 12, Component.literal("Player Name"));
        kickPlayerInput.setMaxLength(16);
        adder.add(kickPlayerInput);

        kickReasonInput = new EditBox(font, sx + 105, sy + 110, w - 125, 12, Component.literal("Reason"));
        kickReasonInput.setMaxLength(50);
        adder.add(kickReasonInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {
        g.drawString(font, "> ADMIN COMMAND CENTER", sx + 5, sy + 18, 0xFFAA0000, false);

        // Discord Labels
        g.drawString(font, "Discord Bot Token (Sensitive)", sx + 20, sy + 35, 0xFF888888, false);
        g.drawString(font, "Discord Channel ID", sx + 20, sy + 60, 0xFF888888, false);

        // Server Management Labels
        g.drawString(font, "Server Management", sx + 20, sy + 100, 0xFF888888, false);
        
        int cx = sx + w / 2;
        g.drawCenteredString(font, "§8Tokens are never sent back to the handheld.", cx, sy + 135, 0xFF444444);
        g.drawCenteredString(font, "§8Check server logs for admin audits.", cx, sy + 145, 0xFF444444);

        // Help text for buttons
        if (kickPlayerInput.isFocused() || kickReasonInput.isFocused()) {
            g.drawCenteredString(font, "§ePress SAVE to Kick Player", cx, sy + 125, 0xFFFFFF00);
        } else if (tokenInput.isFocused() || channelInput.isFocused()) {
            g.drawCenteredString(font, "§ePress SAVE to Update Discord", cx, sy + 125, 0xFFFFFF00);
        }
    }

    @Override public void preRender(int mouseX, int mouseY, float delta, int sx, int sy, int w, int h) {}
    @Override public void tick() {}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) { return false; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }

    @Override
    public boolean isEditMode() {
        return (tokenInput != null && tokenInput.isFocused()) || 
               (channelInput != null && channelInput.isFocused()) ||
               (kickPlayerInput != null && kickPlayerInput.isFocused()) ||
               (kickReasonInput != null && kickReasonInput.isFocused());
    }

    @Override
    public Optional<net.minecraft.client.gui.components.events.GuiEventListener> getInitialFocus() {
        return Optional.ofNullable(tokenInput);
    }

    public void save() {
        if (tokenInput.isFocused() || channelInput.isFocused()) {
            if (!tokenInput.getValue().isEmpty()) {
                ClientPlayNetworking.send(new AdminActionPayload(AdminActionPayload.ActionType.SET_DISCORD_TOKEN, tokenInput.getValue(), ""));
                tokenInput.setValue(""); // Clear sensitive data immediately
            }
            if (!channelInput.getValue().isEmpty()) {
                ClientPlayNetworking.send(new AdminActionPayload(AdminActionPayload.ActionType.SET_DISCORD_CHANNEL, channelInput.getValue(), ""));
            }
        } else if (kickPlayerInput.isFocused() || kickReasonInput.isFocused()) {
            String target = kickPlayerInput.getValue();
            String reason = kickReasonInput.getValue();
            if (!target.isEmpty()) {
                ClientPlayNetworking.send(new AdminActionPayload(AdminActionPayload.ActionType.KICK_PLAYER, target, reason));
                kickPlayerInput.setValue("");
                kickReasonInput.setValue("");
            }
        }
    }

    public void back() {
        HandheldScreen.requestAppSwitch("HOME");
    }
}
