package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.economy.VillageEconomyManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class ServerEvents {

    private static int priceUpdateCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        priceUpdateCounter++;
        if (priceUpdateCounter >= 100) {
            priceUpdateCounter = 0;
            ServerLevel level = event.getServer().getLevel(ServerLevel.OVERWORLD);
            if (level != null) {
                VillageEconomyManager.updatePrices(level);
            }
        }
    }
}