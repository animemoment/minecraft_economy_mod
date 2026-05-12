package com.economymod.economy;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class VillageEconomyManager {

    public static void updatePrices(ServerLevel level) {
        VillageNetworkData data = VillageNetworkData.get(level);
        for (Map.Entry<BlockPos, VillageNetworkData.VillageInfo> entry : data.getAllVillagesWithPos()) {
            BlockPos pos = entry.getKey();
            VillageNetworkData.VillageInfo info = entry.getValue();
            List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                    new AABB(pos).inflate(64),
                    v -> v.isAlive());
            Map<Item, Integer> totalDemand = new HashMap<>();
            Map<Item, Integer> totalSupply = new HashMap<>();
            for (Villager v : villagers) {
                VillagerAttachment att = v.getData(ModAttachments.VILLAGER.get());
                for (VillagerAttachment.Demand d : att.getDemands()) {
                    totalDemand.merge(d.stack.getItem(), d.stack.getCount(), Integer::sum);
                }
                for (VillagerAttachment.Offer o : att.getOffers()) {
                    totalSupply.merge(o.stack.getItem(), o.stack.getCount(), Integer::sum);
                }
            }
            info.recalcFactors(totalDemand, totalSupply, villagers.size());
        }
    }
}