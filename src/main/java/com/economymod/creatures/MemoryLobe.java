package com.economymod.creatures;

public class MemoryLobe extends BrainLobe {
    private final float decayRate;

    public MemoryLobe(int width, int height, int registersPerNeuron, float decayRate, SVRule updateRule) {
        super(width, height, registersPerNeuron, updateRule);
        this.decayRate = decayRate;
    }

    @Override
    public void tick(SensorProvider sensorProvider) {
        super.tick(sensorProvider);
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int r = 0; r < getRegistersPerNeuron(); r++) {
                    float val = getRegister(x, y, r);
                    setRegister(x, y, r, val * decayRate);
                }
            }
        }
    }
}