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
                long bal = sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance();
                sp.sendSystemMessage(Component.literal("Balance: " + bal + " coins"));
                return 1;
            }
            return 0;
        }));
    }
}