package com.economymod.command;

import com.economymod.EconomyMod;
import com.economymod.economy.PriceCalculator;
import com.economymod.world.VillageNetworkData;
import com.economymod.neural.NeuralCommand;
import com.economymod.entity.TestCreatureEntity;
import com.economymod.creatures.LearningSystem;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class CommandRegistry {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        NeuralCommand.register(event.getDispatcher());

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("emotion")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        net.minecraft.world.level.Level level = player.level();
                        AABB box = player.getBoundingBox().inflate(10);
                        for (Entity e : level.getEntitiesOfClass(TestCreatureEntity.class, box, entity -> true)) {
                            TestCreatureEntity creature = (TestCreatureEntity) e;
                            LearningSystem ls = creature.getLearningSystem();
                            float fear = ls.getOverallFear(null);
                            float curiosity = creature.getSensorValue("Curiosity");
                            float aggression = creature.getCreatureBrain().getLobe(1).getRegister(6, 0, 0);
                            float boredom = 1f - ls.getNovelty();
                            player.sendSystemMessage(Component.literal(
                                    String.format("%s: ❤️%.2f 🧠%.2f ⚔️%.2f 😴%.2f",
                                            creature.getName().getString(), fear, curiosity, aggression, boredom)));
                        }
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("fear")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        net.minecraft.world.level.Level level = player.level();
                        AABB box = player.getBoundingBox().inflate(10);
                        for (Entity e : level.getEntitiesOfClass(TestCreatureEntity.class, box, entity -> true)) {
                            TestCreatureEntity creature = (TestCreatureEntity) e;
                            LearningSystem ls = creature.getLearningSystem();
                            String context = ls.getCurrentContext();
                            float overallFear = ls.getOverallFear(null);
                            float desireExplore = creature.getCreatureBrain().getLobe(1).getRegister(3, 0, 0);

                            float health = creature.getHealth() / creature.getMaxHealth();
                            int hunger = (int)(creature.getSensorValue("Hunger") * 100);
                            int fatigue = (int)(creature.getSensorValue("Fatigue") * 100);
                            LearningSystem.MemoryType primaryFear;
                            if (health < 0.6f) primaryFear = LearningSystem.MemoryType.DAMAGE;
                            else if (hunger > 70) primaryFear = LearningSystem.MemoryType.HUNGER;
                            else if (fatigue > 70) primaryFear = LearningSystem.MemoryType.FATIGUE;
                            else primaryFear = LearningSystem.MemoryType.BOREDOM;

                            float modifiedExplore = ls.modifyDesire("Explore", desireExplore, null, primaryFear);
                            player.sendSystemMessage(Component.literal(
                                    String.format("%s: context=%s, overallFear=%.2f, primaryFear=%s, baseExplore=%.2f, modifiedExplore=%.2f",
                                            creature.getName().getString(), context, overallFear, primaryFear.name(), desireExplore, modifiedExplore)));
                        }
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("memory")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        net.minecraft.world.level.Level level = player.level();
                        AABB box = player.getBoundingBox().inflate(10);
                        for (Entity e : level.getEntitiesOfClass(TestCreatureEntity.class, box, entity -> true)) {
                            TestCreatureEntity creature = (TestCreatureEntity) e;
                            LearningSystem ls = creature.getLearningSystem();
                            player.sendSystemMessage(Component.literal("§6=== Memory of " + creature.getName().getString() + " ==="));

                            for (LearningSystem.MemoryType type : LearningSystem.MemoryType.values()) {
                                var mem = ls.getAllMemories().get(type);
                                if (!mem.isEmpty()) {
                                    player.sendSystemMessage(Component.literal("§e" + type.name() + ":"));
                                    int count = 0;
                                    for (var entry : mem.entrySet()) {
                                        if (count++ >= 5) {
                                            player.sendSystemMessage(Component.literal("  ... and " + (mem.size() - 5) + " more"));
                                            break;
                                        }
                                        player.sendSystemMessage(Component.literal("  " + entry.getKey() + " → " + String.format("%.2f", entry.getValue())));
                                    }
                                }
                            }
                            var itemMem = ls.getAllItemFears();
                            if (!itemMem.isEmpty()) {
                                player.sendSystemMessage(Component.literal("§eItem fears:"));
                                int count = 0;
                                for (var entry : itemMem.entrySet()) {
                                    if (count++ >= 5) {
                                        player.sendSystemMessage(Component.literal("  ... and " + (itemMem.size() - 5) + " more"));
                                        break;
                                    }
                                    String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()).getPath();
                                    player.sendSystemMessage(Component.literal("  " + itemName + " → " + String.format("%.2f", entry.getValue())));
                                }
                            }
                            var positiveMem = ls.getAllPositive();
                            if (!positiveMem.isEmpty()) {
                                player.sendSystemMessage(Component.literal("§aPositive memory:"));
                                int count = 0;
                                for (var entry : positiveMem.entrySet()) {
                                    if (count++ >= 5) {
                                        player.sendSystemMessage(Component.literal("  ... and " + (positiveMem.size() - 5) + " more"));
                                        break;
                                    }
                                    player.sendSystemMessage(Component.literal("  " + entry.getKey() + " → +" + String.format("%.2f", entry.getValue())));
                                }
                            }
                        }
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("stats")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        net.minecraft.world.level.Level level = player.level();
                        AABB box = player.getBoundingBox().inflate(10);
                        for (Entity e : level.getEntitiesOfClass(TestCreatureEntity.class, box, entity -> true)) {
                            TestCreatureEntity creature = (TestCreatureEntity) e;
                            float health = creature.getHealth() / creature.getMaxHealth();
                            float hunger = creature.getSensorValue("Hunger");
                            float fatigue = creature.getSensorValue("Fatigue");
                            float freedom = creature.getLearningSystem().getFreedom();
                            float novelty = creature.getLearningSystem().getNovelty();
                            float distress = creature.getLearningSystem().calculateDistress(health, hunger, fatigue);
                            player.sendSystemMessage(Component.literal(
                                    String.format("%s: Health:%.2f Hunger:%.2f Fatigue:%.2f Freedom:%.2f Novelty:%.2f Distress:%.2f",
                                            creature.getName().getString(), health, hunger, fatigue, freedom, novelty, distress)));
                        }
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("emotion")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                        net.minecraft.world.level.Level level = player.level();
                        AABB box = player.getBoundingBox().inflate(10);
                        for (Entity e : level.getEntitiesOfClass(TestCreatureEntity.class, box, entity -> true)) {
                            TestCreatureEntity creature = (TestCreatureEntity) e;
                            LearningSystem ls = creature.getLearningSystem();
                            float fear = ls.getOverallFear(null);
                            float curiosity = creature.getSensorValue("Curiosity");
                            float aggression = creature.getCreatureBrain().getLobe(1).getRegister(6, 0, 0);
                            float boredom = 1f - ls.getNovelty();
                            player.sendSystemMessage(Component.literal(
                                    String.format("%s: Fear=%.2f Curiosity=%.2f Aggression=%.2f Boredom=%.2f",
                                            creature.getName().getString(), fear, curiosity, aggression, boredom)));
                        }
                    }
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("economy")
                .requires(source -> source.hasPermission(2))
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
                .then(Commands.literal("recalc")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);
                            int size = data.getAllVillages().size();
                            EconomyMod.LOGGER.info("ЭКОНОМИКА ДЕБАГ: Запуск recalc. ID базы: {}, Элементов: {}", System.identityHashCode(data), size);
                            int count = 0;
                            for (VillageNetworkData.VillageInfo info : data.getAllVillages()) {
                                info.recalcFactors(level, true);
                                info.updateDailyEconomy();
                                count++;
                            }
                            data.setDirty();
                            final int finalCount = count;
                            final int hash = System.identityHashCode(data);
                            context.getSource().sendSuccess(() -> Component.literal("§aЭкономика пересчитана для §e" + finalCount + " §aдеревень! (База ID: " + hash + ")"), true);
                            return 1;
                        })
                )
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);
                            int beforeSize = data.getAllVillages().size();
                            int beforeHash = System.identityHashCode(data);
                            data.clearAllVillages();
                            data.setDirty();
                            level.getDataStorage().save();
                            int afterSize = data.getAllVillages().size();
                            EconomyMod.LOGGER.info("ЭКОНОМИКА ДЕБАГ: Очистка базы! ID: {}. Было: {}, стало: {}", beforeHash, beforeSize, afterSize);
                            final int before = beforeSize, after = afterSize, hash = beforeHash;
                            context.getSource().sendSuccess(() -> Component.literal(String.format("§aБаза очищена! Было: §e%d§a, стало: §e%d§a. (ID: %d)", before, after, hash)), true);
                            return 1;
                        })
                )
                .then(Commands.literal("list")
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            VillageNetworkData data = VillageNetworkData.get(level);
                            context.getSource().sendSuccess(() -> Component.literal("§d=== Список первых 10 деревень ==="), false);
                            int[] limit = {0};
                            for (BlockPos pos : data.getAllVillagePositions()) {
                                if (limit[0] >= 10) break;
                                limit[0]++;
                                final int num = limit[0];
                                final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
                                context.getSource().sendSuccess(() -> Component.literal(String.format("Деревня %d: §e[%d, %d, %d]", num, x, y, z)), false);
                            }
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
                                if (dist < minDist) { minDist = dist; nearestVillage = vPos; }
                            }
                            if (nearestVillage == null) {
                                context.getSource().sendFailure(Component.literal("Нет известной деревни рядом."));
                                return 0;
                            }
                            final int fx = nearestVillage.getX(), fy = nearestVillage.getY(), fz = nearestVillage.getZ();
                            context.getSource().sendSuccess(() -> Component.literal(String.format("§6=== Цены в деревне [%d, %d, %d] ===", fx, fy, fz)), false);

                            VillageNetworkData.VillageInfo info = data.getVillageInfo(nearestVillage);
                            for (Item item : info.getActiveItems()) {
                                final Item finalItem = item;
                                double buyPrice = PriceCalculator.getBuyPrice(item.getDefaultInstance(), info);
                                context.getSource().sendSuccess(() -> Component.literal(String.format("%s: §a%.2f⛀", finalItem.getDescription().getString(), buyPrice)), false);
                            }
                            return 1;
                        })
                )
        );
    }
}