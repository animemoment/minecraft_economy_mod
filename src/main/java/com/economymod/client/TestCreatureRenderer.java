package com.economymod.client;

import com.economymod.entity.TestCreatureEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TestCreatureRenderer extends HumanoidMobRenderer<TestCreatureEntity, HumanoidModel<TestCreatureEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("economymod", "textures/entity/test_creature.png");

    public TestCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(TestCreatureEntity entity) {
        return TEXTURE;
    }
}