package com.economymod.creatures.biochemistry;

public class Reaction {
    private final Chemical inputA;
    private final Chemical inputB; // может быть null
    private final Chemical output; // может быть null (для уничтожения)
    private final float rate;

    public Reaction(Chemical inputA, Chemical inputB, Chemical output, float rate) {
        this.inputA = inputA;
        this.inputB = inputB;
        this.output = output;
        this.rate = rate;
    }

    public void tick() {
        float amountA = inputA.getConcentration();
        float amountB = (inputB != null) ? inputB.getConcentration() : 1.0f;
        float reactionAmount = Math.min(amountA, amountB) * rate;
        inputA.modifyConcentration(-reactionAmount);
        if (inputB != null) inputB.modifyConcentration(-reactionAmount);
        if (output != null) output.modifyConcentration(reactionAmount);
    }
}