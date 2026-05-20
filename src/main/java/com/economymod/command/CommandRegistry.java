package com.economymod.command;

import com.economymod.EconomyMod;
import com.economymod.economy.PriceCalculator;
import com.economymod.world.VillageNetworkData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("economy")
                .requires(source -> source.hasPermission(2))

                // 1. Команда инфо (с дебагом)
                .then(Commands.literal("info")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);

                            context.getSource().sendSuccess(() -> Component.literal("§6=== Экономика Мира ==="), false);
                            context.getSource().sendSuccess(() -> Component.literal("Деревень в памяти: §e" + data.getAllVillages().size()), false);
                            context.getSource().sendSuccess(() -> Component.literal("ID базы в ОЗУ: §b" + System.identityHashCode(data)), false);
                            return 1;
                        })
                )

                // 2. Команда перерасчета (с логами)
                .then(Commands.literal("recalc")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);

                            int size = data.getAllVillages().size();
                            EconomyMod.LOGGER.info("ЭКОНОМИКА ДЕБАГ: Запуск recalc. ID базы в ОЗУ: {}, Элементов: {}", System.identityHashCode(data), size);

                            int count = 0;
                            for (VillageNetworkData.VillageInfo info : data.getAllVillages()) {
                                info.recalcFactors(level, true);
                                info.updateDailyEconomy();
                                count++;
                            }
                            data.setDirty();

                            final int finalCount = count;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§aЭкономика принудительно пересчитана для §e" + finalCount + " §aдеревень! (База ID: " + System.identityHashCode(data) + ")"), true);
                            return 1;
                        })
                )

                // 3. Команда очистки (с выводом логов до/после)
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);

                            int beforeSize = data.getAllVillages().size();
                            int beforeHash = System.identityHashCode(data);

                            // Очищаем в ОЗУ
                            data.clearAllVillages();
                            data.setDirty();

                            // Силой пишем на диск
                            level.getDataStorage().save();

                            int afterSize = data.getAllVillages().size();

                            EconomyMod.LOGGER.info("ЭКОНОМИКА ДЕБАГ: Запущена очистка базы! ID базы: {}. Было деревень: {}, стало: {}", beforeHash, beforeSize, afterSize);

                            context.getSource().sendSuccess(() -> Component.literal(
                                    String.format("§aБаза очищена! Было деревень: §e%d§a, стало: §e%d§a. (База ID: %d)", beforeSize, afterSize, beforeHash)), true);
                            return 1;
                        })
                )

                // 4. Новая дебаг-команда: Выводит координаты первых 10 деревень в чат (ИСПРАВЛЕНО!)
                .then(Commands.literal("list")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);

                            context.getSource().sendSuccess(() -> Component.literal("§d=== Список первых 10 деревень в базе ==="), false);
                            int limit = 0;
                            for (BlockPos pos : data.getAllVillagePositions()) {
                                if (limit >= 10) break;

                                // ИСПРАВЛЕНО: Форматируем строку заранее и сохраняем в final константу, чтобы обойти ограничение лямбды
                                final int currentNum = limit + 1;
                                final String message = String.format("Деревня %d: §e[%d, %d, %d]", currentNum, pos.getX(), pos.getY(), pos.getZ());

                                context.getSource().sendSuccess(() -> Component.literal(message), false);
                                limit++;
                            }
                            return 1;
                        })
                )

                // 5. Команда цен
                .then(Commands.literal("prices")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                            VillageNetworkData data = VillageNetworkData.get(level);

                            BlockPos nearestVillage = null;
                            double minDist = 10000;
                            for (BlockPos vPos : data.getAllVillagePositions()) {
                                double dist = vPos.distSqr(pos);
                                if (dist < minDist) {
                                    minDist = dist;
                                    nearestVillage = vPos;
                                }
                            }

                            if (nearestVillage == null) {
                                context.getSource().sendFailure(Component.literal("Вы не находитесь рядом с известной деревней."));
                                return 0;
                            }

                            VillageNetworkData.VillageInfo info = data.getVillageInfo(nearestVillage);
                            final BlockPos finalPos = nearestVillage;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    String.format("§6=== Цены в деревне [%d, %d, %d] ===", finalPos.getX(), finalPos.getY(), finalPos.getZ())), false);

                            for (Item item : info.getActiveItems()) {
                                double buyPrice = PriceCalculator.getBuyPrice(item.getDefaultInstance(), info);
                                context.getSource().sendSuccess(() -> Component.literal(
                                        String.format("%s: §a%.2f⛀", item.getDescription().getString(), buyPrice)), false);
                            }

                            return 1;
                        })
                )
        );
    }
}