package com.economymod.command;

import com.economymod.registry.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class InitializeLootCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("initloot")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> {
                            int radius = IntegerArgumentType.getInteger(ctx, "radius");
                            ServerPlayer player = ctx.getSource().getPlayerOrException();

                            var villagers = player.level().getEntitiesOfClass(
                                    Villager.class,
                                    new AABB(player.blockPosition()).inflate(radius),
                                    v -> true
                            );

                            int count = 0;
                            for (Villager v : villagers) {
                                var att = v.getData(ModAttachments.VILLAGER.get());
                                if (att != null) {
                                    att.forceLootGeneration();
                                    count++;
                                }
                            }

                            final int finalCount = count;
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§aСтартовый лут выдан " + finalCount + " жителям в радиусе " + radius), true);
                            return finalCount;
                        })
                )
        );
    }
}