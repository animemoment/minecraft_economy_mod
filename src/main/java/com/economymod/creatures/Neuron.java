package com.economymod.creatures;

public class Neuron {
    // Регистры: state[0] - основное состояние, [1] - вспомогательный и т.д.
    private final float[] stateVariables;
    private float output; // активация после порога
    private float threshold;
    private float leakage;
    private float persistence;

    public Neuron(int numStateVars) {
        this.stateVariables = new float[numStateVars];
        this.output = 0f;
        this.threshold = 0.5f;
        this.leakage = 0.05f;
        this.persistence = 0.8f;
    }

    public float getStateVariable(int idx) { return stateVariables[idx]; }
    public void setStateVariable(int idx, float value) { stateVariables[idx] = value; }
    public float getOutput() { return output; }
    public void setOutput(float output) { this.output = Math.max(0f, Math.min(1f, output)); }
    public float getThreshold() { return threshold; }
    public void setThreshold(float threshold) { this.threshold = threshold; }
    public float getLeakage() { return leakage; }
    public float getPersistence() { return persistence; }

    // Обновление выхода на основе состояния и порога
    public void updateOutput() {
        // Простой порог: если состояние выше порога, выход = состояние, иначе 0
        float state = stateVariables[0];
        output = (state > threshold) ? state : 0f;
        // Применяем утечку (leakage) к состоянию
        stateVariables[0] *= (1f - leakage);
    }
}