package com.economymod.creatures;

public class BrainTract {
    private final BrainLobe sourceLobe;
    private final BrainLobe targetLobe;
    private final int srcX1, srcY1, srcX2, srcY2;
    private final int dstX1, dstY1, dstX2, dstY2;
    private final int sourceRegister;
    private final int targetRegister;
    private final boolean addToExisting;
    private final DendriteSVRule rule;

    // Вес и след (eligibility trace) для обучения
    private float weight;
    private float trace;
    private static final float MAX_WEIGHT = 3.0f;
    private static final float MIN_WEIGHT = 0.1f;
    private static final float TRACE_DECAY = 0.95f;

    public BrainTract(BrainLobe source, BrainLobe target,
                      int srcX1, int srcY1, int srcX2, int srcY2,
                      int dstX1, int dstY1, int dstX2, int dstY2,
                      int sourceRegister, int targetRegister, boolean addToExisting,
                      DendriteSVRule rule) {
        this.sourceLobe = source;
        this.targetLobe = target;
        this.srcX1 = srcX1; this.srcY1 = srcY1; this.srcX2 = srcX2; this.srcY2 = srcY2;
        this.dstX1 = dstX1; this.dstY1 = dstY1; this.dstX2 = dstX2; this.dstY2 = dstY2;
        this.sourceRegister = sourceRegister;
        this.targetRegister = targetRegister;
        this.addToExisting = addToExisting;
        this.rule = rule != null ? rule : DendriteSVRule.identity();
        this.weight = 1.0f;
        this.trace = 0f;

        int srcWidth = srcX2 - srcX1 + 1;
        int srcHeight = srcY2 - srcY1 + 1;
        int dstWidth = dstX2 - dstX1 + 1;
        int dstHeight = dstY2 - dstY1 + 1;
        if (srcWidth != dstWidth || srcHeight != dstHeight) {
            throw new IllegalArgumentException("Source and destination ranges must have same dimensions");
        }
    }

    public float getWeight() { return weight; }
    public void setWeight(float w) { weight = Math.min(MAX_WEIGHT, Math.max(MIN_WEIGHT, w)); }
    public void strengthen(float delta) { setWeight(weight + delta); }
    public void weaken(float delta) { setWeight(weight - delta); }

    public void decayTrace() { trace *= TRACE_DECAY; }

    public void applyReinforcement(float r, float learningRate) {
        float delta = learningRate * r * trace;
        setWeight(weight + delta);
    }

    public void apply() {
        int srcWidth = srcX2 - srcX1 + 1;
        int srcHeight = srcY2 - srcY1 + 1;

        for (int dx = 0; dx < srcWidth; dx++) {
            for (int dy = 0; dy < srcHeight; dy++) {
                int srcX = srcX1 + dx;
                int srcY = srcY1 + dy;
                int dstX = dstX1 + dx;
                int dstY = dstY1 + dy;

                float sourceValue = sourceLobe.getRegister(srcX, srcY, sourceRegister);
                float rawSignal = rule.execute(sourceValue);
                float signal = rawSignal * weight;

                if (rawSignal > 0.5f) {
                    trace = Math.min(1f, trace + 0.1f);
                }

                if (addToExisting) {
                    float current = targetLobe.getRegister(dstX, dstY, targetRegister);
                    targetLobe.setRegister(dstX, dstY, targetRegister, current + signal);
                } else {
                    targetLobe.setRegister(dstX, dstY, targetRegister, signal);
                }
            }
        }
    }
}