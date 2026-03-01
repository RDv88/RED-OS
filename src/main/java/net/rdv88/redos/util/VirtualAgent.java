package net.rdv88.redos.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.rdv88.redos.util.LogisticsEngine.State;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VirtualAgent {
    public final UUID uuid;
    public final BlockPos hubPos;
    public final String networkId;
    public final int homeSlot;
    public BlockPos sourcePos, targetPos;
    public List<BlockPos> waypoints = new ArrayList<>();
    public Vec3 currentPos;
    public State state;
    public ItemStack carriedItem = ItemStack.EMPTY;
    public int taskIndex = -1;
    public String lastStatusMessage = "Initializing...";
    
    // VERKEERSLEIDER DATA
    public transient UUID puppetUuid = null;
    public transient boolean handshakeReceived = false;
    public transient int ghostTicksRemaining = 0;
    public transient boolean isPhysicalActive = false;

    public VirtualAgent(UUID uuid, BlockPos hub, String netId, int slot) {
        this.uuid = uuid;
        this.hubPos = hub;
        this.networkId = netId;
        this.homeSlot = slot;
        this.state = State.STEP1_INITIALIZING;
        this.currentPos = Vec3.atCenterOf(hub).add(0, 0.5, 0);
    }
}
