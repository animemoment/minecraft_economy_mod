package com.economymod.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.HolderLookup;

import java.util.HashMap;
import java.util.Map;

public class VillageEconomy {
    private final Map<Item, Double> prices = new HashMap<>();
    private final Map<Item, Integer> demand = new HashMap<>();
    private final Map<Item, Integer> supply = new HashMap<>();

    public double getPrice(Item item) {
        return prices.getOrDefault(item, 10.0);
    }

    public void recordTransaction(Item item, int amount, boolean isPurchase) {
        if (isPurchase) {
            demand.merge(item, amount, Integer::sum);
        } else {
            supply.merge(item, amount, Integer::sum);
        }
    }

    public void updatePrices() {
        for (Item item : BuiltInRegistries.ITEM) {
            int d = demand.getOrDefault(item, 0);
            int s = supply.getOrDefault(item, 0);
            if (d == 0 && s == 0) continue;

            double currentPrice = prices.getOrDefault(item, 10.0);
            double ratio = (double) (d + 1) / (s + 1);
            double targetPrice = currentPrice * ratio;
            double newPrice = currentPrice + (targetPrice - currentPrice) * 0.15;
            prices.put(item, Math.max(0.1, newPrice));
        }
        demand.clear();
        supply.clear();
    }

    public CompoundTag save(CompoundTag tag) {
        CompoundTag priceTag = new CompoundTag();
        prices.forEach((item, price) -> {
            priceTag.putDouble(BuiltInRegistries.ITEM.getKey(item).toString(), price);
        });
        tag.put("Prices", priceTag);
        return tag;
    }

    public static VillageEconomy load(CompoundTag tag) {
        VillageEconomy economy = new VillageEconomy();
        CompoundTag priceTag = tag.getCompound("Prices");
        for (String key : priceTag.getAllKeys()) {
            ResourceLocation loc = ResourceLocation.tryParse(key);
            if (loc != null) {
                Item item = BuiltInRegistries.ITEM.get(loc);
                if (item != Items.AIR) {
                    economy.prices.put(item, priceTag.getDouble(key));
                }
            }
        }
        return economy;
    }
}