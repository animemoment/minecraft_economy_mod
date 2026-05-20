package com.economymod.economy;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class TransactionService {

    public static boolean processTransaction(IEconomicActor seller, IEconomicActor buyer,
                                             ItemStack item, double pricePerItem, int amount) {
        if (amount <= 0) return false;

        double totalPrice = pricePerItem * (double)amount;

        if (!buyer.canAfford(totalPrice)) return false;
        if (!canRemoveItems(seller, item, amount)) return false;
        if (!canAddItems(buyer, item, amount)) return false;

        buyer.setBalance(buyer.getBalance() - totalPrice);
        seller.setBalance(seller.getBalance() + totalPrice);

        removeItems(seller, item, amount);
        addItems(buyer, item, amount);
        return true;
    }

    public static boolean canRemoveItems(IEconomicActor actor, ItemStack item, int amount) {
        if (actor instanceof PlayerActor playerActor) {
            return countItems(playerActor.getPlayerInventory(), item) >= amount;
        }
        SimpleContainer inv = actor.getInventory();
        if (inv == null) return false;
        return countItems(inv, item) >= amount;
    }

    // ИСПРАВЛЕНО: Умный расчет свободного места во всех слотах суммарно
    public static boolean canAddItems(IEconomicActor actor, ItemStack item, int amount) {
        if (actor instanceof PlayerActor playerActor) {
            Inventory inv = playerActor.getPlayerInventory();
            int free = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) {
                    free += item.getMaxStackSize();
                } else if (ItemStack.isSameItemSameComponents(s, item)) {
                    free += item.getMaxStackSize() - s.getCount();
                }
            }
            return free >= amount;
        }
        SimpleContainer inv = actor.getInventory();
        if (inv == null) return false;
        int free = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                free += item.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(s, item)) {
                free += item.getMaxStackSize() - s.getCount();
            }
        }
        return free >= amount;
    }

    public static void removeItems(IEconomicActor actor, ItemStack item, int amount) {
        if (actor instanceof PlayerActor playerActor) {
            removeFromInventory(playerActor.getPlayerInventory(), item, amount);
            return;
        }
        SimpleContainer inv = actor.getInventory();
        if (inv != null) removeFromInventory(inv, item, amount);
    }

    // ИСПРАВЛЕНО: Используем безопасный ванильный метод SimpleContainer.addItem()
    public static void addItems(IEconomicActor actor, ItemStack item, int amount) {
        if (actor instanceof PlayerActor playerActor) {
            ItemStack copy = item.copy();
            copy.setCount(amount);
            playerActor.getPlayerInventory().add(copy);
            return;
        }
        SimpleContainer inv = actor.getInventory();
        if (inv != null) {
            ItemStack copy = item.copy();
            copy.setCount(amount);
            // Позволяем ванильному коду безопасно раскидать предметы по всем слотам
            inv.addItem(copy);
        }
    }

    // ИСПРАВЛЕНО: Безопасный метод с возвратом результата
    public static boolean addItemsSafe(IEconomicActor actor, ItemStack item, int amount) {
        if (actor instanceof PlayerActor playerActor) {
            ItemStack copy = item.copy();
            copy.setCount(amount);
            return playerActor.getPlayerInventory().add(copy);
        }
        SimpleContainer inv = actor.getInventory();
        if (inv == null) return false;
        ItemStack copy = item.copy();
        copy.setCount(amount);
        ItemStack remaining = inv.addItem(copy);
        return remaining.isEmpty(); // Возвращает true, если всё успешно влезло
    }

    private static int countItems(SimpleContainer inv, ItemStack item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, item)) count += s.getCount();
        }
        return count;
    }

    private static int countItems(Inventory inv, ItemStack item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, item)) count += s.getCount();
        }
        return count;
    }

    private static void removeFromInventory(SimpleContainer inv, ItemStack item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, item)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void removeFromInventory(Inventory inv, ItemStack item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, item)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }
}