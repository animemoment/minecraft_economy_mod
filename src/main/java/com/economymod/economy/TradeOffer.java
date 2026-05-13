package com.economymod.economy;

import net.minecraft.world.item.ItemStack;

public class TradeOffer {
    private final ItemStack itemStack;
    private long price;
    private final ITrader owner;

    public TradeOffer(ItemStack itemStack, long price, ITrader owner) {
        this.itemStack = itemStack.copy();
        this.price = price;
        this.owner = owner;
    }

    public ItemStack getItemStack() { return itemStack.copy(); }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public ITrader getOwner() { return owner; }
}