package com.economymod.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import java.util.*;

public class WorldEconomySavedData extends SavedData {
    private final Map<Item, Double> globalAveragePrices = new HashMap<>();

    public static WorldEconomySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldEconomySavedData::new, (tag, provider) -> new WorldEconomySavedData()),
                "global_prices"
        );
    }

    // ОПТИМИЗИРОВАНО: Перебираем только те предметы, которые есть в экономике
    public void updateGlobal(Collection<VillageNetworkData.VillageInfo> allVillages) {
        Map<Item, Double> sums = new HashMap<>();
        Map<Item, Integer> counts = new HashMap<>();

        for (var village : allVillages) {
            Set<Item> activeItems = village.getActiveItems(); // Используем новый геттер
            for (Item item : activeItems) {
                double price = village.getSupplyDemandFactor(item);
                sums.merge(item, price, Double::sum);
                counts.merge(item, 1, Integer::sum);
            }
        }

        sums.forEach((item, sum) -> globalAveragePrices.put(item, sum / counts.get(item)));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag prices = new CompoundTag();
        globalAveragePrices.forEach((item, val) -> prices.putDouble(BuiltInRegistries.ITEM.getKey(item).toString(), val));
        tag.put("GlobalPrices", prices);
        return tag;
    }
}