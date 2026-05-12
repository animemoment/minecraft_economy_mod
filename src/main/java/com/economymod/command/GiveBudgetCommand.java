package com.economymod.command;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class GiveBudgetCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("givebudget")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> {
                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                            var player = ctx.getSource().getPlayerOrException();

                            var villagers = player.level().getEntitiesOfClass(
                                    Villager.class,
                                    AABB.ofSize(player.position(), 64, 64, 64),
                                    v -> true
                            );

                            int count = 0;
                            for (Villager v : villagers) {
                                var att = v.getData(ModAttachments.VILLAGER.get());
                                att.setBalance(att.getBalance() + amount);
                                count++;
                            }

                            final int finalCount = count;
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("Added " + amount + " budget to " + finalCount + " villagers"), true);
                            return finalCount;
                        })
                )
        );
    }
}