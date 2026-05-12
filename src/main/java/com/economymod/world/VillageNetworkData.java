package com.economymod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public class VillageNetworkData extends SavedData {
    private static final String DATA_NAME = "economymod_village_network";
    private final Map<BlockPos, VillageInfo> villages = new HashMap<>();

    public static VillageNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        VillageNetworkData::new,
                        VillageNetworkData::load
                ),
                DATA_NAME
        );
    }

    public VillageInfo getVillageInfo(BlockPos pos) {
        return villages.computeIfAbsent(pos, k -> new VillageInfo());
    }

    public Collection<VillageInfo> getAllVillages() {
        return villages.values();
    }

    public Set<BlockPos> getAllVillagePositions() {
        return villages.keySet();
    }

    public static class VillageInfo {
        private final Map<Item, Double> demandFactors = new HashMap<>();
        private final Map<Item, Double> supplyFactors = new HashMap<>();
        private double inflationRate = 1.0;

        public double getDemandFactor(Item item) {
            return demandFactors.getOrDefault(item, 1.0);
        }
        public double getSupplyFactor(Item item) {
            return supplyFactors.getOrDefault(item, 1.0);
        }
        public double getInflationRate() { return inflationRate; }
        public void setInflationRate(double rate) { this.inflationRate = Math.max(0.1, Math.min(10.0, rate)); }

        public void recalcFactors(Map<Item, Integer> totalDemand, Map<Item, Integer> totalSupply, int population) {
            demandFactors.clear();
            supplyFactors.clear();
            double popFactor = population * 10.0 + 1;
            for (Map.Entry<Item, Integer> entry : totalDemand.entrySet()) {
                Item item = entry.getKey();
                int dem = entry.getValue();
                demandFactors.put(item, Math.min(2.0, Math.max(0.5, 1.0 + (double) dem / popFactor)));
            }
            for (Map.Entry<Item, Integer> entry : totalSupply.entrySet()) {
                Item item = entry.getKey();
                int sup = entry.getValue();
                supplyFactors.put(item, Math.min(2.0, Math.max(0.5, 1.0 - (double) sup / popFactor)));
            }
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("InflationRate", inflationRate);
            CompoundTag demTag = new CompoundTag();
            for (Map.Entry<Item, Double> e : demandFactors.entrySet()) {
                demTag.putDouble(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue());
            }
            tag.put("DemandFactors", demTag);
            CompoundTag supTag = new CompoundTag();
            for (Map.Entry<Item, Double> e : supplyFactors.entrySet()) {
                supTag.putDouble(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue());
            }
            tag.put("SupplyFactors", supTag);
            return tag;
        }

        public static VillageInfo fromNBT(CompoundTag tag) {
            VillageInfo info = new VillageInfo();
            if (tag.contains("InflationRate")) {
                info.inflationRate = tag.getDouble("InflationRate");
            }
            if (tag.contains("DemandFactors")) {
                CompoundTag demTag = tag.getCompound("DemandFactors");
                for (String key : demTag.getAllKeys()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
                    if (item != Items.AIR) {
                        info.demandFactors.put(item, demTag.getDouble(key));
                    }
                }
            }
            if (tag.contains("SupplyFactors")) {
                CompoundTag supTag = tag.getCompound("SupplyFactors");
                for (String key : supTag.getAllKeys()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
                    if (item != Items.AIR) {
                        info.supplyFactors.put(item, supTag.getDouble(key));
                    }
                }
            }
            return info;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, VillageInfo> entry : villages.entrySet()) {
            CompoundTag villageTag = new CompoundTag();
            villageTag.putLong("Pos", entry.getKey().asLong());
            villageTag.merge(entry.getValue().toNBT());
            list.add(villageTag);
        }
        tag.put("Villages", list);
        return tag;
    }

    public Set<Map.Entry<BlockPos, VillageInfo>> getAllVillagesWithPos() {
        return villages.entrySet();
    }

    public static VillageNetworkData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageNetworkData data = new VillageNetworkData();
        if (tag.contains("Villages")) {
            ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag villageTag = list.getCompound(i);
                BlockPos pos = BlockPos.of(villageTag.getLong("Pos"));
                VillageInfo info = VillageInfo.fromNBT(villageTag);
                data.villages.put(pos, info);
            }
        }
        return data;
    }
}