package com.economymod.economy;

import com.economymod.EconomyMod;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class PriceCalculator {
    private static Map<Item, Double> clientPriceTable = new HashMap<>();

    // Потокобезопасный реактивный кэш цен для деревень: КоординатыДеревни -> (Предмет -> СкомпилированнаяЦена)
    private static final Map<BlockPos, Map<Item, CachedPrice>> villagePriceCache = new ConcurrentHashMap<>();

    private static class CachedPrice {
        final double buyPrice;
        final double sellPrice;
        CachedPrice(double buyPrice, double sellPrice) {
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }

    public static void setClientPriceTable(Map<Item, Double> table) { clientPriceTable = table; }

    public static ItemPriceTable getPriceTable() {
        EconomyManager manager = EconomyMod.getEconomyManager();
        return (manager != null) ? manager.getPriceTable() : null;
    }

    public static double getRawPrice(Item item) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            String threadName = Thread.currentThread().getName();
            if (threadName.contains("Render") || threadName.contains("Client")) {
                if (!clientPriceTable.isEmpty()) {
                    return clientPriceTable.getOrDefault(item, 0.50);
                }
            }
        }
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
        if (stack.isEmpty()) return 0.0;
        if (info == null) {
            double price = calculateDynamicPrice(stack, null) * 1.10;
            return Math.round(price * 100.0) / 100.0;
        }

        // Получаем координаты деревни и ищем кэш
        BlockPos center = info.getCenter();
        Map<Item, CachedPrice> villageMap = villagePriceCache.computeIfAbsent(center, k -> new ConcurrentHashMap<>());
        CachedPrice cached = villageMap.get(stack.getItem());

        if (cached != null) {
            return cached.buyPrice; // Мгновенный возврат скомпилированной цены из ОЗУ без вычислений
        }

        // Если кэша нет — рассчитываем и компилируем пару цен в кэш
        double calculatedBuy = calculateDynamicPrice(stack, info) * 1.10;
        double finalBuy = Math.round(calculatedBuy * 100.0) / 100.0;

        double calculatedSell = calculateDynamicPrice(stack, info) * 0.90;
        double finalSell = Math.max(0.01, Math.round(calculatedSell * 100.0) / 100.0);

        villageMap.put(stack.getItem(), new CachedPrice(finalBuy, finalSell));
        return finalBuy;
    }

    public static double getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo info) {
        if (stack.isEmpty()) return 0.0;
        if (info == null) {
            double price = calculateDynamicPrice(stack, null) * 0.90;
            return Math.max(0.01, Math.round(price * 100.0) / 100.0);
        }

        // Получаем координаты деревни и ищем кэш
        BlockPos center = info.getCenter();
        Map<Item, CachedPrice> villageMap = villagePriceCache.computeIfAbsent(center, k -> new ConcurrentHashMap<>());
        CachedPrice cached = villageMap.get(stack.getItem());

        if (cached != null) {
            return cached.sellPrice; // Мгновенный возврат скомпилированной цены из ОЗУ без вычислений
        }

        // Если кэша нет — рассчитываем и компилируем пару цен в кэш
        double calculatedBuy = calculateDynamicPrice(stack, info) * 1.10;
        double finalBuy = Math.round(calculatedBuy * 100.0) / 100.0;

        double calculatedSell = calculateDynamicPrice(stack, info) * 0.90;
        double finalSell = Math.max(0.01, Math.round(calculatedSell * 100.0) / 100.0);

        villageMap.put(stack.getItem(), new CachedPrice(finalBuy, finalSell));
        return finalSell;
    }

    /**
     * Реактивная инвалидация кэша цен при изменении рыночных переменных
     */
    public static void invalidateCache(BlockPos villageCenter, Item item) {
        if (villageCenter == null) return;
        Map<Item, CachedPrice> villageMap = villagePriceCache.get(villageCenter);
        if (villageMap != null) {
            if (item == null) {
                villageMap.clear(); // Полный сброс кэша деревни при ежедневном перерасчете рынка
            } else {
                villageMap.remove(item); // Точечный сброс цены конкретного предмета при сделке
            }
        }
    }

    public static boolean isPriceTableReady() {
        return !clientPriceTable.isEmpty() || (EconomyMod.getEconomyManager() != null && EconomyMod.getEconomyManager().getPriceTable() != null);
    }
}