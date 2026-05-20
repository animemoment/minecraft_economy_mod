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

    public static ItemPriceTable getPriceTable() {
        EconomyManager manager = EconomyMod.getEconomyManager();
        return (manager != null) ? manager.getPriceTable() : null;
    }

    // ИСПРАВЛЕНО: Безопасное разделение потоков клиента и сервера для исключения гиперинфляции в синглплеере
    public static double getRawPrice(Item item) {
        // Если мы на физическом клиенте И в потоке рендера игры (клиентская часть)
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            String threadName = Thread.currentThread().getName();
            if (threadName.contains("Render") || threadName.contains("Client")) {
                if (!clientPriceTable.isEmpty()) {
                    return clientPriceTable.getOrDefault(item, 0.50);
                }
            }
        }

        // Если мы на сервере (или в серверном потоке синглплеера) — ВСЕГДА используем чистые базовые цены
        EconomyManager manager = EconomyMod.getEconomyManager();
        return (manager != null && manager.getPriceTable() != null) ? manager.getPriceTable().getPrice(item) : 0.50;
    }

    public static double calculateDynamicPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        if (stack.isEmpty()) return 0.0;
        double base = getRawPrice(stack.getItem());
        if (info != null) {
            base = base * info.getSupplyDemandFactor(stack.getItem()) * info.getInflationRate();
        }
        return Math.max(0.01, base);
    }

    public static double getBuyPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        double price = calculateDynamicPrice(stack, info) * 1.10;
        return Math.round(price * 100.0) / 100.0;
    }

    public static double getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        double price = calculateDynamicPrice(stack, info) * 0.90;
        return Math.max(0.01, Math.round(price * 100.0) / 100.0);
    }

    public static boolean isPriceTableReady() {
        return !clientPriceTable.isEmpty() || (EconomyMod.getEconomyManager() != null && EconomyMod.getEconomyManager().getPriceTable() != null);
    }
}