package com.economymod.entity.ai;

import com.economymod.entity.EconomyTraderEntity;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import java.util.*;

public class TravelToVillageGoal extends Goal {
    private final EconomyTraderEntity trader;
    private BlockPos targetBazaar;
    private int cooldown = 0;

    public TravelToVillageGoal(EconomyTraderEntity trader) {
        this.trader = trader;
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!(trader.level() instanceof ServerLevel serverLevel)) return false;
        VillageNetworkData data = VillageNetworkData.get(serverLevel);
        BlockPos currentBazaar = trader.getCurrentBazaar();
        Set<BlockPos> villages = data.getAllVillagePositions();
        villages.remove(currentBazaar);
        if (villages.isEmpty()) return false;
        // Выбираем случайную деревню
        targetBazaar = villages.stream().skip(new Random().nextInt(villages.size())).findFirst().orElse(null);
        return targetBazaar != null;
    }

    @Override
    public void start() {
        if (targetBazaar != null) {
            PathNavigation nav = trader.getNavigation();
            nav.moveTo(targetBazaar.getX() + 0.5, targetBazaar.getY(), targetBazaar.getZ() + 0.5, 0.6);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetBazaar != null && !trader.getNavigation().isDone();
    }

    @Override
    public void stop() {
        if (targetBazaar != null && trader.blockPosition().distSqr(targetBazaar) < 4) {
            trader.arriveAtVillage(targetBazaar);
            cooldown = 600; // 30 секунд перед следующим путешествием
        }
        targetBazaar = null;
    }
}