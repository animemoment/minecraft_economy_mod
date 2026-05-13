package com.economymod.economy;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

public class VillageEconomyManager {

    private static final long RECALC_COOLDOWN_TICKS = 20L;

    public static class VillageInfo {
        private final BlockPos center;
        private final Map<Item, Double> supplyDemandFactors = new HashMap<>();
        private final Map<Item, Double> averageDesires = new HashMap<>();
        private long lastRecalcTick = -RECALC_COOLDOWN_TICKS;

        public VillageInfo(BlockPos center) {
            this.center = center;
        }

        public BlockPos getCenter() {
            return center;
        }

        public double getSupplyDemandFactor(Item item) {
            return supplyDemandFactors.getOrDefault(item, 1.0);
        }

        public double getAverageDesire(ItemStack stack) {
            return averageDesires.getOrDefault(stack.getItem(), 0.0);
        }

        public void recalcFactors(Level level) {
            long gameTime = level.getGameTime();
            if (gameTime - lastRecalcTick < RECALC_COOLDOWN_TICKS) return;
            lastRecalcTick = gameTime;

            List<Villager> villagers = level.getEntitiesOfClass(
                    Villager.class,
                    new net.minecraft.world.phys.AABB(center).inflate(64.0)
            );

            if (villagers.isEmpty()) return;

            Map<Item, Double> totalDemand = new HashMap<>();
            Map<Item, Double> totalSupply = new HashMap<>();
            Map<Item, Double> totalDesire = new HashMap<>();
            Map<Item, Integer> desireCount = new HashMap<>();

            for (Villager villager : villagers) {
                VillagerAttachment attachment = villager.getData(ModAttachments.VILLAGER.get());
                if (attachment == null) continue;

                for (VillagerAttachment.Demand demand : attachment.getDemands()) {
                    Item item = demand.stack.getItem();
                    totalDemand.merge(item, (double) demand.stack.getCount(), Double::sum);
                }

                for (VillagerAttachment.Offer offer : attachment.getOffers()) {
                    Item item = offer.stack.getItem();
                    totalSupply.merge(item, (double) offer.stack.getCount(), Double::sum);
                }

                for (int i = 0; i < attachment.getInventory().getContainerSize(); i++) {
                    ItemStack stack = attachment.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        double desire = attachment.wantsToBuy(stack) ? 1.0 : 0.0;
                        totalDesire.merge(stack.getItem(), desire, Double::sum);
                        desireCount.merge(stack.getItem(), 1, Integer::sum);
                    }
                }
            }

            supplyDemandFactors.clear();
            Set<Item> allItems = new HashSet<>();
            allItems.addAll(totalDemand.keySet());
            allItems.addAll(totalSupply.keySet());

            for (Item item : allItems) {
                double demand = totalDemand.getOrDefault(item, 0.0);
                double supply = totalSupply.getOrDefault(item, 0.0);

                if (supply < 1.0) supply = 1.0;
                double factor = demand / supply;
                if (factor < 0.1) factor = 0.1;
                if (factor > 5.0) factor = 5.0;
                supplyDemandFactors.put(item, factor);
            }

            averageDesires.clear();
            for (Map.Entry<Item, Double> entry : totalDesire.entrySet()) {
                Item item = entry.getKey();
                int count = desireCount.getOrDefault(item, 1);
                averageDesires.put(item, entry.getValue() / count);
            }
        }
    }
}