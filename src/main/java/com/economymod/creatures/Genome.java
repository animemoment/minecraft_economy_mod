package com.economymod.creatures;

import net.minecraft.nbt.CompoundTag;
import java.util.Random;

/**
 * Геном существа: набор генов, влияющих на личность, метаболизм, обучаемость.
 * Передаётся по наследству с кроссинговером и мутациями.
 */
public class Genome {
    // Гены (значения в диапазоне 0..1, но некоторые могут быть шире)
    public float metabolismRate;      // скорость метаболизма (0.5..1.5) – голод растёт быстрее
    public float basalCuriosity;      // базовая любопытность (0..1)
    public float fearProne;           // склонность к страху (0..1)
    public float aggression;          // агрессивность (0..1) – влияет на желание драться
    public float learningRate;        // скорость обучения (0..1) – насколько быстро учится
    public float forgetfulness;       // забывчивость (0..1) – обратно пропорционально забыванию
    public float socialNeed;          // потребность в социализации (0..1)
    public float energyEfficiency;    // эффективность энергии (0.5..1.5) – насколько быстро устаёт

    private static final Random random = new Random();

    // Конструктор для случайного генома (при рождении от природы)
    public Genome() {
        this.metabolismRate = 0.7f + random.nextFloat() * 0.8f;
        this.basalCuriosity = random.nextFloat();
        this.fearProne = random.nextFloat();
        this.aggression = random.nextFloat();
        this.learningRate = 0.3f + random.nextFloat() * 0.6f;
        this.forgetfulness = random.nextFloat();
        this.socialNeed = random.nextFloat();
        this.energyEfficiency = 0.6f + random.nextFloat() * 0.8f;
    }

    // Конструктор из родителей (кроссинговер + мутации)
    public Genome(Genome parent1, Genome parent2) {
        // Кроссинговер: случайный выбор гена от одного из родителей
        this.metabolismRate = random.nextBoolean() ? parent1.metabolismRate : parent2.metabolismRate;
        this.basalCuriosity = random.nextBoolean() ? parent1.basalCuriosity : parent2.basalCuriosity;
        this.fearProne = random.nextBoolean() ? parent1.fearProne : parent2.fearProne;
        this.aggression = random.nextBoolean() ? parent1.aggression : parent2.aggression;
        this.learningRate = random.nextBoolean() ? parent1.learningRate : parent2.learningRate;
        this.forgetfulness = random.nextBoolean() ? parent1.forgetfulness : parent2.forgetfulness;
        this.socialNeed = random.nextBoolean() ? parent1.socialNeed : parent2.socialNeed;
        this.energyEfficiency = random.nextBoolean() ? parent1.energyEfficiency : parent2.energyEfficiency;

        // Мутации: с вероятностью 0.1 на ген, изменяем в пределах ±0.1
        mutate(0.1f, 0.1f);
    }

    private void mutate(float probability, float delta) {
        if (random.nextFloat() < probability) metabolismRate = clamp(metabolismRate + (random.nextFloat() * 2 - 1) * delta, 0.5f, 1.5f);
        if (random.nextFloat() < probability) basalCuriosity = clamp(basalCuriosity + (random.nextFloat() * 2 - 1) * delta, 0f, 1f);
        if (random.nextFloat() < probability) fearProne = clamp(fearProne + (random.nextFloat() * 2 - 1) * delta, 0f, 1f);
        if (random.nextFloat() < probability) aggression = clamp(aggression + (random.nextFloat() * 2 - 1) * delta, 0f, 1f);
        if (random.nextFloat() < probability) learningRate = clamp(learningRate + (random.nextFloat() * 2 - 1) * delta, 0.3f, 0.9f);
        if (random.nextFloat() < probability) forgetfulness = clamp(forgetfulness + (random.nextFloat() * 2 - 1) * delta, 0f, 1f);
        if (random.nextFloat() < probability) socialNeed = clamp(socialNeed + (random.nextFloat() * 2 - 1) * delta, 0f, 1f);
        if (random.nextFloat() < probability) energyEfficiency = clamp(energyEfficiency + (random.nextFloat() * 2 - 1) * delta, 0.5f, 1.5f);
    }

    private float clamp(float val, float min, float max) {
        return Math.min(max, Math.max(min, val));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("MetabolismRate", metabolismRate);
        tag.putFloat("BasalCuriosity", basalCuriosity);
        tag.putFloat("FearProne", fearProne);
        tag.putFloat("Aggression", aggression);
        tag.putFloat("LearningRate", learningRate);
        tag.putFloat("Forgetfulness", forgetfulness);
        tag.putFloat("SocialNeed", socialNeed);
        tag.putFloat("EnergyEfficiency", energyEfficiency);
        return tag;
    }

    public void load(CompoundTag tag) {
        metabolismRate = tag.getFloat("MetabolismRate");
        basalCuriosity = tag.getFloat("BasalCuriosity");
        fearProne = tag.getFloat("FearProne");
        aggression = tag.getFloat("Aggression");
        learningRate = tag.getFloat("LearningRate");
        forgetfulness = tag.getFloat("Forgetfulness");
        socialNeed = tag.getFloat("SocialNeed");
        energyEfficiency = tag.getFloat("EnergyEfficiency");
    }
}