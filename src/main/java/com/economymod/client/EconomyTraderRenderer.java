package com.economymod.client;

import com.economymod.EconomyMod;
import com.economymod.entity.EconomyTraderEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

public class EconomyTraderRenderer extends LivingEntityRenderer<EconomyTraderEntity, VillagerModel<EconomyTraderEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "textures/entity/economy_trader.png");

    public EconomyTraderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(EconomyTraderEntity entity) {
        return TEXTURE;
    }
}