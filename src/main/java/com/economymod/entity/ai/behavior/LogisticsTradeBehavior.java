package com.economymod.entity.ai.behavior;

import com.economymod.economy.systems.VirtualTraderManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

public class LogisticsTradeBehavior extends Behavior<Villager> {
    public LogisticsTradeBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return owner.getInventory().countItem(Items.EMERALD) > 10;
    }

    @Override
    protected void start(ServerLevel level, Villager owner, long gameTime) {
        BlockPos currentPos = owner.blockPosition();
        BlockPos destination = currentPos.offset(500, 0, 500);
        VirtualTraderManager.startJourney(owner, destination);
    }
}