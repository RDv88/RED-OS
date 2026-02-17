package net.rdv88.redos.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.rdv88.redos.entity.DroneEntity;

public class DroneEntityRenderer extends MobRenderer<DroneEntity, DroneEntityRenderer.DroneRenderState, DroneEntityRenderer.DroneModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/polished_blackstone.png");
    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/block/amethyst_block.png");
    private final ItemModelResolver itemModelResolver;

    public DroneEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DroneModel(createModelData().bakeRoot()), 0.3f);
        this.itemModelResolver = ctx.getItemModelResolver();
        this.addLayer(new DroneItemLayer(this));
        this.addLayer(new DroneGlowLayer(this));
    }

    @Override
    public DroneRenderState createRenderState() { return new DroneRenderState(); }

    @Override
    public Identifier getTextureLocation(DroneRenderState state) { return TEXTURE; }

    public static class DroneRenderState extends LivingEntityRenderState {
        public float propRotation;
        public final ItemStackRenderState carriedItemRenderState = new ItemStackRenderState();
    }

    @Override
    public void extractRenderState(DroneEntity entity, DroneRenderState state, float delta) {
        super.extractRenderState(entity, state, delta);
        state.propRotation = (entity.level().getGameTime() + delta) * 0.8f;
        ItemStack stack = entity.getCarriedItem();
        if (stack != null && !stack.isEmpty()) {
            this.itemModelResolver.updateForLiving(state.carriedItemRenderState, stack, ItemDisplayContext.FIXED, entity);
        } else {
            state.carriedItemRenderState.clear();
        }
    }

    private class DroneItemLayer extends RenderLayer<DroneRenderState, DroneModel> {
        public DroneItemLayer(DroneEntityRenderer parent) { super(parent); }
        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light, DroneRenderState state, float yaw, float pitch) {
            if (state.carriedItemRenderState.isEmpty()) return;
            poses.pushPose();
            poses.translate(0.0, 0.8, 0.0);
            // Universal 1.5f scaling works because IO Tag now handles its own internal 0.5f scaling in JSON
            poses.scale(1.5f, 1.5f, 1.5f);
            poses.mulPose(Axis.XP.rotationDegrees(90f));
            state.carriedItemRenderState.submit(poses, collector, light, OverlayTexture.NO_OVERLAY, 0);
            poses.popPose();
        }
    }

    private class DroneGlowLayer extends RenderLayer<DroneRenderState, DroneModel> {
        public DroneGlowLayer(DroneEntityRenderer parent) { super(parent); }
        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light, DroneRenderState state, float yaw, float pitch) {
            collector.submitModelPart(this.getParentModel().getLeds(), poses, RenderTypes.eyes(GLOW_TEXTURE), 15, OverlayTexture.NO_OVERLAY, null);
        }
    }

    public static class DroneModel extends EntityModel<DroneRenderState> {
        private final ModelPart root;
        private final ModelPart props;
        private final ModelPart leds;

        public DroneModel(ModelPart root) {
            super(root);
            this.root = root;
            this.props = root.getChild("props");
            this.leds = this.props; 
        }

        public ModelPart getLeds() { return leds; }

        @Override
        public void setupAnim(DroneRenderState state) {
            super.setupAnim(state);
            this.props.setRotation(0, state.propRotation, 0);
        }
    }

    public static LayerDefinition createModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        
        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 2.0F, 6.0F), 
            PartPose.offset(0.0F, 4.0F, 0.0F));
            
        root.addOrReplaceChild("arms", CubeListBuilder.create()
            .texOffs(0, 10).addBox(-5.0F, 0.5F, -0.5F, 10.0F, 1.0F, 1.0F)
            .texOffs(0, 10).addBox(-0.5F, 0.5F, -5.0F, 1.0F, 1.0F, 10.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F));

        PartDefinition props = root.addOrReplaceChild("props", CubeListBuilder.create(), PartPose.offset(0.0F, 4.0F, 0.0F));
        
        PartDefinition p1 = props.addOrReplaceChild("p1", CubeListBuilder.create().texOffs(0, 15).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 0.1F, 2.0F), PartPose.offset(-5.0F, 0.0F, -5.0F));
        p1.addOrReplaceChild("led1", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 0.2F, 1.0F), PartPose.ZERO);
        
        PartDefinition p2 = props.addOrReplaceChild("p2", CubeListBuilder.create().texOffs(0, 15).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 0.1F, 2.0F), PartPose.offset(5.0F, 0.0F, -5.0F));
        p2.addOrReplaceChild("led2", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 0.2F, 1.0F), PartPose.ZERO);

        PartDefinition p3 = props.addOrReplaceChild("p3", CubeListBuilder.create().texOffs(0, 15).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 0.1F, 2.0F), PartPose.offset(-5.0F, 0.0F, 5.0F));
        p3.addOrReplaceChild("led3", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 0.2F, 1.0F), PartPose.ZERO);

        PartDefinition p4 = props.addOrReplaceChild("p4", CubeListBuilder.create().texOffs(0, 15).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 0.1F, 2.0F), PartPose.offset(5.0F, 0.0F, 5.0F));
        p4.addOrReplaceChild("led4", CubeListBuilder.create().texOffs(0, 20).addBox(-0.5F, -0.2F, -0.5F, 1.0F, 0.2F, 1.0F), PartPose.ZERO);

        return LayerDefinition.create(mesh, 32, 32);
    }
}
