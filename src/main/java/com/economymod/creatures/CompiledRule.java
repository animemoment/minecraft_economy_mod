package com.economymod.creatures;

public interface CompiledRule {
    /**
     * Выполнить скомпилированное правило нейрона на нативной скорости процессора
     */
    void execute(float[][][] neurons, int x, int y, SensorProvider sensors);
}