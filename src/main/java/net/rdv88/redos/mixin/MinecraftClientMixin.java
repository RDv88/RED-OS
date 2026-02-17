package net.rdv88.redos.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.rdv88.redos.Redos;
import net.rdv88.redos.RedosClient;
import net.rdv88.redos.client.gui.screen.VersionNoticeScreen;
import net.rdv88.redos.util.UpdateChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof DisconnectedScreen ds) {
            String title = ds.getTitle().getString().toLowerCase();
            
            if (title.contains("redos") || title.contains("registry")) {
                Minecraft client = Minecraft.getInstance();
                String latest = UpdateChecker.getLatestRemoteVersion();
                String serverVer = RedosClient.getLastServerVersion();

                // Friendly briefing text with Yellow Title
                String message = "§e§lRedos\n\n" +
                                 "A version discrepancy was found between your\nlocal engine and the remote server host.\n\n" +
                                 "§8» §7Your System: §aV" + Redos.VERSION + "\n" +
                                 "§8» §7Remote Host: §eV" + serverVer + "\n\n" +
                                 "§fLATEST BUILD: §bV" + latest + " §3(Available)\n\n" +
                                 "§7Both systems must match to gain access.\n" +
                                 "§7Please update your local software files.";

                client.setScreen(new VersionNoticeScreen(
                    null, "", message, true,
                    (shouldContinue) -> {
                        client.disconnectFromWorld(Component.literal("Connection aborted by user."));
                    }
                ));
                ci.cancel();
            }
        }
    }
}
