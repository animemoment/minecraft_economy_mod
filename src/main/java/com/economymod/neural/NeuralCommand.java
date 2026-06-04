package com.economymod.neural;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class NeuralCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("neural")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            NeuralManager.toggle();
                            ctx.getSource().sendSuccess(() -> Component.literal("Neural system " + (NeuralConfig.ENABLED ? "enabled" : "disabled")), true);
                            return 1;
                        })
                )
                .then(Commands.literal("status")
                        .then(Commands.argument("radius", FloatArgumentType.floatArg(1, 32))
                                .executes(ctx -> {
                                    float radius = FloatArgumentType.getFloat(ctx, "radius");
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    AABB box = new AABB(player.blockPosition()).inflate(radius);
                                    int found = 0;

                                    // Поиск жителей в радиусе и вывод статуса их обучения
                                    for (Villager v : player.level().getEntitiesOfClass(Villager.class, box, LivingEntity::isAlive)) {
                                        String status = NeuralManager.getStatus(v);
                                        player.sendSystemMessage(Component.literal("§a" + v.getName().getString() + "§r: " + status));
                                        found++;
                                    }

                                    if (found == 0) {
                                        player.sendSystemMessage(Component.literal("No villagers with neural network found within " + radius + " blocks"));
                                    }
                                    return found;
                                })
                        )
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            AABB box = player.getBoundingBox().inflate(10);

                            Villager nearestVillager = null;
                            double minDist = 1000;
                            for (Villager v : player.level().getEntitiesOfClass(Villager.class, box, LivingEntity::isAlive)) {
                                double d = player.distanceToSqr(v);
                                if (d < minDist) {
                                    minDist = d;
                                    nearestVillager = v;
                                }
                            }
                            if (nearestVillager != null) {
                                String status = NeuralManager.getStatus(nearestVillager);
                                player.sendSystemMessage(Component.literal(nearestVillager.getName().getString() + ": " + status));
                                return 1;
                            }

                            player.sendSystemMessage(Component.literal("No entity with neural network nearby"));
                            return 0;
                        })
                )
        );
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }
}