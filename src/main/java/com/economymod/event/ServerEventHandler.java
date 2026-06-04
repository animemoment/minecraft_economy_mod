package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.creatures.VillagerBrainWrapper;
import com.economymod.creatures.LearningSystem;
import com.economymod.registry.ModAttachments;
import com.economymod.entity.VillageGuardEntity;
import com.economymod.entity.ai.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class ServerEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            var p = event.getEntity();
            var eco = p.getData(ModAttachments.PLAYER_ECONOMY.get());
            EconomyMod.LOGGER.info("Игрок {} вошел в игру. Баланс: {}",
                    p.getName().getString(), eco.getBalance());
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Villager villager) {
            // Удаляем ванильные цели торговли, чтобы они не конфликтовали с нашими
            villager.goalSelector.getAvailableGoals().removeIf(goal ->
                    goal.getGoal() instanceof TradeWithPlayerGoal || goal.getGoal() instanceof LookAtTradingPlayerGoal);

            villager.setCanPickUpLoot(true);

            // Регистрируем цели ИИ строго один раз с выверенными приоритетами
            villager.goalSelector.addGoal(1, new VillagerCollectItemsGoal(villager)); // Подбор предметов с земли
            villager.goalSelector.addGoal(2, new VillagerFarmingGoal(villager));      // Работа на ферме
            villager.goalSelector.addGoal(2, new VillagerDepositTrashGoal(villager)); // Выгрузка мусора в сундуки
            villager.goalSelector.addGoal(3, new VillagerMiningGoal(villager));       // Шахтерство
            villager.goalSelector.addGoal(3, new VillagerLumberjackGoal(villager));   // ИСПРАВЛЕНО: Зарегистрировали цель Лесозаготовки дровосека!
            villager.goalSelector.addGoal(3, new VillagerSmeltingGoal(villager));     // Плавка руды в печь
            villager.goalSelector.addGoal(4, new VillagerCraftingGoal(villager));     // Крафт инструментов
            villager.goalSelector.addGoal(4, new VillagerCompostingGoal(villager));   // Компостирование семян
            villager.goalSelector.addGoal(5, new VillagerP2PTradeGoal(villager));     // Торговля жителей друг с другом

            VillagerBrainWrapper.getOrCreate(villager);

            EconomyMod.LOGGER.info("ЭКОНОМИКА ИИ: Все экономические цели успешно прописаны в мозг жителя [{}]!",
                    villager.getDisplayName().getString());
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof Villager villager && villager.isAlive()) {
            var att = villager.getData(ModAttachments.VILLAGER.get());
            if (att != null) att.tick();

            VillagerBrainWrapper wrapper = VillagerBrainWrapper.getOrCreate(villager);
            wrapper.tick();
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity victim = event.getEntity();

        if (victim instanceof Villager villager) {
            // При получении урона жителем активируется гормональный выброс ИИ и запоминание страха!
            VillagerBrainWrapper brain = VillagerBrainWrapper.getOrCreate(villager);
            if (brain != null) {
                brain.modifyChemical("Adrenaline", 0.85f); // Резкий скачок адреналина
                brain.modifyChemical("Cortisol", 0.65f);   // Подъем гормона стресса/страха

                // Запоминаем текущую опасную обстановку (заносим контекст в долговременную память)
                String context = brain.getLearningSystem().getCurrentContext();
                brain.getLearningSystem().learnNegative(
                        LearningSystem.MemoryType.DAMAGE,
                        context,
                        0.85f
                );
            }

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