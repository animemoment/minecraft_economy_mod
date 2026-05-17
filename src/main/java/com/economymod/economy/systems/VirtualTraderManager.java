package com.economymod.economy.systems;

import com.economymod.entity.EconomyTraderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity; // Добавлен импорт
import java.util.*;

public class VirtualTraderManager {
    private static final Map<UUID, VirtualEntry> travelingTraders = new HashMap<>();
    public record VirtualEntry(CompoundTag data, BlockPos target, long arrivalTime) {}

    // Изменено: принимаем LivingEntity для универсальности, но проверяем на EconomyTraderEntity
    public static void startJourney(LivingEntity trader, BlockPos target) {
        if (trader instanceof EconomyTraderEntity et) {
            long travelTime = (long) (Math.sqrt(et.blockPosition().distSqr(target)) * 2);
            CompoundTag tag = new CompoundTag();
            et.saveWithoutId(tag);
            travelingTraders.put(et.getUUID(), new VirtualEntry(tag, target, et.level().getGameTime() + travelTime));
            et.discard();
        }
    }

    public static void tick(ServerLevel level) {
        Iterator<Map.Entry<UUID, VirtualEntry>> it = travelingTraders.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next().getValue();
            if (level.getGameTime() >= entry.arrivalTime && level.isLoaded(entry.target)) {
                // Убедись, что ID сущности в EntityType правильный
                EconomyTraderEntity trader = (EconomyTraderEntity) EntityType.byString("economymod:economy_trader").get().create(level);
                if (trader != null) {
                    trader.load(entry.data);
                    trader.setPos(entry.target.getX(), entry.target.getY(), entry.target.getZ());
                    level.addFreshEntity(trader);
                    it.remove();
                }
            }
        }
    }
}