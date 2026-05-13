package com.economymod.economy;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class TransactionService {

    /**
     * Универсальная транзакция между любыми IEconomicActor.
     * Поддерживает PlayerActor (PlayerInventory) и обычные SimpleContainer.
     */
    public static boolean processTransaction(IEconomicActor seller, IEconomicActor buyer,
                                             ItemStack item, long pricePerItem, int amount) {
        if (amount <= 0) return false;
        long totalPrice = pricePerItem * amount;
        if (!buyer.canAfford(totalPrice)) return false;
        if (!canRemoveItems(seller, item, amount)) return false;
        if (!canAddItems(buyer, item, amount)) return false;

        // Списываем деньги
        buyer.setBalance(buyer.getBalance() - totalPrice);
        seller.setBalance(seller.getBalance() + totalPrice);

        // Перемещаем предметы
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

    public static boolean canAddItems(IEconomicActor actor, ItemStack item, int amount) {
        if (actor instanceof PlayerActor playerActor) {
            // Проще: проверяем через стандартный add, но без реального добавления
            Inventory inv = playerActor.getPlayerInventory();
            int freeSlot = inv.getFreeSlot();
            if (freeSlot >= 0) return true; // есть пустой слот
            // Ищем слот с таким же предметом и свободным местом
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (ItemStack.isSameItemSameComponents(s, item) && s.getCount() + amount <= s.getMaxStackSize()) {
                    return true;
                }
            }
            return false;
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
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) { inv.setItem(i, copy); return; }
                else if (ItemStack.isSameItemSameComponents(s, copy) && s.getCount() + copy.getCount() <= copy.getMaxStackSize()) {
                    s.grow(copy.getCount()); return;
                }
            }
        }
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
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) { inv.setItem(i, copy); return true; }
            else if (ItemStack.isSameItemSameComponents(s, copy) && s.getCount() + copy.getCount() <= copy.getMaxStackSize()) {
                s.grow(copy.getCount()); return true;
            }
        }
        return false;
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