package net.rdv88.redos.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.rdv88.redos.client.gui.screen.HandheldScreen;
import net.rdv88.redos.client.gui.screen.QuantumPorterScreen;

@Environment(EnvType.CLIENT)
public class ClientUtil {
    
    /**
     * @param status 0: Authorized, 1: ID_MISMATCH, 2: NO_HANDHELD
     */
    public static void openQuantumPorterScreen(BlockPos pos, String name, int status) {
        Minecraft.getInstance().setScreen(new QuantumPorterScreen(pos, name, status));
    }

    public static void openHandheldScreen(ItemStack stack, Player player) {
        Minecraft.getInstance().setScreen(new HandheldScreen(stack, player));
    }
}
