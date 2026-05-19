package com.economymod.command;

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
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("economy")
                .requires(source -> source.hasPermission(2))

                .then(Commands.literal("info")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);
                            context.getSource().sendSuccess(() -> Component.literal("§6=== Экономика Мира ==="), false);
                            context.getSource().sendSuccess(() -> Component.literal("Деревень в базе: §e" + data.getAllVillages().size()), false);
                            return 1;
                        })
                )

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
                                // ИСПРАВЛЕНО: double
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