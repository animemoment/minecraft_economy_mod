package com.economymod.creatures.biochemistry;

import com.economymod.creatures.BrainLobe;

public class NeuroEmitter {
    private final Chemical target;
    private final float gain;
    private final BrainLobe lobe;
    private final int neuronX, neuronY;

    public NeuroEmitter(Chemical target, BrainLobe lobe, int x, int y, float gain) {
        this.target = target;
        this.gain = gain;
        this.lobe = lobe;
        this.neuronX = x;
        this.neuronY = y;
    }

    public void tick() {
        if (lobe == null) return;
        float activation = lobe.getActivation(neuronX, neuronY);
        float input = Math.max(0, Math.min(1, activation));
        float output = input * gain;
        if (target != null) {
            target.modifyConcentration(output);
        }
    }
}