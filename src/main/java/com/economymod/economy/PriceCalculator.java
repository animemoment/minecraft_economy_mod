package com.economymod.economy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.HashMap;

public class PriceCalculator {

    private static ItemPriceTable serverPriceTable;
    private static Map<Item, Double> clientPriceTable = new HashMap<>();

    // Установка таблицы на сервере
    public static void setPriceTable(ItemPriceTable table) {
        serverPriceTable = table;
    }

    // Синхронизация с клиентом (принимает Long из пакета и хранит как Double)
    public static void setClientPriceTable(Map<Item, Long> longTable) {
        clientPriceTable.clear();
        longTable.forEach((item, price) -> clientPriceTable.put(item, price.doubleValue()));
    }

    public static ItemPriceTable getPriceTable() {
        return serverPriceTable;
    }

    public static boolean isPriceTableReady() {
        return serverPriceTable != null || !clientPriceTable.isEmpty();
    }

    // Получение «сырой» цены за 1 шт
    public static double getRawPrice(Item item) {
        if (serverPriceTable != null) {
            return serverPriceTable.getPrice(item);
        }
        return clientPriceTable.getOrDefault(item, 0.10);
    }

    // Цена покупки (для обычных сделок, с наценкой)
    public static long getBuyPrice(ItemStack stack, Object info) {
        double price = getRawPrice(stack.getItem()) * stack.getCount();
        return (long) Math.max(1, Math.round(price * 1.2));
    }

    // Цена продажи (для обычных сделок, со скидкой)
    public static long getSellPrice(ItemStack stack, Object info) {
        double price = getRawPrice(stack.getItem()) * stack.getCount();
        return (long) Math.max(1, Math.round(price * 0.8));
    }

    // Динамическая цена (для совместимости с твоим EconomyTraderEntity)
    public static long calculateDynamicPrice(ItemStack stack, Object info) {
        return getBuyPrice(stack, info);
    }

    public static double calculateStackPrice(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        return getRawPrice(stack.getItem()) * stack.getCount();
    }
}