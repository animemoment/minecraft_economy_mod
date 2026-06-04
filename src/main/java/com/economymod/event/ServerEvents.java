package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.economy.EconomyManager;
import com.economymod.entity.ai.AsyncBrainStorage;
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

        // Передаем ссылку на сервер для фонового потока асинхронного сохранения
        AsyncBrainStorage.ServerLevelProvider.setServer(event.getServer());

        EconomyMod.LOGGER.info("ServerEvents: EconomyManager и AsyncBrainStorage успешно инициализированы.");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EconomyMod.setEconomyManager(null);

        // Обнуляем ссылку при выгрузке сервера для предотвращения утечек памяти в ОЗУ
        AsyncBrainStorage.ServerLevelProvider.setServer(null);

        EconomyMod.clearLootQueue();
        EconomyMod.LOGGER.info("ServerEvents: EconomyManager выгружен, очередь задержки лута очищена.");
    }
}