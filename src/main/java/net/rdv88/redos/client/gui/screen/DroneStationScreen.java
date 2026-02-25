package net.rdv88.redos.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
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
import net.rdv88.redos.network.payload.SyncHandheldDataPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DroneStationScreen extends AbstractContainerScreen<DroneStationScreenHandler> {
    private enum ViewMode { MAIN, SELECT_SOURCE, SELECT_TARGET, SELECT_PRIORITY, EDIT_OPTIONS, TASK_STATUS }
    private ViewMode viewMode = ViewMode.MAIN;
    
    private BlockPos pendingSource = null;
    private BlockPos pendingTarget = null;
    private int editingTaskIndex = -1;
    private int statusTaskIndex = -1;
    
    private static List<SyncDroneHubTasksPayload.TaskData> clientTasks = new ArrayList<>();
    private static List<Boolean> clientLockedSlots = new ArrayList<>(List.of(false, false, false));
    private float currentScale = 1.0f;
    private int syncTimer = 0;

    public DroneStationScreen(DroneStationScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 176 + 20 + 380; 
        this.imageHeight = 180;
    }

    public void updateTaskList(List<SyncDroneHubTasksPayload.TaskData> tasks, List<Boolean> lockedSlots) {
        clientTasks = tasks;
        clientLockedSlots = lockedSlots;
        refreshButtons();
    }

    @Override
    protected void init() {
        this.leftPos = 0; this.topPos = 0;
        super.init();
        this.leftPos = 0; this.topPos = 0;
        
        // AUTO-PING: Request visible devices for this hub's network immediately
        DroneStationBlockEntity be = (DroneStationBlockEntity) minecraft.level.getBlockEntity(this.menu.getPos());
        if (be != null) {
            ClientPlayNetworking.send(new net.rdv88.redos.network.payload.ConfigureHandheldPayload(be.getNetworkId()));
        }
        
        ClientPlayNetworking.send(new RequestSyncDroneTasksPayload(this.menu.getPos()));
        refreshButtons();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (syncTimer++ >= 20) {
            ClientPlayNetworking.send(new RequestSyncDroneTasksPayload(this.menu.getPos()));
            syncTimer = 0;
        }
    }

    private void refreshButtons() {
        this.clearWidgets();
        int rx = 176 + 20;
        int rWidth = 380;
        int midX = rx + rWidth / 2;

        if (viewMode == ViewMode.MAIN) {
            this.addRenderableWidget(new NavButton(rx + rWidth - 165, 8, 85, 12, "SYSTEM RESET", b -> {
                ClientPlayNetworking.send(new net.rdv88.redos.network.payload.PurgeDronesPayload(this.menu.getPos()));
                HandheldScreen.showToast("ENGINE REBOOTING...");
            }, 0xFF880000));

            if (clientTasks.size() < 9) {
                this.addRenderableWidget(new NavButton(rx + rWidth - 75, 8, 65, 12, "ADD TASK", b -> {
                    viewMode = ViewMode.SELECT_SOURCE; refreshButtons();
                }, 0xFF00AA22));
            }

            for (int i = 0; i < clientTasks.size(); i++) {
                if (i >= 9) break;
                int index = i;
                SyncDroneHubTasksPayload.TaskData t = clientTasks.get(i);
                int rowY = 40 + (i * 12);
                
                this.addRenderableWidget(new LedToggleButton(rx + 5, rowY + 2, 25, 8, t.enabled(), b -> {
                    ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "TOGGLE_TASK", index, BlockPos.ZERO, BlockPos.ZERO, 0));
                }));

                this.addRenderableWidget(new NavButton(rx + 285, rowY, 45, 11, "STATUS", b -> {
                    statusTaskIndex = index; viewMode = ViewMode.TASK_STATUS; refreshButtons();
                }, 0xFF444444));

                this.addRenderableWidget(new NavButton(rx + 335, rowY, 14, 11, "E", b -> {
                    editingTaskIndex = index; pendingSource = t.src(); pendingTarget = t.dst(); viewMode = ViewMode.EDIT_OPTIONS; refreshButtons();
                }, 0xFF0055AA));
                this.addRenderableWidget(new NavButton(rx + 352, rowY, 14, 11, "X", b -> {
                    ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "REMOVE_TASK", index, BlockPos.ZERO, BlockPos.ZERO, 0));
                }, 0xFFAA0000));
            }
        } else if (viewMode == ViewMode.SELECT_SOURCE || viewMode == ViewMode.SELECT_TARGET) {
            setupTagSelection(rx, rWidth);
            this.addRenderableWidget(new NavButton(midX - 50, 155, 100, 16, "CANCEL", b -> { resetFlow(); }, 0xFF444444));
        } else if (viewMode == ViewMode.SELECT_PRIORITY) {
            this.addRenderableWidget(new NavButton(midX - 60, 50, 120, 20, "PRIORITY 1 (HIGH)", b -> finalizeTask(1), 0xFF00AA22));
            this.addRenderableWidget(new NavButton(midX - 60, 75, 120, 20, "PRIORITY 2 (NORMAL)", b -> finalizeTask(2), 0xFF008888));
            this.addRenderableWidget(new NavButton(midX - 60, 100, 120, 20, "PRIORITY 3 (LOW)", b -> finalizeTask(3), 0xFF444444));
            this.addRenderableWidget(new NavButton(midX - 50, 155, 100, 16, "BACK", b -> { viewMode = ViewMode.SELECT_TARGET; refreshButtons(); }, 0xFF444444));
        } else if (viewMode == ViewMode.EDIT_OPTIONS) {
            this.addRenderableWidget(new NavButton(midX - 80, 50, 160, 20, "CHANGE SOURCE TAG", b -> { viewMode = ViewMode.SELECT_SOURCE; refreshButtons(); }, 0xFF0055AA));
            this.addRenderableWidget(new NavButton(midX - 80, 75, 160, 20, "CHANGE DESTINATION TAG", b -> { viewMode = ViewMode.SELECT_TARGET; refreshButtons(); }, 0xFF0055AA));
            this.addRenderableWidget(new NavButton(midX - 80, 100, 160, 20, "CHANGE PRIORITY", b -> { viewMode = ViewMode.SELECT_PRIORITY; refreshButtons(); }, 0xFF0055AA));
            this.addRenderableWidget(new NavButton(midX - 50, 155, 100, 16, "CANCEL", b -> { resetFlow(); }, 0xFF444444));
        } else if (viewMode == ViewMode.TASK_STATUS) {
            this.addRenderableWidget(new NavButton(midX - 50, 155, 100, 16, "CLOSE", b -> { viewMode = ViewMode.MAIN; refreshButtons(); }, 0xFF444444));
        }
    }

    private void finalizeTask(int prio) {
        if (editingTaskIndex != -1) ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "REMOVE_TASK", editingTaskIndex, BlockPos.ZERO, BlockPos.ZERO, 0));
        ClientPlayNetworking.send(new ConfigureDroneHubPayload(this.menu.getPos(), "ADD_TASK", prio, pendingSource, pendingTarget, 0));
        resetFlow();
    }

    private void resetFlow() { viewMode = ViewMode.MAIN; pendingSource = null; pendingTarget = null; editingTaskIndex = -1; refreshButtons(); }

    private void setupTagSelection(int rx, int rw) {
        DroneStationBlockEntity be = (DroneStationBlockEntity) minecraft.level.getBlockEntity(this.menu.getPos());
        String netId = be != null ? be.getNetworkId() : "00000";
        List<SyncHandheldDataPayload.DeviceEntry> tags = HandheldScreen.VISIBLE_DEVICES.stream()
                .filter(d -> d.type().equals("IO_TAG") && d.id().equals(netId))
                .filter(d -> {
                    if (viewMode == ViewMode.SELECT_TARGET && pendingSource != null) return !d.pos().equals(pendingSource);
                    if (viewMode == ViewMode.SELECT_SOURCE && pendingTarget != null) return !d.pos().equals(pendingTarget);
                    return true;
                }).collect(Collectors.toList());

        for (int i = 0; i < tags.size(); i++) {
            if (i > 8) break;
            SyncHandheldDataPayload.DeviceEntry node = tags.get(i);
            this.addRenderableWidget(new NavButton(rx + 15, 45 + (i * 11), rw - 30, 10, "UPLINK: " + node.name(), b -> {
                if (viewMode == ViewMode.SELECT_SOURCE) { pendingSource = node.pos(); viewMode = (editingTaskIndex != -1) ? ViewMode.EDIT_OPTIONS : ViewMode.SELECT_TARGET; }
                else { pendingTarget = node.pos(); viewMode = (editingTaskIndex != -1) ? ViewMode.EDIT_OPTIONS : ViewMode.SELECT_PRIORITY; }
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

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        drawChestBackground(g, 0, 0, 176, 166);
        for (int i = 0; i < 3; i++) {
            int slotY = 18 + (i * 20);
            g.drawString(this.font, "DRONE " + (i + 1) + ":", 12, slotY + 5, 0xFF404040, false);
            drawSlot(g, 59, slotY);
            String status = "§8EMPTY";
            if (!this.menu.getSlot(i).getItem().isEmpty()) {
                if (i < clientLockedSlots.size() && clientLockedSlots.get(i)) {
                    status = "§aACTIVE"; g.fill(60, slotY + 1, 60 + 16, slotY + 17, 0xAA000000);
                } else status = "§7READY";
            }
            g.drawString(this.font, status, 80, slotY + 5, 0xFFFFFFFF, false);
        }
        for (int i = 0; i < 3; i++) for (int j = 0; j < 9; j++) drawSlot(g, 7 + j * 18, 83 + i * 18);
        for (int i = 0; i < 9; i++) drawSlot(g, 7 + i * 18, 141);

        int rx = 176 + 20; int rWidth = 380; int rHeight = 180;
        g.fill(rx, 0, rx + rWidth, rHeight, 0xFF000000);
        g.renderOutline(rx, 0, rWidth, rHeight, 0xFF440000);
        g.fill(rx + 2, 2, rx + rWidth - 2, rHeight - 2, 0xFF100505);

        g.drawString(this.font, "§c> LOGISTICS COMMAND CENTER", rx + 10, 10, 0xFFFFFFFF, false);
        g.fill(rx + 10, 20, rx + rWidth - 10, 21, 0xFF440000);

        if (viewMode == ViewMode.MAIN) {
            int hY = 25;
            g.drawString(this.font, "§7CTR", rx + 8, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§bSOURCE TAG", rx + 35, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§bQTY", rx + 110, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§aDESTINATION", rx + 145, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§aFREE", rx + 220, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§6PRIO", rx + 255, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§7STATUS", rx + 285, hY, 0xFFFFFFFF, false);
            g.drawString(this.font, "§7EDIT", rx + 340, hY, 0xFFFFFFFF, false);
            
            g.fill(rx + 32, hY - 2, rx + 33, hY + 150, 0x22FFFFFF); 
            g.fill(rx + 107, hY - 2, rx + 108, hY + 150, 0x22FFFFFF); 
            g.fill(rx + 142, hY - 2, rx + 143, hY + 150, 0x22FFFFFF); 
            g.fill(rx + 217, hY - 2, rx + 218, hY + 150, 0x22FFFFFF); 
            g.fill(rx + 252, hY - 2, rx + 253, hY + 150, 0x22FFFFFF); 
            g.fill(rx + 282, hY - 2, rx + 283, hY + 150, 0x22FFFFFF); 
            g.fill(rx + 332, hY - 2, rx + 333, hY + 150, 0x22FFFFFF); 

            for (int i = 0; i < clientTasks.size(); i++) {
                SyncDroneHubTasksPayload.TaskData t = clientTasks.get(i);
                int y = 40 + (i * 12);
                TechNetwork.NetworkNode sNode = TechNetwork.getNodes().get(t.src()), dNode = TechNetwork.getNodes().get(t.dst());
                String sName = sNode != null ? sNode.customName : "OFFLINE", dName = dNode != null ? dNode.customName : "OFFLINE";
                
                drawMarqueeText(g, sName, rx + 35, y + 2, 70, 0xFFAAAAAA);
                g.drawString(minecraft.font, "[" + t.srcCount() + "]", rx + 110 + (30 - font.width("["+t.srcCount()+"]"))/2, y + 2, 0xFF55FFFF, false);
                drawMarqueeText(g, dName, rx + 145, y + 2, 70, 0xFFAAAAAA);
                g.drawString(minecraft.font, "(" + t.dstFree() + ")", rx + 220 + (35 - font.width("("+t.dstFree()+")"))/2, y + 2, 0xFF55FF55, false);
                g.drawString(minecraft.font, "P" + t.prio(), rx + 255 + (25 - font.width("P"+t.prio()))/2, y + 2, 0xFFFFAA00, false);
            }

            g.fill(rx + 10, hY + 9, rx + rWidth - 10, hY + 10, 0xFF330000);
            if (clientTasks.isEmpty()) g.drawCenteredString(font, "SYSTEM STANDBY - READY FOR UPLINK", rx + rWidth/2, 80, 0xFF444444);
        } else if (viewMode == ViewMode.TASK_STATUS) {
            g.drawCenteredString(font, "§cMISSION TELEMETRY #" + (statusTaskIndex + 1), rx + rWidth/2, 30, 0xFFFFFFFF);
            SyncDroneHubTasksPayload.TaskData t = clientTasks.get(statusTaskIndex);
            
            // Real-time Status from Master
            String statusText = t.statusMessage().isEmpty() ? "§8WAITING FOR DATA..." : t.statusMessage();
            g.drawCenteredString(font, statusText, rx + rWidth/2, 80, 0xFFFFFFFF);
            
            if (!t.droneState().equals("IDLE")) {
                int seconds = t.etaTicks() / 20;
                String etaStr = seconds > 0 ? "§7REMAINING TIME: §b" + seconds + " seconds" : "§7STATUS: §bArriving at destination";
                g.drawCenteredString(font, etaStr, rx + rWidth/2, 100, 0xFFFFFFFF);
                
                // Show current flight stage
                String stage = "§8Current Mission Phase: §7" + t.droneState();
                g.drawCenteredString(font, stage, rx + rWidth/2, 120, 0xFF666666);
            }
        } else {
            String step = switch(viewMode) { case SELECT_SOURCE -> "STEP 1: SELECT SOURCE IO TAG"; case SELECT_TARGET -> "STEP 2: SELECT DESTINATION IO TAG"; case SELECT_PRIORITY -> "STEP 3: SELECT MISSION PRIORITY"; case EDIT_OPTIONS -> "MISSION CONFIGURATION"; default -> ""; };
            g.drawCenteredString(font, "§b" + step, rx + rWidth/2, 30, 0xFFFFFFFF);
        }
        g.drawString(this.font, "RED-OS Logistic Hub", (176 - font.width("RED-OS Logistic Hub"))/2, 6, 0xFF404040, false);
    }

    private String translateState(String state, SyncDroneHubTasksPayload.TaskData t) {
        return switch (state) {
            case "STEP1_INITIALIZING" -> "§eBooting drone flight systems...";
            case "STEP2_GOING_TO_SOURCE" -> "§bEn route to collection point: §f" + getTagShortName(t.src());
            case "STEP3_GOING_TO_TARGET" -> "§aEn route to delivery point: §f" + getTagShortName(t.dst());
            case "STEP4_RETURNING_HOME" -> "§7Mission complete. Returning to Hub.";
            default -> "§8SYSTEM STANDBY - WAITING FOR ASSIGNMENT";
        };
    }

    private String getTagShortName(BlockPos pos) { TechNetwork.NetworkNode n = TechNetwork.getNodes().get(pos); return n != null ? (n.customName.length() > 10 ? n.customName.substring(0, 8) + ".." : n.customName) : "---"; }
    private String getTagFullName(BlockPos pos) { TechNetwork.NetworkNode n = TechNetwork.getNodes().get(pos); return n != null ? n.customName : "Unknown Tag"; }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x80000000);
        this.currentScale = imageWidth > this.width * 0.95f ? (this.width * 0.95f) / (float)this.imageWidth : 1.0f;
        float centerX = this.width / 2f, centerY = this.height / 2f;
        float scaledHalfWidth = (this.imageWidth * currentScale) / 2f, scaledHalfHeight = (this.imageHeight * currentScale) / 2f;
        g.pose().pushMatrix();
        g.pose().translate(centerX - scaledHalfWidth, centerY - scaledHalfHeight);
        g.pose().scale(currentScale, currentScale);
        int smX = (int)((mouseX - (centerX - scaledHalfWidth)) / currentScale), smY = (int)((mouseY - (centerY - scaledHalfHeight)) / currentScale);
        this.renderBg(g, delta, smX, smY); super.render(g, smX, smY, delta);
        g.pose().popMatrix(); this.renderTooltip(g, mouseX, mouseY);
    }

    private MouseButtonEvent translateEvent(MouseButtonEvent e) {
        float centerX = this.width / 2f, centerY = this.height / 2f;
        float scaledHalfWidth = (this.imageWidth * currentScale) / 2f, scaledHalfHeight = (this.imageHeight * currentScale) / 2f;
        return new MouseButtonEvent((e.x() - (centerX - scaledHalfWidth)) / currentScale, (e.y() - (centerY - scaledHalfHeight)) / currentScale, e.buttonInfo());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean isSecondary) { return super.mouseClicked(translateEvent(event), isSecondary); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(translateEvent(event)); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) { return super.mouseDragged(translateEvent(event), deltaX / currentScale, deltaY / currentScale); }
    @Override public void onClose() { ClientPlayNetworking.send(new net.rdv88.redos.network.payload.ConfigureHandheldPayload("STOP_TRACKING")); super.onClose(); }

    private class LedToggleButton extends Button {
        private final boolean enabled;
        public LedToggleButton(int x, int y, int w, int h, boolean e, OnPress p) { super(x, y, w, h, Component.empty(), p, DEFAULT_NARRATION); this.enabled = e; }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int ledCol = enabled ? 0xFF00FF00 : 0xFF444444;
            g.fill(getX() + 2, getY() + 2, getX() + 6, getY() + 6, ledCol);
            g.drawString(minecraft.font, enabled ? "ON" : "OFF", getX() + 8, getY() + 1, enabled ? 0xFFFFFFFF : 0xFF888888, false);
            if (isHovered()) g.renderOutline(getX(), getY(), width, height, 0xFFAA0000);
        }
    }

    private void drawMarqueeText(GuiGraphics g, String text, int x, int y, int mw, int col) {
        int tw = minecraft.font.width(text); 
        g.enableScissor((int)(x * currentScale), (int)(y * currentScale), (int)((x + mw) * currentScale), (int)((y + 10) * currentScale));
        if (tw <= mw) { g.drawString(minecraft.font, text, x, y, col, false); }
        else {
            int off = (int) ((Math.sin(System.currentTimeMillis() / 2000.0 * Math.PI) * 0.5 + 0.5) * (tw - mw));
            g.drawString(minecraft.font, text, x - off, y, col, false);
        }
        g.disableScissor();
    }

    private class NavButton extends net.minecraft.client.gui.components.Button {
        private final int color;
        public NavButton(int x, int y, int w, int h, String txt, OnPress p, int col) { super(x, y, w, h, Component.literal(txt), p, DEFAULT_NARRATION); this.color = col; }
        @Override public void onPress(net.minecraft.client.input.InputWithModifiers m) { this.onPress.onPress(this); }
        @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? color : color - 0x22000000);
            g.renderOutline(getX(), getY(), width, height, isHovered() ? 0xFFFF0000 : 0xFF660000);
            g.drawString(minecraft.font, getMessage(), getX() + (width - minecraft.font.width(getMessage()))/2, getY() + (height - 8) / 2, 0xFFFFFFFF, false);
        }
    }
}