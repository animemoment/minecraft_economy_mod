package com.economymod.economy.data;

import com.economymod.economy.VillageEconomy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class WorldEconomySavedData extends SavedData {
    private final Map<Item, Double> globalPrices = new HashMap<>();

    public static WorldEconomySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WorldEconomySavedData::new,
                        WorldEconomySavedData::load,
                        null
                ),
                "global_economy"
        );
    }

    public void updateGlobalPrices(Map<BlockPos, VillageEconomy> allVillages) {
        Map<Item, Double> sums = new HashMap<>();
        Map<Item, Integer> counts = new HashMap<>();

        for (VillageEconomy economy : allVillages.values()) {
            for (Item item : BuiltInRegistries.ITEM) {
                double price = economy.getPrice(item);
                sums.merge(item, price, Double::sum);
                counts.merge(item, 1, Integer::sum);
            }
        }

        sums.forEach((item, sum) -> globalPrices.put(item, sum / counts.get(item)));
        setDirty();
    }

    public static WorldEconomySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldEconomySavedData data = new WorldEconomySavedData();
        CompoundTag prices = tag.getCompound("GlobalPrices");
        for (String key : prices.getAllKeys()) {
            ResourceLocation loc = ResourceLocation.tryParse(key);
            if (loc != null) {
                Item item = BuiltInRegistries.ITEM.get(loc);
                data.globalPrices.put(item, prices.getDouble(key));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag prices = new CompoundTag();
        globalPrices.forEach((item, price) -> {
            prices.putDouble(BuiltInRegistries.ITEM.getKey(item).toString(), price);
        });
        tag.put("GlobalPrices", prices);
        return tag;
    }
}