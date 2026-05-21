package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.registry.ModAttachments;
import com.economymod.entity.VillageGuardEntity;
import com.economymod.entity.ai.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent; // ИСПРАВЛЕНО: Новый импорт события тиков сущностей в 1.21.1

import java.util.List;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            var p = event.getEntity();
            var eco = p.getData(ModAttachments.PLAYER_ECONOMY.get());
            EconomyMod.LOGGER.info("Player {} joined. Balance: {}",
                    p.getName().getString(), eco.getBalance());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Villager villager) {
            villager.goalSelector.addGoal(1, new VillagerDepositTrashGoal(villager));
            villager.goalSelector.addGoal(2, new VillagerMiningGoal(villager));
            villager.goalSelector.addGoal(2, new VillagerSmeltingGoal(villager));
            villager.goalSelector.addGoal(3, new VillagerCraftingGoal(villager));
            villager.goalSelector.addGoal(3, new VillagerCompostingGoal(villager));
            villager.goalSelector.addGoal(4, new VillagerP2PTradeGoal(villager));

            EconomyMod.LOGGER.info("ЭКОНОМИКА ИИ: Все экономические цели успешно прописаны в мозг жителя [{}]!",
                    villager.getDisplayName().getString());
        }
    }

    // ИСПРАВЛЕНО: Подписываемся на конкретный сабкласс .Pre, чтобы избежать краша абстрактного класса
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof Villager villager) {
            var att = villager.getData(ModAttachments.VILLAGER.get());
            if (att != null) {
                att.tick(); // Запускаем цикл голода и питания жителя
            }
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity victim = event.getEntity();
        if (victim instanceof Villager || victim instanceof VillageGuardEntity) {
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
                List<VillageGuardEntity> guards = victim.level().getEntitiesOfClass(
                        VillageGuardEntity.class,
                        victim.getBoundingBox().inflate(32.0D)
                );

                int alertedCount = 0;
                for (VillageGuardEntity guard : guards) {
                    if (!(attacker instanceof Villager)) {
                        guard.setTarget(attacker);
                        alertedCount++;
                    }
                }

                if (alertedCount > 0) {
                    EconomyMod.LOGGER.info("ЭКОНОМИКА ТРЕВОГА: Нанесен входящий урон {}. Поднято по тревоге стражников: {}",
                            victim.getName().getString(), alertedCount);
                }
            }
        }
    }
}