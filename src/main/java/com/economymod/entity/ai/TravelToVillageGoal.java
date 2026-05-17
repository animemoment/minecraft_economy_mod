package com.economymod.entity.ai;

import com.economymod.economy.systems.VirtualTraderManager;
import com.economymod.entity.EconomyTraderEntity;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TravelToVillageGoal extends Goal {
    private final EconomyTraderEntity trader;
    private long lastCheckTime;

    public TravelToVillageGoal(EconomyTraderEntity trader) {
        this.trader = trader;
    }

    @Override
    public boolean canUse() {
        if (!(trader.level() instanceof ServerLevel level)) return false;

        // Проверяем желание уйти раз в 10 секунд (200 тиков)
        if (level.getGameTime() - lastCheckTime < 200) return false;
        lastCheckTime = level.getGameTime();

        // Торговец решает уйти, если у него много денег (закупился/продался)
        // ИЛИ просто с шансом 1 к 20 (чтобы не стоял вечно)
        return trader.budget > 2000 || trader.getRandom().nextInt(20) == 0;
    }

    @Override
    public void start() {
        if (!(trader.level() instanceof ServerLevel level)) return;

        VillageNetworkData data = VillageNetworkData.get(level);
        Set<BlockPos> villages = data.getAllVillagePositions();

        // Если в мире известна только 1 деревня (текущая), идти некуда
        if (villages.size() <= 1) return;

        List<BlockPos> targets = new ArrayList<>(villages);
        targets.remove(trader.getCurrentBazaar()); // Удаляем текущую деревню из списка целей

        if (!targets.isEmpty()) {
            // Выбираем случайную другую деревню
            BlockPos destination = targets.get(trader.getRandom().nextInt(targets.size()));

            com.economymod.EconomyMod.LOGGER.info("Торговец {} отправляется в деревню {}", trader.getId(), destination);

            // Запускаем виртуальное путешествие (сущность исчезнет и появится там)
            VirtualTraderManager.startJourney(trader, destination);
        }
    }
}