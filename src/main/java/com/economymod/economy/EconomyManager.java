package com.economymod.economy;

import com.economymod.EconomyMod;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class EconomyManager {
    private final ServerLevel level;
    private ItemPriceTable priceTable;
    private int tickCounter = 0;

    public EconomyManager(ServerLevel level) {
        this.level = level;
        this.priceTable = new ItemPriceTable(level.getRecipeManager(), level);
        EconomyMod.LOGGER.info("EconomyManager: Таблица цен успешно закэширована.");
    }

    public ItemPriceTable getPriceTable() {
        return priceTable;
    }

    public void tick() {
        tickCounter++;
        if (tickCounter >= 1200) {
            tickCounter = 0;
            VillageNetworkData data = VillageNetworkData.get(level);
            for (VillageNetworkData.VillageInfo info : data.getAllVillages()) {
                info.recalcFactors(level);
                info.updateDailyEconomy();
            }
            data.setDirty();
        }
    }

    // ИСПРАВЛЕНО: double для всех методов
    public void addCoins(Player player, double amount) {
        if (player instanceof ServerPlayer sp) {
            sp.getData(ModAttachments.PLAYER_ECONOMY.get()).add(amount);
        }
    }

    public boolean removeCoins(Player player, double amount) {
        if (player instanceof ServerPlayer sp) {
            return sp.getData(ModAttachments.PLAYER_ECONOMY.get()).subtract(amount);
        }
        return false;
    }

    public double getBalance(Player player) {
        if (player instanceof ServerPlayer sp) {
            return sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance();
        }
        return 0.0;
    }
}