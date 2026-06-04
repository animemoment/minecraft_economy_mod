package com.economymod.command;

import com.economymod.registry.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CoinsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("coins")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                            target.getData(ModAttachments.PLAYER_ECONOMY.get()).add(amount);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Gave " + String.format("%.2f", amount) + " coins to " +
                                                            target.getName().getString()), true);
                                            return 1;
                                        })))));
    }
}