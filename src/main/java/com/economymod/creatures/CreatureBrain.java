package com.economymod.creatures;

import java.util.ArrayList;
import java.util.List;

public class CreatureBrain {
    private final List<BrainLobe> lobes = new ArrayList<>();
    private final List<BrainTract> tracts = new ArrayList<>();

    public void addLobe(BrainLobe lobe) { lobes.add(lobe); }
    public void addTract(BrainTract tract) { tracts.add(tract); }
    public List<BrainTract> getTracts() { return tracts; }

    public void tick(SensorProvider sensorProvider) {
        for (BrainLobe lobe : lobes) {
            lobe.tick(sensorProvider);
        }
        for (BrainTract tract : tracts) {
            tract.apply();
        }
        for (BrainTract tract : tracts) {
            tract.decayTrace();
        }
    }

    public void reinforce(float r, float learningRate) {
        for (BrainTract tract : tracts) {
            tract.applyReinforcement(r, learningRate);
        }
    }

    public BrainLobe getLobe(int index) { return lobes.get(index); }
    public int getLobeCount() { return lobes.size(); }
}