package com.economymod.world;

import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class VillageNetworkData extends SavedData {
    private static final String DATA_NAME = "economymod_village_network";
    private final Map<BlockPos, VillageInfo> villages = new HashMap<>();

    private static final Map<Item, Double> BASKET_ITEMS = Map.of(
            Items.BREAD, 2.0,
            Items.COAL, 4.0,
            Items.IRON_INGOT, 1.0,
            Items.OAK_LOG, 3.0
    );

    public static VillageNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageNetworkData::new, VillageNetworkData::load),
                DATA_NAME
        );
    }

    public VillageInfo getVillageInfo(BlockPos pos) {
        if (pos == null) return null;

        // Математическое объединение деревень в радиусе 80 блоков
        for (Map.Entry<BlockPos, VillageInfo> entry : villages.entrySet()) {
            if (entry.getKey().distSqr(pos) < 6400.0) {
                return entry.getValue();
            }
        }
        return villages.computeIfAbsent(pos, VillageInfo::new);
    }

    public Collection<VillageInfo> getAllVillages() {
        return villages.values();
    }

    public Set<BlockPos> getAllVillagePositions() {
        return villages.keySet();
    }

    public void clearAllVillages() {
        this.villages.clear();
    }

    public static class VillageInfo {
        private final BlockPos center;
        private final Map<Item, Double> demandFactors = new HashMap<>();
        private final Map<Item, Double> supplyFactors = new HashMap<>();
        private final Map<Item, Double> priceAdjustments = new HashMap<>();
        private final Map<Item, Integer> dailyTradeVolume = new HashMap<>();
        private double inflationRate = 1.0;
        private long lastRecalcTick = -200L;

        public VillageInfo(BlockPos center) { this.center = center; }
        public BlockPos getCenter() { return center; }

        public Set<Item> getActiveItems() {
            Set<Item> items = new HashSet<>();
            items.addAll(demandFactors.keySet());
            items.addAll(supplyFactors.keySet());
            items.addAll(priceAdjustments.keySet());
            return items;
        }

        public void recordTrade(Item item, int amount) {
            dailyTradeVolume.merge(item, amount, Integer::sum);
        }

        public void updateDailyEconomy() {
            dailyTradeVolume.forEach((item, volume) -> {
                double currentAdj = priceAdjustments.getOrDefault(item, 1.0);
                if (volume > 10) {
                    priceAdjustments.put(item, Math.min(3.0, currentAdj * 1.15));
                } else if (volume < 2) {
                    if (currentAdj > 1.0) {
                        priceAdjustments.put(item, Math.max(1.0, currentAdj * 0.95));
                    } else if (currentAdj < 1.0) {
                        priceAdjustments.put(item, Math.min(1.0, currentAdj * 1.05));
                    }
                }
            });

            priceAdjustments.keySet().forEach(item -> {
                if (!dailyTradeVolume.containsKey(item)) {
                    double currentAdj = priceAdjustments.get(item);
                    if (currentAdj > 1.0) {
                        priceAdjustments.put(item, Math.max(1.0, currentAdj * 0.98));
                    } else if (currentAdj < 1.0) {
                        priceAdjustments.put(item, Math.min(1.0, currentAdj * 1.02));
                    }
                }
            });

            dailyTradeVolume.clear();
        }

        public double getInflationRate() { return inflationRate; }
        public void setInflationRate(double rate) { this.inflationRate = Mth.clamp(rate, 0.2, 5.0); }

        public double getSupplyDemandFactor(Item item) {
            double demand = demandFactors.getOrDefault(item, 0.0);
            double supply = supplyFactors.getOrDefault(item, 0.0);
            double adj = priceAdjustments.getOrDefault(item, 1.0);

            double marketFactor = (demand + 5.0) / (supply + 5.0);
            return Mth.clamp(marketFactor * adj, 0.15, 6.0);
        }

        public void recalcFactors(Level level, boolean force) {
            if (center == null) return;
            if (!force && level.getGameTime() - lastRecalcTick < 200) return;
            lastRecalcTick = level.getGameTime();

            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(64.0));
            if (villagers.isEmpty()) return;

            Map<Item, Integer> totalDemand = new HashMap<>();
            Map<Item, Integer> totalSupply = new HashMap<>();

            for (Villager v : villagers) {
                var att = v.getData(ModAttachments.VILLAGER.get());
                if (att == null) continue;
                att.getDemands().forEach(d -> totalDemand.merge(d.stack.getItem(), d.stack.getCount(), Integer::sum));
                att.getOffers().forEach(o -> totalSupply.merge(o.stack.getItem(), o.stack.getCount(), Integer::sum));
            }

            demandFactors.clear();
            supplyFactors.clear();
            totalDemand.forEach((item, count) -> demandFactors.put(item, (double) count));
            totalSupply.forEach((item, count) -> supplyFactors.put(item, (double) count));

            recalcInflation();
        }

        private void recalcInflation() {
            double baseBasketValue = 0.0;
            double currentBasketValue = 0.0;

            for (Map.Entry<Item, Double> entry : BASKET_ITEMS.entrySet()) {
                Item item = entry.getKey();
                double weight = entry.getValue();
                double basePrice = com.economymod.economy.PriceCalculator.getRawPrice(item);

                baseBasketValue += basePrice * weight;
                currentBasketValue += basePrice * getSupplyDemandFactor(item) * weight;
            }

            if (baseBasketValue > 0) {
                double targetInflation = currentBasketValue / baseBasketValue;
                this.inflationRate = Mth.lerp(0.05, this.inflationRate, targetInflation);
                this.inflationRate = Mth.clamp(this.inflationRate, 0.25, 4.0);
            }
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            if (center != null) tag.putLong("Center", center.asLong());
            tag.putDouble("InflationRate", inflationRate);

            CompoundTag dem = new CompoundTag();
            demandFactors.forEach((i, v) -> dem.putDouble(BuiltInRegistries.ITEM.getKey(i).toString(), v));
            tag.put("DemandFactors", dem);

            CompoundTag sup = new CompoundTag();
            supplyFactors.forEach((i, v) -> sup.putDouble(BuiltInRegistries.ITEM.getKey(i).toString(), v));
            tag.put("SupplyFactors", sup);

            CompoundTag adj = new CompoundTag();
            priceAdjustments.forEach((i, v) -> adj.putDouble(BuiltInRegistries.ITEM.getKey(i).toString(), v));
            tag.put("PriceAdjustments", adj);

            return tag;
        }

        public static VillageInfo fromNBT(CompoundTag tag) {
            VillageInfo info = new VillageInfo(BlockPos.of(tag.getLong("Center")));
            info.inflationRate = tag.contains("InflationRate") ? tag.getDouble("InflationRate") : 1.0;

            CompoundTag dem = tag.getCompound("DemandFactors");
            for (String k : dem.getAllKeys()) {
                Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(k));
                if (i != Items.AIR) info.demandFactors.put(i, dem.getDouble(k));
            }

            CompoundTag sup = tag.getCompound("SupplyFactors");
            for (String k : sup.getAllKeys()) {
                Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(k));
                if (i != Items.AIR) info.supplyFactors.put(i, sup.getDouble(k));
            }

            if (tag.contains("PriceAdjustments")) {
                CompoundTag adj = tag.getCompound("PriceAdjustments");
                for (String k : adj.getAllKeys()) {
                    Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(k));
                    if (i != Items.AIR) info.priceAdjustments.put(i, adj.getDouble(k));
                }
            }

            return info;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        villages.forEach((pos, info) -> {
            CompoundTag vTag = info.toNBT();
            vTag.putLong("Pos", pos.asLong());
            list.add(vTag);
        });
        tag.put("Villages", list);
        return tag;
    }

    public static VillageNetworkData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageNetworkData data = new VillageNetworkData();
        ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag vTag = list.getCompound(i);
            data.villages.put(BlockPos.of(vTag.getLong("Pos")), VillageInfo.fromNBT(vTag));
        }
        return data;
    }
}