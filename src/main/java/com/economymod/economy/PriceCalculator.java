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
        return Math.max(0.1, base);
    }

    public static long getBuyPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        return (long) Math.ceil(calculateDynamicPrice(stack, info) * 1.10);
    }

    public static long getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        return (long) Math.max(1L, Math.floor(calculateDynamicPrice(stack, info) * 0.90));
    }

    // ВОЗВРАЩЕНО: проверка готовности данных
    public static boolean isPriceTableReady() {
        return !clientPriceTable.isEmpty() || (EconomyMod.getEconomyManager() != null && EconomyMod.getEconomyManager().getPriceTable() != null);
    }
}