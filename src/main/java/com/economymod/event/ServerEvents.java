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
        // Получаем главный мир (Overworld)
        ServerLevel overworld = event.getServer().overworld();

        // Создаем EconomyManager.
        // Внутри его конструктора автоматически создастся ItemPriceTable (кэш цен).
        EconomyManager manager = new EconomyManager(overworld);

        // Сохраняем менеджер в главном классе мода
        EconomyMod.setEconomyManager(manager);

        EconomyMod.LOGGER.info("ServerEvents: EconomyManager успешно инициализирован.");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Очищаем менеджер при остановке сервера, чтобы избежать утечек памяти
        EconomyMod.setEconomyManager(null);
        EconomyMod.LOGGER.info("ServerEvents: EconomyManager выгружен.");
    }
}