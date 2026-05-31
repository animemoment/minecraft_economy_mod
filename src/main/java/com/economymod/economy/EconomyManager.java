package com.economymod.economy;

import com.economymod.EconomyMod;
import com.economymod.creatures.VillagerBrainWrapper;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

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
        if (tickCounter >= 1200) { // раз в минуту (20 тиков/сек * 60 = 1200)
            tickCounter = 0;
            VillageNetworkData data = VillageNetworkData.get(level);
            for (VillageNetworkData.VillageInfo info : data.getAllVillages()) {
                info.recalcFactors(level, false);
                info.updateDailyEconomy();

                // Влияние стресса жителей на инфляцию
                double avgDistress = getAverageDistressForVillage(info.getCenter(), level);
                info.applyDistressInflation(avgDistress);
            }
            data.setDirty();
        }
    }

    private double getAverageDistressForVillage(net.minecraft.core.BlockPos center, ServerLevel level) {
        AABB area = new AABB(center).inflate(64);
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, v -> true);
        if (villagers.isEmpty()) return 0.0;
        double totalDistress = 0.0;
        int count = 0;
        for (Villager v : villagers) {
            VillagerBrainWrapper wrapper = VillagerBrainWrapper.getOrCreate(v);
            if (wrapper != null) {
                // Для простоты используем только здоровье, голод и усталость пока заглушками
                float health = v.getHealth() / v.getMaxHealth();
                // В будущем добавим голод и усталость из VillagerAttachment
                float distress = wrapper.getLearningSystem().calculateDistress(health, 0f, 0f);
                totalDistress += distress;
                count++;
            }
        }
        return count > 0 ? totalDistress / count : 0.0;
    }

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