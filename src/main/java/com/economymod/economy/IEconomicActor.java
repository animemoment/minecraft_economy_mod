package com.economymod.economy;

import net.minecraft.world.SimpleContainer;

public interface IEconomicActor {
    SimpleContainer getInventory();
    long getBalance();
    void setBalance(long balance);
    default boolean canAfford(long amount) { return getBalance() >= amount; }
    String getActorDisplayName();
}