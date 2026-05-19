package com.economymod.command;

import com.economymod.registry.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BalanceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("balance").executes(ctx -> {
            if (ctx.getSource().getEntity() instanceof ServerPlayer sp) {
                // ИСПРАВЛЕНО: double
                double bal = sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance();
                sp.sendSystemMessage(Component.literal(String.format("Balance: %.2f coins", bal)));
                return 1;
            }
            return 0;
        }));
    }
}