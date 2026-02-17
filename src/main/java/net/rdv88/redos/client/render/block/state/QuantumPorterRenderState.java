package net.rdv88.redos.client.render.block.state;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class QuantumPorterRenderState extends BlockEntityRenderState {
    public Component name;
    public Direction facing;
    public boolean isUpper;
}