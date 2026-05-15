package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.economy.ItemPriceTable;
import com.economymod.economy.PriceCalculator;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class ServerEvents {

    private static int priceUpdateCounter = 0;
    private static boolean tableBuilt = false;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        priceUpdateCounter++;
        if (priceUpdateCounter >= 100) {
            priceUpdateCounter = 0;
        }
        // Построим таблицу на первом тике, когда сервер точно загружен
        if (!tableBuilt && event.getServer().getLevel(ServerLevel.OVERWORLD) != null) {
            ServerLevel overworld = event.getServer().getLevel(ServerLevel.OVERWORLD);
            ItemPriceTable table = new ItemPriceTable(event.getServer().getRecipeManager(), overworld);
            PriceCalculator.setPriceTable(table);
            tableBuilt = true;
            EconomyMod.LOGGER.info("ItemPriceTable built on first tick");
        }
    }
}