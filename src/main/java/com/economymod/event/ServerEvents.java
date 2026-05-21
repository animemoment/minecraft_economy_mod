package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.economy.EconomyManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class ServerEvents {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        EconomyManager manager = new EconomyManager(overworld);
        EconomyMod.setEconomyManager(manager);
        EconomyMod.LOGGER.info("ServerEvents: EconomyManager успешно инициализирован.");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EconomyMod.setEconomyManager(null);
        EconomyMod.clearLootQueue(); // ← ДОБАВЛЕНО: убираем утечку UUID выгруженного сервера
        EconomyMod.LOGGER.info("ServerEvents: EconomyManager выгружен, очередь задержки лута очищена.");
    }
}