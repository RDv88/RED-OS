package net.rdv88.redos.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.entity.player.PlayerModelType;
import net.rdv88.redos.entity.WatcherEntity;
import java.util.Optional;
import java.util.UUID;

public class WatcherEntityRenderer extends LivingEntityRenderer<WatcherEntity, AvatarRenderState, PlayerModel> {
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;

    public WatcherEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @Override
    public void extractRenderState(WatcherEntity entity, AvatarRenderState state, float delta) {
        super.extractRenderState(entity, state, delta);
        state.id = entity.getId();
        state.xRot = 35.0f;
        
        String name = entity.getOwnerName();
        if (name != null && !name.isEmpty()) {
            ResolvableProfile profile = net.minecraft.world.item.component.ResolvableProfile.createUnresolved(name);
            state.skin = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin();
        }

        if (state.skin != null && state.skin.model() == PlayerModelType.SLIM) {
            this.model = this.slimModel;
        } else {
            this.model = this.wideModel;
        }
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        if (state.skin != null && state.skin.body() != null) {
            return state.skin.body().texturePath();
        }
        return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png");
    }
}