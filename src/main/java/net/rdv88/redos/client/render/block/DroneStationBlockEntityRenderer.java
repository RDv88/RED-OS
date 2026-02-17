package net.rdv88.redos.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.rdv88.redos.block.custom.DroneStationBlock;
import net.rdv88.redos.block.entity.DroneStationBlockEntity;

public class DroneStationBlockEntityRenderer implements BlockEntityRenderer<DroneStationBlockEntity, DroneStationBlockEntityRenderer.DroneHubRenderState> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(Identifier.fromNamespaceAndPath("redos", "drone_hub"), "main");
    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/amethyst_block.png");
    private final ModelPart leds;

    public DroneStationBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        ModelPart root = ctx.bakeLayer(LAYER_LOCATION);
        this.leds = root.getChild("leds");
    }

    public static class DroneHubRenderState extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public long gameTime = 0;
    }

    @Override public DroneHubRenderState createRenderState() { return new DroneHubRenderState(); }

    @Override
    public void extractRenderState(DroneStationBlockEntity entity, DroneHubRenderState state, float delta, net.minecraft.world.phys.Vec3 cameraOffset, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay overlay) {
        state.facing = entity.getBlockState().getValue(DroneStationBlock.FACING);
        state.gameTime = entity.getLevel().getGameTime();
    }

    @Override
    public void submit(DroneHubRenderState state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState cameraState) {
        poses.pushPose();
        poses.translate(0.5, 0, 0.5);
        float rotation = state.facing.toYRot();
        poses.mulPose(Axis.YP.rotationDegrees(-rotation));
        poses.translate(-0.5, 0, -0.5);

        collector.submitModelPart(this.leds, poses, RenderTypes.eyes(GLOW_TEXTURE), 15, OverlayTexture.NO_OVERLAY, null);

        poses.pushPose();
        poses.translate(0.5, 0.7, 0.5); 
        poses.translate(0, 0.12, 0.23); 
        poses.mulPose(Axis.XP.rotationDegrees(22.5f));
        float scale = 0.007f;
        poses.scale(scale, -scale, scale);
        drawEnhancedScreen(poses, collector, state);
        poses.popPose();

        poses.popPose();
    }

    private void drawEnhancedScreen(PoseStack poses, SubmitNodeCollector collector, DroneHubRenderState state) {
        int brightPurple = 0xFFCC44FF;
        int darkPurple = 0xFF6600AA;
        long time = state.gameTime;
        drawText(collector, poses, " [ NETWORK NODE ] ", -45, -45, brightPurple);
        for (int i = 0; i < 4; i++) {
            long rowTime = time / (2 + i) + (i * 50);
            String hex = Integer.toHexString((int)(rowTime * 99)).toUpperCase();
            if (hex.length() > 4) hex = hex.substring(0, 4);
            int color = (i == (time/10)%4) ? brightPurple : darkPurple;
            drawText(collector, poses, "> 0x" + hex + " SYNC", -40, -15 + (i * 10), color);
        }
        String load = "LOAD: ";
        int bars = (int)((Math.sin(time * 0.2) * 0.5 + 0.5) * 8);
        for(int i=0; i<8; i++) load += (i < bars) ? "I" : ".";
        drawText(collector, poses, load, -40, 40, brightPurple);
    }

    private void drawText(SubmitNodeCollector collector, PoseStack poses, String text, float x, float y, int color) {
        FormattedCharSequence chars = Component.literal(text).getVisualOrderText();
        collector.submitText(poses, x, y, chars, false, Font.DisplayMode.NORMAL, color, 0, 15, OverlayTexture.NO_OVERLAY);
    }

    public static LayerDefinition createModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("leds", CubeListBuilder.create()
            .texOffs(0, 24).addBox(1, 8.1f, 1, 1, 0.2f, 1)
            .texOffs(0, 24).addBox(14, 8.1f, 1, 1, 0.2f, 1)
            .texOffs(0, 24).addBox(1, 8.1f, 14, 1, 0.2f, 1)
            .texOffs(0, 24).addBox(14, 8.1f, 14, 1, 0.2f, 1),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
