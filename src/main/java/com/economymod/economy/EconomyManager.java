package com.economymod.economy;

import com.economymod.EconomyMod;
import com.economymod.attachment.PlayerEconomyAttachment;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class EconomyManager {
    private final ServerLevel level;
    private ItemPriceTable priceTable; // КЭШ базовых цен
    private int tickCounter = 0;

    public EconomyManager(ServerLevel level) {
        this.level = level;
        // 1. Строим таблицу цен ОДИН РАЗ при старте сервера
        this.priceTable = new ItemPriceTable(level.getRecipeManager(), level);
        EconomyMod.LOGGER.info("EconomyManager: Таблица цен успешно закэширована.");
    }

    public ItemPriceTable getPriceTable() {
        return priceTable;
    }

    // 2. Фоновый пересчет экономики (Вызывать из EconomyMod.onLevelTick)
    public void tick() {
        tickCounter++;
        // Пересчитываем экономику раз в 1200 тиков (1 минута)
        if (tickCounter >= 1200) {
            tickCounter = 0;
            VillageNetworkData data = VillageNetworkData.get(level);

            for (VillageNetworkData.VillageInfo info : data.getAllVillages()) {
                // Сканируем жителей асинхронно/фоном
                info.recalcFactors(level);
                // Применяем влияние сделок
                info.updateDailyEconomy();
            }
            data.setDirty();
            EconomyMod.LOGGER.debug("EconomyManager: Фоновый пересчет экономики завершен.");
        }
    }

    // --- Методы кошелька игрока ---
    public void addCoins(Player player, long amount) {
        if (player instanceof ServerPlayer sp) {
            sp.getData(ModAttachments.PLAYER_ECONOMY.get()).add(amount);
        }
    }

    public boolean removeCoins(Player player, long amount) {
        if (player instanceof ServerPlayer sp) {
            return sp.getData(ModAttachments.PLAYER_ECONOMY.get()).subtract(amount);
        }
        return false;
    }

    public long getBalance(Player player) {
        if (player instanceof ServerPlayer sp) {
            return sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance();
        }
        return 0L;
    }
}