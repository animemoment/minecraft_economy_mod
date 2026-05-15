package com.economymod.command;

import com.economymod.economy.VillageEconomy;
import com.economymod.economy.data.VillageEconomyData;
import com.economymod.economy.data.WorldEconomySavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("economy")
                .requires(source -> source.hasPermission(2)) // Только для операторов

                .then(Commands.literal("prices")
                        .then(Commands.literal("prices")
                                .executes(context -> {
                                    ServerLevel level = context.getSource().getLevel();
                                    VillageEconomyData data = VillageEconomyData.get(level);
                                    Map<BlockPos, VillageEconomy> villages = data.getVillages();

                                    if (villages.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal("База деревень пуста. Сделки еще не регистрировались в VillageEconomyData!"));
                                        return 0;
                                    }

                                    BlockPos playerPos = BlockPos.containing(context.getSource().getPosition());

                                    context.getSource().sendSuccess(() -> Component.literal("§6Список активных рынков:"), false);
                                    for (BlockPos vPos : villages.keySet()) {
                                        double dist = Math.sqrt(vPos.distSqr(playerPos));
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                String.format("§e- Деревня [%d, %d, %d] (Дистанция: %.1f)",
                                                        vPos.getX(), vPos.getY(), vPos.getZ(), dist)), false);
                                    }
                                    return 1;
                                })
                        )
                )

                // 3. Команда /economy force_update - принудительный пересчет цен
                .then(Commands.literal("force_update")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageEconomyData data = VillageEconomyData.get(level);

                            data.getVillages().values().forEach(VillageEconomy::updatePrices);
                            WorldEconomySavedData.get(level).updateGlobalPrices(data.getVillages());

                            context.getSource().sendSuccess(() -> Component.literal("§aЦены во всех деревнях обновлены на основе спроса/предложения!"), false);
                            return 1;
                        })
                )
        );
    }
}