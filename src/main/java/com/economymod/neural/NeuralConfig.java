package com.economymod.neural;

public final class NeuralConfig {
    public static boolean ENABLED = true;
    public static boolean PROCESS_VILLAGERS = false; // ← НОВЫЙ ФЛАГ: обрабатывать ли жителей (по умолчанию false, чтобы не лагало)

    // Параметры нейронов
    public static final float ACTIVATION_THRESHOLD = 0.5f;
    public static final float DECAY_RATE = 0.01f;
    public static final float LEAK_RATE = 0.1f;

    // Параметры синапсов
    public static final float LEARNING_RATE = 0.001f;
    public static final float MAX_WEIGHT = 2.0f;
    public static final float MIN_WEIGHT = -2.0f;

    private NeuralConfig() {}
}