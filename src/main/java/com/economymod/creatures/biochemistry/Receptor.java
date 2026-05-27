package com.economymod.creatures.biochemistry;

import com.economymod.creatures.BrainLobe;

public class Receptor {
    public enum Target { LEAK_RATE, THRESHOLD, REST_STATE, INPUT_GAIN }

    private final Chemical chemical;
    private final Target target;
    private final BrainLobe lobe;
    private final int neuronX, neuronY;
    private final float multiplier;

    public Receptor(Chemical chemical, Target target, BrainLobe lobe, int x, int y, float multiplier) {
        this.chemical = chemical;
        this.target = target;
        this.lobe = lobe;
        this.neuronX = x;
        this.neuronY = y;
        this.multiplier = multiplier;
    }

    public void apply() {
        float value = chemical.getConcentration() * multiplier;
        switch (target) {
            case LEAK_RATE -> lobe.setLeakRate(neuronX, neuronY, value);
            case THRESHOLD -> lobe.setThreshold(neuronX, neuronY, value);
            case REST_STATE -> lobe.setRestState(neuronX, neuronY, value);
            case INPUT_GAIN -> lobe.setInputGain(neuronX, neuronY, value);
        }
    }
}