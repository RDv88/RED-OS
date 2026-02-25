package net.rdv88.redos.util;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.rdv88.redos.Redos;
import net.rdv88.redos.network.payload.SyncPermissionsPayload;

public class PermissionHandler {
    private static final Identifier ADV_MAIN = Identifier.fromNamespaceAndPath(Redos.MOD_ID, "main");
    private static final Identifier ADV_HIGHTECH = Identifier.fromNamespaceAndPath(Redos.MOD_ID, "hightech");

    public static void pushPermissions(ServerPlayer player) {
        if (player == null || player.connection == null) return;

        boolean hasMain = player.isCreative();
        boolean hasHighTech = player.isCreative();
        boolean isAdmin = player.level().getServer() != null && player.level().getServer().getPlayerList().isOp(new net.minecraft.server.players.NameAndId(player.getGameProfile()));

        if (!player.isCreative() && player.level().getServer() != null) {
            AdvancementHolder mainAdv = player.level().getServer().getAdvancements().get(ADV_MAIN);
            AdvancementHolder highTechAdv = player.level().getServer().getAdvancements().get(ADV_HIGHTECH);

            boolean mainDone = (mainAdv != null && player.getAdvancements().getOrStartProgress(mainAdv).isDone());
            boolean highTechDone = (highTechAdv != null && player.getAdvancements().getOrStartProgress(highTechAdv).isDone());

            hasMain = mainDone;
            hasHighTech = mainDone && highTechDone;
        }

        // Push version along with permissions as a secondary safety net
        ServerPlayNetworking.send(player, new SyncPermissionsPayload(hasMain, hasHighTech, isAdmin, Redos.VERSION));
    }
}