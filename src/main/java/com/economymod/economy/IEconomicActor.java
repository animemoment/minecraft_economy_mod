package com.economymod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Set;

public interface IEconomicActor {
    SimpleContainer getInventory();
    double getBalance();
    void setBalance(double balance);
    default boolean canAfford(double amount) { return getBalance() >= amount; }
    String getActorDisplayName();
    default boolean wantsToBuy(ItemStack stack) { return false; }
    default Set<Item> getWantedItems() { return Collections.emptySet(); }
    default BlockPos getPosition() { return null; }
    default LivingEntity getEntity() { return null; }
}