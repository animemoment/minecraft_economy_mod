package com.economymod.neural;

import com.economymod.EconomyMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class NeuralManager {
    private static final Map<UUID, NeuralNetwork> networks = new HashMap<>();
    private static boolean isActive = false;

    // Универсальный метод получения/создания сети для любого жителя
    public static NeuralNetwork getOrCreate(LivingEntity entity) {
        UUID id = entity.getUUID();
        if (!networks.containsKey(id)) {
            networks.put(id, new NeuralNetwork(entity));
            EconomyMod.LOGGER.debug("NeuralNetwork created for {}", entity.getName().getString());
        }
        return networks.get(id);
    }

    public static NeuralNetwork get(LivingEntity entity) {
        return networks.get(entity.getUUID());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        isActive = true;
        EconomyMod.LOGGER.info("Neural system activated");
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!NeuralConfig.ENABLED) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        AABB worldArea = new AABB(level.getWorldBorder().getMinX(), -64, level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), 320, level.getWorldBorder().getMaxZ());

        // Обрабатываем нейросети жителей
        for (Villager villager : level.getEntitiesOfClass(Villager.class, worldArea, Entity::isAlive)) {
            NeuralNetwork net = getOrCreate(villager);
            if (net != null) net.tick();
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!NeuralConfig.ENABLED) return;
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof Villager villager) {
            getOrCreate(villager);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        networks.clear();
        isActive = false;
    }

    public static void toggle() {
        NeuralConfig.ENABLED = !NeuralConfig.ENABLED;
        EconomyMod.LOGGER.info("Neural system " + (NeuralConfig.ENABLED ? "enabled" : "disabled"));
    }

    // Статус нейросети для вывода в консоль / чат
    public static String getStatus(LivingEntity entity) {
        NeuralNetwork net = networks.get(entity.getUUID());
        if (net == null) return "No network";
        return String.format("Eat:%.2f Sleep:%.2f Trade:%.2f Run:%.2f Explore:%.2f Socialize:%.2f",
                net.getOutputActivation(NeuralNetwork.OUTPUT_EAT),
                net.getOutputActivation(NeuralNetwork.OUTPUT_SLEEP),
                net.getOutputActivation(NeuralNetwork.OUTPUT_TRADE),
                net.getOutputActivation(NeuralNetwork.OUTPUT_RUN_AWAY),
                net.getOutputActivation(NeuralNetwork.OUTPUT_EXPLORE),
                net.getOutputActivation(NeuralNetwork.OUTPUT_SOCIALIZE)
        );
    }
}