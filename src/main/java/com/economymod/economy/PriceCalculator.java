package com.economymod.economy;

import com.economymod.world.VillageNetworkData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PriceCalculator {

    public static long calculateDynamicPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        long basePrice = getBasePrice(stack);
        if (villageInfo == null || stack.isEmpty()) return basePrice;
        double D = villageInfo.getDemandFactor(stack.getItem());
        double S = villageInfo.getSupplyFactor(stack.getItem());
        double E = Math.max(0.25, Math.min(4.0, D / S));
        double I = villageInfo.getInflationRate();
        double rarity = (64.0 - stack.getMaxStackSize()) / 64.0 * 0.5;
        double R = 1.0 + rarity;
        double U = 1.0 + villageInfo.getAverageDesire(stack.getItem()) * 0.5;
        long adjusted = (long) (basePrice * E * I * R * U);
        return Math.max(1L, adjusted);
    }

    public static long getBuyPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        return calculateDynamicPrice(stack, villageInfo);
    }

    public static long getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        return Math.max(1L, getBasePrice(stack) / 2);
    }

    public static long getBasePrice(ItemStack stack) {
        if (stack.isEmpty()) return 1L;

        Item item = stack.getItem();
        int count = stack.getCount();

        if (item == Items.EMERALD) return 20L * count;
        if (item == Items.DIAMOND) return 50L * count;
        if (item == Items.IRON_INGOT) return 5L * count;
        if (item == Items.GOLD_INGOT) return 12L * count;
        if (item == Items.COAL) return 2L * count;
        if (item == Items.WHEAT) return 1L * count;
        if (item == Items.BREAD) return 3L * count;
        if (item == Items.COOKED_CHICKEN) return 4L * count;
        if (item == Items.CHICKEN) return 2L * count;
        if (item == Items.OAK_LOG) return 1L * count;
        if (item == Items.STICK) return 1L * Math.max(1, count / 4);
        if (item == Items.STONE_PICKAXE) return 4L;
        if (item == Items.IRON_PICKAXE) return 15L;
        if (item == Items.STONE_HOE) return 3L;
        if (item == Items.IRON_HOE) return 12L;
        if (item == Items.BONE_MEAL) return 1L * count;

        if (item.getDefaultMaxStackSize() <= 16) return 8L * count;
        if (stack.getFoodProperties(null) != null) return 2L * count; // исправлено: вызываем на ItemStack с параметром null
        return 1L * count;
    }
}