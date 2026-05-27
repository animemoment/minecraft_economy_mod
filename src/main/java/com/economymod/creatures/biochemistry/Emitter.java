package com.economymod.creatures.biochemistry;

import java.util.function.Function;
import java.util.function.Supplier;

public class Emitter {
    private final Chemical target;
    private final float gain;
    private final Function<Float, Float> curve;
    private final Supplier<Float> inputSupplier;

    public Emitter(Chemical target, float gain, Function<Float, Float> curve, Supplier<Float> inputSupplier) {
        this.target = target;
        this.gain = gain;
        this.curve = curve;
        this.inputSupplier = inputSupplier;
    }

    public void tick() {
        float input = inputSupplier.get();
        input = Math.max(0, Math.min(1, input));
        float output = curve.apply(input) * gain;
        target.modifyConcentration(output);
    }
}