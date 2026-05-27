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

        int srcWidth = srcX2 - srcX1 + 1;
        int srcHeight = srcY2 - srcY1 + 1;
        int dstWidth = dstX2 - dstX1 + 1;
        int dstHeight = dstY2 - dstY1 + 1;
        if (srcWidth != dstWidth || srcHeight != dstHeight) {
            throw new IllegalArgumentException("Source and destination ranges must have same dimensions");
        }
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
                float signal = rule.execute(sourceValue);

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