package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

import java.util.EnumSet;
import java.util.List;

public class VillagerCollectItemsGoal extends Goal {
    private final Villager villager;
    private ItemEntity targetItem;
    private int delay = 0;

    public VillagerCollectItemsGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (delay > 0) { delay--; return false; }
        if (villager.level().getGameTime() % 20 != 0) return false;

        // Ищем выпавшие на землю предметы в радиусе 16 блоков
        List<ItemEntity> items = villager.level().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(villager.blockPosition()).inflate(16.0),
                entity -> entity.isAlive() && entity.onGround() && isInterestingItem(entity.getItem())
        );

        if (!items.isEmpty()) {
            // Бежим к ближайшему выпавшему ресурсу
            this.targetItem = items.stream().min((e1, e2) ->
                    Double.compare(villager.distanceToSqr(e1), villager.distanceToSqr(e2))).orElse(null);
            return this.targetItem != null;
        }
        return false;
    }

    private boolean isInterestingItem(ItemStack stack) {
        // Подбираем руду, необработанную руду, уголь, алмазы, факелы и строительные блоки
        return stack.is(Tags.Items.ORES) ||
                stack.is(Tags.Items.RAW_MATERIALS) ||
                stack.is(net.minecraft.world.item.Items.COAL) ||
                stack.is(net.minecraft.world.item.Items.CHARCOAL) ||
                stack.is(net.minecraft.world.item.Items.DIAMOND) ||
                stack.is(net.minecraft.world.item.Items.EMERALD) ||
                stack.is(net.minecraft.world.item.Items.RAW_IRON) ||
                stack.is(net.minecraft.world.item.Items.RAW_GOLD) ||
                stack.is(net.minecraft.world.item.Items.RAW_COPPER) ||
                stack.getItem() instanceof net.minecraft.world.item.PickaxeItem ||
                stack.is(net.minecraft.world.item.Items.TORCH) ||
                net.minecraft.world.level.block.Block.byItem(stack.getItem()).defaultBlockState().isSolid();
    }

    @Override
    public void tick() {
        if (targetItem == null || !targetItem.isAlive()) return;

        villager.getLookControl().setLookAt(targetItem, 30.0F, 30.0F);
        villager.getNavigation().moveTo(targetItem, 0.6D);

        if (villager.distanceToSqr(targetItem) < 2.0) {
            VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
            if (att != null) {
                ItemStack leftover = att.getInventory().addItem(targetItem.getItem().copy());
                if (leftover.getCount() < targetItem.getItem().getCount()) {
                    targetItem.setItem(leftover);
                    if (leftover.isEmpty()) targetItem.discard();
                    villager.level().playSound(null, villager.blockPosition(),
                            net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);
                }
            }
            this.targetItem = null;
            this.delay = 10;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetItem != null && targetItem.isAlive() && villager.distanceToSqr(targetItem) < 256.0;
    }
}