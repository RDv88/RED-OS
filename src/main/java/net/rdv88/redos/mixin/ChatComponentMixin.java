package net.rdv88.redos.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void redos$hideChatWhenHandheldChatOpen(GuiGraphics graphics, Font font, int tickCount, int mouseX, int mouseY, boolean focused, boolean isChatScreen, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof HandheldScreen hs) {
            if (hs.getCurrentAppName().equals("CHAT")) {
                ci.cancel();
            }
        }
    }
}
