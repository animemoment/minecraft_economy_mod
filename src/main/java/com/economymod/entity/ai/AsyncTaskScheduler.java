package com.economymod.entity.ai;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncTaskScheduler {

    // Пул потоков ИИ, размерность подстраивается под количество ядер процессора
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            runnable -> {
                Thread thread = new Thread(runnable, "EconomyMod-AI-Worker");
                thread.setDaemon(true); // Демонический поток автоматически завершается при закрытии JVM
                return thread;
            }
    );

    /**
     * Отправить тяжелую задачу сканирования ИИ на параллельное выполнение
     */
    public static Future<?> submit(Runnable task) {
        return EXECUTOR.submit(task);
    }
}