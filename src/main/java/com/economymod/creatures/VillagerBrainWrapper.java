package com.economymod.creatures;

import com.economymod.creatures.biochemistry.*;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VillagerBrainWrapper implements SensorProvider {
    private final Villager villager;
    private final LearningSystem learningSystem;
    private final Genome genome;
    private CreatureBrain brain;             // Убрали final, чтобы инициализировать в методах
    private BiochemistrySystem biochemistry; // Убрали final, чтобы инициализировать в методах

    private float fatigue = 0.0f; // Усталость жителя (0.0f - 100.0f)
    private float cachedHostileNearby = 0f;
    private float cachedSocialNearby = 0f;
    private float cachedPlayerNearby = 0f;

    private static final Map<UUID, VillagerBrainWrapper> wrappers = new HashMap<>();

    public VillagerBrainWrapper(Villager villager) {
        this.villager = villager;
        this.learningSystem = new LearningSystem(villager);
        this.genome = new Genome();

        // Полноценная инициализация биохимии и мозга
        buildBiochemistry();
        buildBrain();
    }

    private void buildBiochemistry() {
        this.biochemistry = new BiochemistrySystem();

        Chemical glucose = new Chemical("Glucose", 0.6f, 300);
        Chemical atp = new Chemical("ATP", 0.8f, 150);
        Chemical hungerChem = new Chemical("Hunger", 0.0f, 300);
        Chemical fatigueChem = new Chemical("Fatigue", 0.0f, 300);
        Chemical adrenaline = new Chemical("Adrenaline", 0.0f, 45);
        Chemical dopamine = new Chemical("Dopamine", 0.0f, 80);
        Chemical serotonin = new Chemical("Serotonin", 0.0f, 100);
        Chemical cortisol = new Chemical("Cortisol", 0.0f, 150);
        Chemical oxytocin = new Chemical("Oxytocin", 0.0f, 200);

        biochemistry.addChemical(glucose);
        biochemistry.addChemical(atp);
        biochemistry.addChemical(hungerChem);
        biochemistry.addChemical(fatigueChem);
        biochemistry.addChemical(adrenaline);
        biochemistry.addChemical(dopamine);
        biochemistry.addChemical(serotonin);
        biochemistry.addChemical(cortisol);
        biochemistry.addChemical(oxytocin);

        // Эмиттеры гормонов
        biochemistry.addEmitter(new Emitter(hungerChem, 0.02f, x -> x, () -> getSensorValue("Hunger")));
        biochemistry.addEmitter(new Emitter(fatigueChem, 0.02f, x -> x, () -> getSensorValue("Fatigue")));
        biochemistry.addEmitter(new Emitter(adrenaline, 0.4f, x -> x, () -> getSensorValue("HostileNearby")));
        biochemistry.addEmitter(new Emitter(oxytocin, 0.3f, x -> x, () -> getSensorValue("SocialNearby")));

        // Реакции метаболизма и стресса
        biochemistry.addReaction(new Reaction(glucose, null, atp, 0.01f));
        biochemistry.addReaction(new Reaction(dopamine, null, serotonin, 0.005f));
        biochemistry.addReaction(new Reaction(adrenaline, null, cortisol, 0.01f));
    }

    private void buildBrain() {
        this.brain = new CreatureBrain();
        SVRule emptyRule = new SVRule();
        BrainLobe sensorLobe = new BrainLobe(11, 1, 1, emptyRule);
        BrainLobe desireLobe = new BrainLobe(9, 1, 1, emptyRule);
        brain.addLobe(sensorLobe);
        brain.addLobe(desireLobe);

        // Интеграция трактов нейросети
        addTract(sensorLobe, desireLobe, 0,0,0,0, 0,0,0,0, DendriteSVRule.identity()); // Hunger -> Eat
        addTract(sensorLobe, desireLobe, 1,0,1,0, 1,0,1,0, DendriteSVRule.identity()); // Fatigue -> Sleep
        addTract(sensorLobe, desireLobe, 4,0,4,0, 2,0,2,0, DendriteSVRule.identity()); // Hostile -> Run
        addTract(sensorLobe, desireLobe, 6,0,6,0, 3,0,3,0, DendriteSVRule.identity()); // Curiosity -> Explore
        addTract(sensorLobe, desireLobe, 7,0,7,0, 4,0,4,0, DendriteSVRule.identity()); // Social -> Socialize
        addTract(sensorLobe, desireLobe, 5,0,5,0, 5,0,5,0, DendriteSVRule.identity()); // FoodNearby -> FindFood
        addTract(sensorLobe, desireLobe, 10,0,10,0, 5,0,5,0, DendriteSVRule.identity()); // Glucose -> FindFood
        addTract(sensorLobe, desireLobe, 8,0,8,0, 6,0,6,0, DendriteSVRule.identity()); // Temp -> Fight/Comfort
        addTract(sensorLobe, desireLobe, 2,0,2,0, 8,0,8,0, DendriteSVRule.identity()); // Health -> Protect
    }

    private void addTract(BrainLobe src, BrainLobe dst, int sx, int sy, int sx2, int sy2, int dx, int dy, int dx2, int dy2, DendriteSVRule rule) {
        brain.addTract(new BrainTract(src, dst, sx, sy, sx2, sy2, dx, dy, dx2, dy2, 0, 0, false, rule));
    }

    public LearningSystem getLearningSystem() { return learningSystem; }
    public Genome getGenome() { return genome; }
    public CreatureBrain getBrain() { return brain; }
    public BiochemistrySystem getBiochemistry() { return biochemistry; }

    public float getFatigue() { return fatigue; }
    public void setFatigue(float fatigue) { this.fatigue = Math.max(0f, Math.min(100f, fatigue)); }

    public float getChemicalLevel(String name) {
        Chemical c = biochemistry.getChemical(name);
        return c == null ? 0f : c.getConcentration();
    }

    public void modifyChemical(String name, float delta) {
        Chemical c = biochemistry.getChemical(name);
        if (c != null) c.modifyConcentration(delta);
    }

    @Override
    public float getSensorValue(String sensorName) {
        switch (sensorName) {
            case "Hunger":
                var att = villager.getData(ModAttachments.VILLAGER.get());
                return att != null ? (float)(20.0 - att.getHunger()) / 20.0f : 0.5f;
            case "Fatigue":
                return fatigue / 100.0f;
            case "Health":
                return villager.getHealth() / villager.getMaxHealth();
            case "Light":
                return villager.level().getBrightness(LightLayer.BLOCK, villager.blockPosition()) / 15.0f;
            case "HostileNearby":
                return cachedHostileNearby;
            case "SocialNearby":
                return cachedSocialNearby;
            case "PlayerNearby":
                return cachedPlayerNearby;
            case "Curiosity":
                return 0.5f * genome.basalCuriosity;
            case "Temperature": {
                Biome biome = villager.level().getBiome(villager.blockPosition()).value();
                float temp = (biome.getBaseTemperature() - 0.2f) / 1.0f;
                return Math.min(1f, Math.max(0f, temp));
            }
            case "TimeOfDay": {
                long time = villager.level().getDayTime() % 24000;
                return time > 12000 ? (time - 12000) / 12000f : 0f;
            }
            case "Glucose":
                return getChemicalLevel("Glucose");
            default:
                return 0f;
        }
    }

    private void updateCachedSensors() {
        // Оптимизированный редкий расчет пространственного окружения жителя
        List<Monster> monsters = villager.level().getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(16), Entity::isAlive);
        cachedHostileNearby = monsters.isEmpty() ? 0f : Math.min(1f, monsters.size() / 4f);

        List<Villager> neighbors = villager.level().getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(12), e -> e != villager && e.isAlive());
        cachedSocialNearby = neighbors.isEmpty() ? 0f : Math.min(1f, neighbors.size() / 5f);

        boolean playerNear = !villager.level().players().isEmpty() &&
                villager.level().players().stream().anyMatch(p -> p.distanceToSqr(villager) < 100);
        cachedPlayerNearby = playerNear ? 1f : 0f;
    }

    private void updateSensorsInBrain() {
        if (brain == null) return;
        BrainLobe sensorLobe = brain.getLobe(0);
        if (sensorLobe == null) return;
        sensorLobe.setRegister(0, 0, 0, getSensorValue("Hunger"));
        sensorLobe.setRegister(1, 0, 0, getSensorValue("Fatigue"));
        sensorLobe.setRegister(2, 0, 0, getSensorValue("Health"));
        sensorLobe.setRegister(3, 0, 0, getSensorValue("Light"));
        sensorLobe.setRegister(4, 0, 0, getSensorValue("HostileNearby"));
        sensorLobe.setRegister(5, 0, 0, getSensorValue("SocialNearby"));
        sensorLobe.setRegister(6, 0, 0, getSensorValue("Curiosity"));
        sensorLobe.setRegister(7, 0, 0, getSensorValue("SocialNearby"));
        sensorLobe.setRegister(8, 0, 0, getSensorValue("Temperature"));
        sensorLobe.setRegister(9, 0, 0, getSensorValue("TimeOfDay"));
        sensorLobe.setRegister(10, 0, 0, getSensorValue("Glucose"));
    }

    public void tick() {
        long gameTime = villager.level().getGameTime();
        // Индивидуальное смещение для каждого жителя для сглаживания нагрузки (staggered ticking)
        int offset = Math.abs(villager.getUUID().hashCode() % 10);

        // 1. Циркадные ритмы и забывание (раз в секунду)
        if ((gameTime + offset) % 20 == 0) {
            learningSystem.updateCircadian(gameTime);
            learningSystem.tickForgetting();
        }

        // 2. Медленный шахматный цикл ИИ и биохимии (раз в 10 тиков, распределенно)
        if ((gameTime + offset) % 10 == 0) {
            updateCachedSensors();
            updateSensorsInBrain();

            if (biochemistry != null) {
                biochemistry.tick(villager); // Тикаем с привязкой к сущности жителя
            }
            if (brain != null) {
                brain.tick(this);
            }

            // Физиологический расход ресурсов при активности
            if (villager.getNavigation().isInProgress()) {
                modifyChemical("ATP", -0.003f * genome.energyEfficiency);
                setFatigue(fatigue + 1.2f);
            } else {
                setFatigue(fatigue - 0.6f);
            }

            // Медленная пассивная трата глюкозы
            modifyChemical("Glucose", -0.002f * genome.metabolismRate);
        }
    }

    public static VillagerBrainWrapper getOrCreate(Villager villager) {
        return wrappers.computeIfAbsent(villager.getUUID(), u -> new VillagerBrainWrapper(villager));
    }

    public static VillagerBrainWrapper get(Villager villager) {
        return wrappers.get(villager.getUUID());
    }

    public static void remove(UUID uuid) { wrappers.remove(uuid); }

    // Добавлено: считывает сильнейшее биологическое желание жителя из CreatureBrain
    public String getStrongestDesire() {
        if (brain == null) return "Спокоен";
        BrainLobe desireLobe = brain.getLobe(1);
        if (desireLobe == null) return "Спокоен";

        float max = 0.15f; // Порог спокойствия
        String strongest = "Спокоен";

        String[] desires = {"EAT", "SLEEP", "RUN_AWAY", "EXPLORE", "SOCIALIZE", "FIND_FOOD"};
        for (int i = 0; i < desires.length; i++) {
            // В Lobe 1 (желания) регистры отвечают за разные действия жителей
            float val = desireLobe.getActivation(i, 0);
            if (val > max) {
                max = val;
                strongest = desires[i];
            }
        }
        return strongest;
    }
}