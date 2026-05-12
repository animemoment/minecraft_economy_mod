package com.economymod.economy;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public interface ITrader {
    List<TradeOffer> getOffers();
    List<TradeOffer> getDemands();
    boolean canAfford(long price);
    void charge(long amount);
    void credit(long amount);
    long getBalance();
}