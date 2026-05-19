package com.economymod.economy;

import com.economymod.EconomyMod;
import com.economymod.world.VillageNetworkData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.HashMap;

public class PriceCalculator {
    private static Map<Item, Double> clientPriceTable = new HashMap<>();

    public static void setClientPriceTable(Map<Item, Double> table) { clientPriceTable = table; }

    public static double getRawPrice(Item item) {
        if (!clientPriceTable.isEmpty()) return clientPriceTable.getOrDefault(item, 0.50);
        EconomyManager manager = EconomyMod.getEconomyManager();
        return (manager != null && manager.getPriceTable() != null) ? manager.getPriceTable().getPrice(item) : 0.50;
    }

    public static double calculateDynamicPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        if (stack.isEmpty()) return 0.0;
        double base = getRawPrice(stack.getItem());
        if (info != null) base = base * info.getSupplyDemandFactor(stack.getItem()) * info.getInflationRate();
        return Math.max(0.01, base); // Минимум 1 копейка
    }

    public static double getBuyPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        // Наценка 10%
        double price = calculateDynamicPrice(stack, info) * 1.10;
        return Math.round(price * 100.0) / 100.0; // Округление до 2 знаков
    }

    public static double getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        // Скидка 10% при продаже игроком
        double price = calculateDynamicPrice(stack, info) * 0.90;
        return Math.max(0.01, Math.round(price * 100.0) / 100.0);
    }

    public static boolean isPriceTableReady() {
        return !clientPriceTable.isEmpty() || (EconomyMod.getEconomyManager() != null && EconomyMod.getEconomyManager().getPriceTable() != null);
    }
}