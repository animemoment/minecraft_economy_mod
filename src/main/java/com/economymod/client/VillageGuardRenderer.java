package com.economymod.client;

import com.economymod.EconomyMod;
import com.economymod.entity.VillageGuardEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class VillageGuardRenderer extends HumanoidMobRenderer<VillageGuardEntity, HumanoidModel<VillageGuardEntity>> {

    // Текстура воина-жителя (скин на модель игрока)
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "textures/entity/village_guard.png");

    public VillageGuardRenderer(EntityRendererProvider.Context context) {
        // Рендерим его на скелете игрока (PLAYER) с тенью 0.5f
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);

        // Добавляем полноценный рендеринг ванильной брони (всех типов!)
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));

        // Прикрепляем 3D нос жителя к его лицу!
        this.addLayer(new VillagerNoseLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(VillageGuardEntity entity) {
        return TEXTURE;
    }
}