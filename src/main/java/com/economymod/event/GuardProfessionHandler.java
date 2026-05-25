package com.economymod.event;

// import com.economymod.EconomyMod;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

public class GuardProfessionHandler {

    // Обработчик клика по мишени (но теперь он вызывает автоматическую замену)
    @SubscribeEvent
    public static void onRightClickTarget(PlayerInteractEvent.RightClickBlock event) {
        // if (event.getLevel().isClientSide()) return;
        // if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.TARGET)) return;
        //
        // ServerLevel level = (ServerLevel) event.getLevel();
        // BlockPos pos = event.getPos();
        //
        // // Ищем безработного жителя рядом
        // AABB searchBox = new AABB(pos).inflate(10);
        // List<Villager> villagers = level.getEntitiesOfClass(Villager.class, searchBox,
        //         v -> v.getVillagerData().getProfession() == VillagerProfession.NONE);
        //
        // for (Villager villager : villagers) {
        //     replaceWithGuard(villager, level);
        // }
    }

    // Основной метод замены жителя на стража
    private static void replaceWithGuard(Villager oldVillager, ServerLevel level) {
        // if (oldVillager instanceof VillageGuardEntity) return;
        //
        // VillageGuardEntity guard = ModEntities.VILLAGE_GUARD.get().create(level);
        // if (guard == null) return;
        //
        // // Копируем позицию и поворот
        // guard.setPos(oldVillager.getX(), oldVillager.getY(), oldVillager.getZ());
        // guard.setYRot(oldVillager.getYRot());
        // guard.setXRot(oldVillager.getXRot());
        //
        // // Копируем имя
        // if (oldVillager.getCustomName() != null) {
        //     guard.setCustomName(oldVillager.getCustomName());
        //     guard.setCustomNameVisible(oldVillager.isCustomNameVisible());
        // }
        //
        // // Копируем данные аттачмента
        // var attachments = oldVillager.getData(ModAttachments.VILLAGER.get());
        // if (attachments != null) {
        //     var newAtt = guard.getData(ModAttachments.VILLAGER.get());
        //     if (newAtt != null) {
        //         for (int i = 0; i < attachments.getInventory().getContainerSize(); i++) {
        //             newAtt.getInventory().setItem(i, attachments.getInventory().getItem(i).copy());
        //         }
        //         newAtt.setBalance(attachments.getBalance());
        //         newAtt.setHunger(attachments.getHunger());
        //         newAtt.setPersonalChestPos(attachments.getPersonalChestPos());
        //         newAtt.setMiningStartY(attachments.getMiningStartY());
        //         newAtt.setLootGenerated(attachments.wasLootGenerated());
        //     }
        // }
        //
        // // Даём профессию стражу
        // guard.setVillagerData(new VillagerData(
        //         guard.getVillagerData().getType(),
        //         ModProfessions.GUARD.get(),
        //         guard.getVillagerData().getLevel()
        // ));
        //
        // // Удаляем старого и спавним нового
        // oldVillager.discard();
        // level.addFreshEntity(guard);
        //
        // EconomyMod.LOGGER.info("Житель {} стал стражем!",
        //         guard.getName().getString());
    }

    // При загрузке мира заменяем всех жителей с профессией GUARD на стражей
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // if (event.getLevel().isClientSide()) return;
        // if (!(event.getEntity() instanceof Villager villager)) return;
        // if (villager instanceof VillageGuardEntity) return;
        //
        // if (villager.getVillagerData().getProfession() == ModProfessions.GUARD.get()) {
        //     replaceWithGuard(villager, (ServerLevel) event.getLevel());
        // }
    }
}