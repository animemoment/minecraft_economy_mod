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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent; // ИСПРАВЛЕНО: Новый импорт события входящего урона
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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

    // ИСПРАВЛЕНО: Используем LivingIncomingDamageEvent вместо LivingDamageEvent
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity victim = event.getEntity();

        // Если пострадал житель или другой стражник-соратник
        if (victim instanceof Villager || victim instanceof VillageGuardEntity) {
            // Если урон нанес живой противник (игрок или монстр)
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {

                // Ищем всех стражников в радиусе 32 блоков вокруг пострадавшего
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