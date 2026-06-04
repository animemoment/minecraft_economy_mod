package com.economymod.zones.commands;

import com.economymod.zones.ZoneInstance;
import com.economymod.zones.ZoneRegistry;
import com.economymod.zones.detectors.ZoneDetectionManager;
import com.economymod.zones.detectors.ZoneDetector;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;

public class ZoneCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zones")
                .requires(source -> source.hasPermission(2)) // Требуется уровень оператора (OP)

                // 1. Подкоманда info: выводит информацию о количестве зарегистрированных зон
                .then(Commands.literal("info")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            ZoneRegistry registry = ZoneRegistry.get(level);
                            int size = registry.getAllZones().size();

                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§6=== Реестр Зон ===\n" +
                                            "Зарегистрировано зон в этом измерении: §e" + size
                            ), false);
                            return size;
                        })
                )

                // 2. Подкоманда scan [radius]: запускает принудительное сканирование чанков вокруг игрока
                .then(Commands.literal("scan")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    int radius = IntegerArgumentType.getInteger(context, "radius");

                                    ChunkPos centerChunk = new ChunkPos(player.blockPosition());
                                    int scannedChunks = 0;

                                    for (int dx = -radius; dx <= radius; dx++) {
                                        for (int dz = -radius; dz <= radius; dz++) {
                                            ChunkPos targetChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                                            if (level.hasChunk(targetChunk.x, targetChunk.z)) {
                                                for (ZoneDetector detector : ZoneDetectionManager.getDetectors()) {
                                                    detector.scanChunk(level, targetChunk);
                                                }
                                                scannedChunks++;
                                            }
                                        }
                                    }

                                    final int finalScanned = scannedChunks;
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "§aПринудительное сканирование завершено! Просканировано чанков: §e" + finalScanned
                                    ), true);
                                    return finalScanned;
                                })
                        )
                )

                // 3. Подкоманда clear: удаляет ВСЕ зоны в текущем измерении
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            ZoneRegistry registry = ZoneRegistry.get(level);
                            int size = registry.getAllZones().size();

                            // Создаем копию списка ID зон для безопасного удаления из мапы
                            java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
                            for (ZoneInstance zone : registry.getAllZones()) {
                                ids.add(zone.getId());
                            }

                            for (java.util.UUID id : ids) {
                                registry.deregister(id);
                            }

                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§cВсе зоны в этом измерении успешно удалены! Количество очищенных зон: §e" + size
                            ), true);
                            return size;
                        })
                )

                // 4. Подкоманда debug: включает/выключает подсветку зон для игрока
                .then(Commands.literal("debug")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();
                            ZoneRegistry registry = ZoneRegistry.get(level);

                            registry.toggleDebugPlayer(player.getUUID(), player);
                            boolean isDebugging = registry.getDebugPlayers().contains(player.getUUID());

                            context.getSource().sendSuccess(() -> Component.literal(
                                    isDebugging ? "§aРежим отладки зон ВКЛЮЧЕН! Блоки подсвечены."
                                            : "§cРежим отладки зон ВЫКЛЮЧЕН! Подсветка убрана."
                            ), false);
                            return 1;
                        })
                )
        );
    }
}