package net.rdv88.redos.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.DisconnectionDetails;
import net.rdv88.redos.Redos;
import net.rdv88.redos.RedosClient;
import net.rdv88.redos.client.gui.screen.VersionNoticeScreen;
import net.rdv88.redos.util.UpdateChecker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private DisconnectionDetails details;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (details == null || details.reason() == null) return;
        
        String errorText = details.reason().getString().toLowerCase();
        
        if (errorText.contains("redos") || errorText.contains("server_rack")) {
            ci.cancel(); 
            
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                String latest = UpdateChecker.getLatestRemoteVersion();
                String serverVer = RedosClient.getLastServerVersion();
                
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
            });
        }
    }
}