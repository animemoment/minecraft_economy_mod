package com.economymod.economy;

import com.economymod.world.VillageNetworkData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

public class PriceCalculator {

    // Базовая цена покупки (для торговца: сколько он продаёт)
    public static long getBuyPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        long base = switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
            case "minecraft:bread" -> 2;
            case "minecraft:coal" -> 1;
            case "minecraft:iron_ingot" -> 6;
            case "minecraft:chicken" -> 3;
            case "minecraft:wheat_seeds" -> 1;
            case "minecraft:bone_meal" -> 2;
            case "minecraft:wooden_hoe" -> 4;
            case "minecraft:iron_pickaxe" -> 18;
            case "minecraft:book" -> 8;
            case "minecraft:paper" -> 1;
            case "minecraft:beef" -> 3;
            case "minecraft:gold_ingot" -> 12;
            case "minecraft:redstone" -> 5;
            default -> 1;
        };
        if (villageInfo != null) {
            double factor = villageInfo.getDemandFactor(stack.getItem())
                    * villageInfo.getInflationRate();
            base = (long) Math.max(1, base * factor);
        }
        return base;
    }

    // Базовая цена продажи (сколько торговец платит жителю)
    public static long getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        long base = switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
            case "minecraft:bread" -> 1;
            case "minecraft:coal" -> 1;
            case "minecraft:iron_ingot" -> 4;
            case "minecraft:chicken" -> 2;
            case "minecraft:wheat_seeds" -> 1;
            case "minecraft:bone_meal" -> 1;
            case "minecraft:wooden_hoe" -> 2;
            case "minecraft:iron_pickaxe" -> 12;
            case "minecraft:book" -> 5;
            case "minecraft:paper" -> 1;
            case "minecraft:beef" -> 2;
            case "minecraft:gold_ingot" -> 8;
            case "minecraft:redstone" -> 3;
            default -> 1;
        };
        if (villageInfo != null) {
            double factor = villageInfo.getSupplyFactor(stack.getItem())
                    * (1.0 / villageInfo.getInflationRate());
            base = (long) Math.max(1, base * factor);
        }
        return base;
    }
}