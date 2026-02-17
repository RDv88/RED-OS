package net.rdv88.redos.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.rdv88.redos.Redos;
import net.rdv88.redos.network.ModMessages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.network.Connection;

@Mixin(ServerLoginPacketListenerImpl.class)
public class ServerLoginHandshakeMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("redos-handshake");

    @Shadow
    public Connection connection;

    @Inject(method = "handleHello", at = @At("HEAD"))
    private void onHandleHello(ServerboundHelloPacket packet, CallbackInfo ci) {
        ServerLoginPacketListenerImpl handler = (ServerLoginPacketListenerImpl)(Object)this;
        
        // 1. SKIP in Singleplayer (Mojmap verified name)
        if (connection.isMemoryConnection()) {
            return;
        }

        // 2. SAFETY GATE: Only proceed if connection is active (Mojmap verified name)
        // This avoids the Netty "unknown packet: disconnect" error when bots drop immediately.
        if (connection == null || !connection.isConnected()) {
            return;
        }

        // 3. SECURE HANDSHAKE (Original Beveiliging)
        try {
            LOGGER.info("RED-OS: [FORCE HANDSHAKE] Sending version check to '{}'", packet.name());
            
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(Redos.VERSION);
            
            ServerLoginNetworking.getSender(handler).sendPacket(ModMessages.VERSION_CHECK_ID, buf);
        } catch (Exception e) {
            LOGGER.warn("RED-OS: Handshake skipped for '{}' (Connection dropped by client)", packet.name());
        }
    }
}
