package com.economymod.creatures;

import net.minecraft.world.entity.npc.Villager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VillagerBrainWrapper {
    private final Villager villager;
    private final LearningSystem learningSystem;
    private final Genome genome;
    private static final Map<UUID, VillagerBrainWrapper> wrappers = new HashMap<>();

    public VillagerBrainWrapper(Villager villager) {
        this.villager = villager;
        this.learningSystem = new LearningSystem(villager);
        this.genome = new Genome();
    }

    public LearningSystem getLearningSystem() { return learningSystem; }
    public Genome getGenome() { return genome; }

    public void tick() {
        long dayTime = villager.level().getDayTime();
        learningSystem.updateCircadian(dayTime);
        learningSystem.tickForgetting();

        // Обновление свободы и новизны для жителя (можно добавить позже)
        if (villager.tickCount % 100 == 0) {
            // learningSystem.getFreedom(); // обновит кэш
        }
    }

    public static VillagerBrainWrapper getOrCreate(Villager villager) {
        return wrappers.computeIfAbsent(villager.getUUID(), u -> new VillagerBrainWrapper(villager));
    }

    public static void remove(UUID uuid) { wrappers.remove(uuid); }
}