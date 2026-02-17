package net.rdv88.redos.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.rdv88.redos.Redos;
import net.rdv88.redos.RedosClient;
import net.rdv88.redos.client.gui.screen.VersionNoticeScreen;
import net.rdv88.redos.util.UpdateChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void onHandleHello(ClientboundHelloPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        
        // Always skip for singleplayer
        if (client.isLocalServer()) return;

        // LEGACY DETECTION: If we reach the Minecraft 'hello' packet without receiving our mod's version packet,
        // it means the server is either not running Redos or is running a very old version (< V1.0.4).
        if (!RedosClient.isVersionReceived()) {
            ci.cancel(); 
            
            client.execute(() -> {
                String latest = UpdateChecker.getLatestRemoteVersion();
                
                String message = "§e§lRedos\n\n" +
                                 "The remote host does not support the required\n" +
                                 "RED-OS handshake protocol.\n\n" +
                                 "§8» §7Status: §4Legacy / Missing Mod\n" +
                                 "§8» §7Required: §aV" + Redos.VERSION + "\n\n" +
                                 "§fLATEST BUILD: §bV" + latest + " §3(Available)\n\n" +
                                 "§7Please ensure the server is running the mod.";
                
                client.setScreen(new VersionNoticeScreen(
                    null, "", message, true,
                    (shouldContinue) -> {
                        client.disconnectFromWorld(Component.literal("Connection rejected by RED-OS."));
                    }
                ));
            });
        }
    }
}
