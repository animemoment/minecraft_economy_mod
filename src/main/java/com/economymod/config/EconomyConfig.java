package com.economymod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class EconomyConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue TAX_RATE;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_TRADES;
    public static final ModConfigSpec.IntValue MARKET_UPDATE_INTERVAL;
    public static final ModConfigSpec.DoubleValue METABOLISM_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue NEURAL_SYSTEM_ENABLED;

    static {
        BUILDER.push("Economy Mod Config");

        TAX_RATE = BUILDER
                .comment("Налоговая ставка, применяемая к торговым операциям (от 0.0 до 1.0)")
                .defineInRange("taxRate", 0.05, 0.0, 1.0);

        MAX_CONCURRENT_TRADES = BUILDER
                .comment("Максимальное количество параллельных сделок, которые житель может оценивать")
                .defineInRange("maxConcurrentTrades", 8, 1, 64);

        MARKET_UPDATE_INTERVAL = BUILDER
                .comment("Интервал в тиках между перерасчетом рыночных цен в деревнях (200 - 24000)")
                .defineInRange("marketUpdateInterval", 1200, 200, 24000);

        METABOLISM_MULTIPLIER = BUILDER
                .comment("Глобальный множитель скорости голода и метаболизма жителей (0.1 - 10.0)")
                .defineInRange("metabolismMultiplier", 1.0, 0.1, 10.0);

        NEURAL_SYSTEM_ENABLED = BUILDER
                .comment("Включить или отключить сложную динамическую нейросеть жителей")
                .define("neuralSystemEnabled", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}