package net.rdv88.redos.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.rdv88.redos.block.entity.DroneStationBlockEntity;
import net.rdv88.redos.screen.DroneStationScreenHandler;
import net.rdv88.redos.util.TechNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.rdv88.redos.network.payload.ConfigureDroneHubPayload;
import net.rdv88.redos.network.payload.SyncDroneHubTasksPayload;
import net.rdv88.redos.network.payload.RequestSyncDroneTasksPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DroneStationScreen extends AbstractContainerScreen<DroneStationScreenHandler> {
    private enum ViewMode { MAIN, SELECT_SOURCE, SELECT_TARGET, SELECT_PRIORITY, EDIT_OPTIONS }
    private ViewMode viewMode = ViewMode.MAIN;
    
    private BlockPos pendingSource = null;
    private BlockPos pendingTarget = null;
    private int editingTaskIndex = -1;
    
    private static List<SyncDroneHubTasksPayload.TaskData> clientTasks = new ArrayList<>();

    public DroneStationScreen(DroneStationScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 176 + 20 + 350; 
        this.imageHeight = 180;
    }

    public void updateTaskList(List<SyncDroneHubTasksPayload.TaskData> tasks) {
        clientTasks = tasks;
        refreshButtons();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        ClientPlayNetworking.send(new RequestSyncDroneTasksPayload(this.menu.getPos()));
        refreshButtons();
    }

    private void refreshButtons() {
        this.clearWidgets();
        int rx = this.leftPos + 176 + 20;
        int rWidth = 350;
        int midX = rx + rWidth / 2;

        if (viewMode == ViewMode.MAIN) {
            this.addRenderableWidget(new NavButton(midX - 10, this.topPos + 22, 20, 12, "+", b -> {
                viewMode = ViewMode.SELECT_SOURCE;
                refreshButtons();
            }, 0xFF00AA22));

            for (int i = 0; i < clientTasks.size(); i++) {
                if (i > 10) break;
                int index = i;
                SyncDroneHubTasksPayload.TaskData t = clientTasks.get(i);
                int rowY = this.topPos + 40 + (i * 12);
                
                this.addRenderableWidget(new RowButton(rx + 10, rowY, rWidth - 60, 11, t, b -> {
                    ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "TOGGLE_TASK", index, BlockPos.ZERO, BlockPos.ZERO, 0));
                }));

                this.addRenderableWidget(new NavButton(rx + rWidth - 35, rowY, 14, 11, "E", b -> {
                    editingTaskIndex = index;
                    pendingSource = t.src();
                    pendingTarget = t.dst();
                    viewMode = ViewMode.EDIT_OPTIONS;
                    refreshButtons();
                }, 0xFF0055AA));
                
                this.addRenderableWidget(new NavButton(rx + rWidth - 18, rowY, 14, 11, "X", b -> {
                    ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "REMOVE_TASK", index, BlockPos.ZERO, BlockPos.ZERO, 0));
                }, 0xFFAA0000));
            }
        } else if (viewMode == ViewMode.SELECT_SOURCE || viewMode == ViewMode.SELECT_TARGET) {
            setupTagSelection(rx, rWidth);
            this.addRenderableWidget(new NavButton(midX - 50, this.topPos + 155, 100, 16, "CANCEL", b -> { resetFlow(); }, 0xFF444444));
        } else if (viewMode == ViewMode.SELECT_PRIORITY) {
            this.addRenderableWidget(new NavButton(midX - 60, this.topPos + 50, 120, 20, "PRIORITY 1 (HIGH)", b -> finalizeTask(1), 0xFF00AA22));
            this.addRenderableWidget(new NavButton(midX - 60, this.topPos + 75, 120, 20, "PRIORITY 2 (NORMAL)", b -> finalizeTask(2), 0xFF008888));
            this.addRenderableWidget(new NavButton(midX - 60, this.topPos + 100, 120, 20, "PRIORITY 3 (LOW)", b -> finalizeTask(3), 0xFF444444));
            this.addRenderableWidget(new NavButton(midX - 50, this.topPos + 155, 100, 16, "BACK", b -> { viewMode = ViewMode.SELECT_TARGET; refreshButtons(); }, 0xFF444444));
        } else if (viewMode == ViewMode.EDIT_OPTIONS) {
            this.addRenderableWidget(new NavButton(midX - 80, this.topPos + 50, 160, 20, "CHANGE SOURCE TAG", b -> { viewMode = ViewMode.SELECT_SOURCE; refreshButtons(); }, 0xFF0055AA));
            this.addRenderableWidget(new NavButton(midX - 80, this.topPos + 75, 160, 20, "CHANGE DESTINATION TAG", b -> { viewMode = ViewMode.SELECT_TARGET; refreshButtons(); }, 0xFF0055AA));
            this.addRenderableWidget(new NavButton(midX - 80, this.topPos + 100, 160, 20, "CHANGE PRIORITY", b -> { viewMode = ViewMode.SELECT_PRIORITY; refreshButtons(); }, 0xFF0055AA));
            this.addRenderableWidget(new NavButton(midX - 50, this.topPos + 155, 100, 16, "CANCEL", b -> { resetFlow(); }, 0xFF444444));
        }
    }

    private void finalizeTask(int prio) {
        if (editingTaskIndex != -1) {
            ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "REMOVE_TASK", editingTaskIndex, BlockPos.ZERO, BlockPos.ZERO, 0));
        }
        ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "ADD_TASK", prio, pendingSource, pendingTarget, 0));
        resetFlow();
    }

    private void resetFlow() {
        viewMode = ViewMode.MAIN;
        pendingSource = null;
        pendingTarget = null;
        editingTaskIndex = -1;
        refreshButtons();
    }

    private void setupTagSelection(int rx, int rw) {
        DroneStationBlockEntity be = (DroneStationBlockEntity) minecraft.level.getBlockEntity(this.menu.getPos());
        String netId = be != null ? be.getNetworkId() : "00000";
        
        // INTELLIGENT FILTERING:
        // 1. Filter only IO Tags on the SAME network ID
        // 2. If selecting TARGET, filter out the chosen SOURCE (prevent loopback)
        // 3. If selecting SOURCE, filter out the chosen TARGET (prevent loopback)
        List<TechNetwork.NetworkNode> tags = TechNetwork.getNodes().values().stream()
                .filter(n -> n.type == TechNetwork.NodeType.IO_TAG && n.networkId.equals(netId))
                .filter(n -> {
                    if (viewMode == ViewMode.SELECT_TARGET && pendingSource != null) return !n.pos.equals(pendingSource);
                    if (viewMode == ViewMode.SELECT_SOURCE && pendingTarget != null) return !n.pos.equals(pendingTarget);
                    return true;
                })
                .collect(Collectors.toList());

        for (int i = 0; i < tags.size(); i++) {
            if (i > 8) break;
            TechNetwork.NetworkNode node = tags.get(i);
            this.addRenderableWidget(new NavButton(rx + 15, this.topPos + 45 + (i * 11), rw - 30, 10, "UPLINK: " + node.customName, b -> {
                if (viewMode == ViewMode.SELECT_SOURCE) {
                    pendingSource = node.pos;
                    viewMode = (editingTaskIndex != -1) ? ViewMode.EDIT_OPTIONS : ViewMode.SELECT_TARGET;
                } else {
                    pendingTarget = node.pos;
                    viewMode = (editingTaskIndex != -1) ? ViewMode.EDIT_OPTIONS : ViewMode.SELECT_PRIORITY;
                }
                refreshButtons();
            }, 0xFF008888));
        }
    }

    private void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x, y, x + 17, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 17, 0xFF373737);
        g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private void drawChestBackground(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF555555);
    }

    @Override
    protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        drawChestBackground(g, x, y, 176, 133);
        for (int i = 0; i < 5; i++) drawSlot(g, x + 43 + (i * 18), y + 17);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) drawSlot(g, x + 7 + j * 18, y + 50 + i * 18);
        }
        for (int i = 0; i < 9; i++) drawSlot(g, x + 7 + i * 18, y + 108);

        int rx = x + 176 + 20; 
        int rWidth = 350;
        int rHeight = 180;
        g.fill(rx, y, rx + rWidth, y + rHeight, 0xFF000000);
        g.renderOutline(rx, y, rWidth, rHeight, 0xFF440000);
        g.fill(rx + 2, y + 2, rx + rWidth - 2, y + rHeight - 2, 0xFF100505);

        g.drawString(this.font, "§c> LOGISTICS COMMAND CENTER", rx + 10, y + 10, 0xFFFFFFFF, false);
        g.fill(rx + 10, y + 20, rx + rWidth - 10, y + 21, 0xFF440000);

        if (viewMode == ViewMode.MAIN) {
            g.drawString(this.font, "ACTIVE LOGISTICS MISSIONS:", rx + 10, y + 25, 0xFFAA0000, false);
            if (clientTasks.isEmpty()) g.drawCenteredString(font, "SYSTEM STANDBY - READY FOR UPLINK", rx + rWidth/2, y + 80, 0xFF444444);
        } else if (viewMode == ViewMode.EDIT_OPTIONS) {
            g.drawCenteredString(font, "§bEDIT MISSION #" + (editingTaskIndex + 1), rx + rWidth/2, y + 30, 0xFFFFFFFF);
        } else {
            String step = switch(viewMode) {
                case SELECT_SOURCE -> "STEP 1: SELECT SOURCE IO TAG";
                case SELECT_TARGET -> "STEP 2: SELECT DESTINATION IO TAG";
                case SELECT_PRIORITY -> "STEP 3: SELECT MISSION PRIORITY";
                default -> "";
            };
            g.drawCenteredString(font, "§b" + step, rx + rWidth/2, y + 30, 0xFFFFFFFF);
        }

        g.drawString(this.font, "Drone Hub", x + (176 - font.width("Drone Hub"))/2, y + 6, 0xFF404040, false);
        g.drawString(this.font, "Inventory", x + 8, y + 39, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    private class RowButton extends Button {
        private final SyncDroneHubTasksPayload.TaskData task;
        public RowButton(int x, int y, int w, int h, SyncDroneHubTasksPayload.TaskData t, OnPress p) {
            super(x, y, w, h, Component.empty(), p, DEFAULT_NARRATION);
            this.task = t;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            if (isHovered()) g.fill(getX(), getY(), getX() + width, getY() + height, 0x22FF0000);
            
            String srcName = TechNetwork.getNodes().containsKey(task.src()) ? TechNetwork.getNodes().get(task.src()).customName : "TAG-SRC";
            String dstName = TechNetwork.getNodes().containsKey(task.dst()) ? TechNetwork.getNodes().get(task.dst()).customName : "TAG-DST";
            String status = task.enabled() ? "§a[ENABLED]" : "§7[PAUSED]";
            String taskText = String.format("%s §7->§r %s §6P%d §r%s", srcName, dstName, task.prio(), status);
            
            g.drawString(minecraft.font, taskText, getX() + 4, getY() + 2, isHovered() ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        }
    }

    private class NavButton extends net.minecraft.client.gui.components.Button {
        private final int color;
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col) {
            super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION);
            this.color = col;
        }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? color : color - 0x22000000);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.drawCenteredString(minecraft.font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
        }
    }
}
