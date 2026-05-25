package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.entity.VillageGuardEntity;
import com.economymod.registry.ModAttachments;
import com.economymod.registry.ModEntities;
import com.economymod.registry.ModProfessions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

public class GuardAutoAssigner {

    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 200;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Перебираем все блоки в загруженной области жителей (игроки + спавн)
        // Простой и надёжный способ: проверить вокруг каждого игрока
        if (level.players().isEmpty()) return;

        for (var player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            // Проверяем область 50x50 вокруг каждого игрока
            for (int dx = -25; dx <= 25; dx++) {
                for (int dz = -25; dz <= 25; dz++) {
                    for (int dy = -10; dy <= 10; dy++) {
                        BlockPos pos = playerPos.offset(dx, dy, dz);
                        if (!level.isLoaded(pos)) continue;
                        if (level.getBlockState(pos).is(Blocks.TARGET)) {
                            // Нашли мишень
                            AABB searchBox = new AABB(pos).inflate(10);
                            List<Villager> villagers = level.getEntitiesOfClass(Villager.class, searchBox,
                                    v -> v.getVillagerData().getProfession() == VillagerProfession.NONE);

                            for (Villager villager : villagers) {
                                replaceWithGuard(villager, level, pos);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void replaceWithGuard(Villager oldVillager, ServerLevel level, BlockPos targetPos) {
        if (oldVillager instanceof VillageGuardEntity) return;

        VillageGuardEntity guard = ModEntities.VILLAGE_GUARD.get().create(level);
        if (guard == null) return;

        // Копируем позицию и поворот
        guard.setPos(oldVillager.getX(), oldVillager.getY(), oldVillager.getZ());
        guard.setYRot(oldVillager.getYRot());
        guard.setXRot(oldVillager.getXRot());

        // Копируем имя
        if (oldVillager.getCustomName() != null) {
            guard.setCustomName(oldVillager.getCustomName());
            guard.setCustomNameVisible(oldVillager.isCustomNameVisible());
        }

        // Копируем данные аттачмента
        var attachments = oldVillager.getData(ModAttachments.VILLAGER.get());
        if (attachments != null) {
            var newAtt = guard.getData(ModAttachments.VILLAGER.get());
            if (newAtt != null) {
                for (int i = 0; i < attachments.getInventory().getContainerSize(); i++) {
                    newAtt.getInventory().setItem(i, attachments.getInventory().getItem(i).copy());
                }
                newAtt.setBalance(attachments.getBalance());
                newAtt.setHunger(attachments.getHunger());
                newAtt.setPersonalChestPos(attachments.getPersonalChestPos());
                newAtt.setMiningStartY(attachments.getMiningStartY());
                newAtt.setLootGenerated(attachments.wasLootGenerated());
            }
        }

        // Назначаем профессию стражу
        guard.setVillagerData(new VillagerData(
                guard.getVillagerData().getType(),
                ModProfessions.GUARD.get(),
                guard.getVillagerData().getLevel()
        ));

        // Удаляем старого, спавним нового
        oldVillager.discard();
        level.addFreshEntity(guard);

        EconomyMod.LOGGER.info("Житель {} автоматически стал стражем у мишени {}",
                guard.getName().getString(), targetPos.toShortString());
    }
}