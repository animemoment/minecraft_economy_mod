package com.economymod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose; // Добавлено
import net.minecraft.client.model.geom.builders.CubeListBuilder; // Добавлено
import net.minecraft.client.model.geom.builders.LayerDefinition; // Добавлено
import net.minecraft.client.model.geom.builders.MeshDefinition; // Добавлено
import net.minecraft.client.model.geom.builders.PartDefinition; // Добавлено
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class VillagerNoseLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    private static final ResourceLocation NOSE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private final ModelPart nose;

    public VillagerNoseLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet) {
        super(parent);

        // ИСПРАВЛЕНО: Официальное выпекание 3D-носа жителя через MeshDefinition (для 1.21.1)
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0)
                .addBox(-1.0F, -2.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);

        this.nose = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("nose");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(NOSE_TEXTURE));
        this.nose.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}