package com.economymod.economy;

import com.economymod.world.VillageNetworkData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PriceCalculator {

    @Nullable
    private static ItemPriceTable priceTable = null;

    // Клиентская таблица, полученная с сервера
    @Nullable
    private static Map<Item, Long> clientPriceTable = null;

    public static void setPriceTable(@Nullable ItemPriceTable table) {
        priceTable = table;
    }

    public static boolean isPriceTableReady() {
        return priceTable != null;
    }

    @Nullable
    public static ItemPriceTable getPriceTable() {
        return priceTable;
    }

    public static void setClientPriceTable(@Nullable Map<Item, Long> table) {
        clientPriceTable = table;
    }

    public static long calculateDynamicPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        long basePrice = getBasePrice(stack.getItem());
        if (villageInfo == null || stack.isEmpty()) return basePrice;
        double D = villageInfo.getDemandFactor(stack.getItem());
        double S = villageInfo.getSupplyFactor(stack.getItem());
        double E = Math.max(0.25, Math.min(4.0, D / S));
        double I = villageInfo.getInflationRate();
        double R = 1.0 + (64.0 - stack.getMaxStackSize()) / 64.0 * 0.5;
        double U = 1.0 + villageInfo.getAverageDesire(stack.getItem()) * 0.5;
        long adjusted = (long) (basePrice * E * I * R * U);
        return Math.max(1L, adjusted);
    }

    public static long getBuyPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        return calculateDynamicPrice(stack, villageInfo);
    }

    public static long getSellPrice(ItemStack stack, VillageNetworkData.VillageInfo villageInfo) {
        return Math.max(1L, getBasePrice(stack.getItem()) / 2);
    }

    public static long getBasePrice(Item item) {
        // 1. Серверная таблица
        if (priceTable != null) {
            return priceTable.getPrice(item);
        }
        // 2. Клиентская таблица (получена с сервера)
        if (clientPriceTable != null) {
            return clientPriceTable.getOrDefault(item, getFallbackPrice(item));
        }
        // 3. Статический fallback
        return getFallbackPrice(item);
    }

    private static long getFallbackPrice(Item item) {
        if (item == Items.DIAMOND) return 40L;
        if (item == Items.EMERALD) return 50L;
        if (item == Items.IRON_INGOT) return 7L;
        if (item == Items.GOLD_INGOT) return 12L;
        if (item == Items.COAL) return 2L;
        if (item == Items.STICK) return 1L;
        if (item == Items.OAK_PLANKS) return 1L;
        if (item == Items.OAK_LOG) return 2L;
        if (item == Items.IRON_ORE) return 5L;
        if (item == Items.GOLD_ORE) return 8L;
        if (item == Items.DIAMOND_ORE) return 30L;
        if (item == Items.EMERALD_ORE) return 40L;
        if (item == Items.RAW_IRON) return 5L;
        if (item == Items.RAW_GOLD) return 8L;
        if (item == Items.RAW_COPPER) return 3L;
        if (item == Items.STONE) return 1L;
        if (item == Items.COBBLESTONE) return 1L;
        if (item == Items.DIRT) return 1L;
        if (item.getDefaultMaxStackSize() <= 16) return 8L;
        return 1L;
    }
}