package com.economymod.creatures.biochemistry;

import net.minecraft.nbt.CompoundTag;

public class Chemical {
    public final String name;
    private float concentration;
    private final float halfLife;

    public Chemical(String name, float initialConcentration, float halfLife) {
        this.name = name;
        this.concentration = initialConcentration;
        this.halfLife = halfLife;
    }

    public float getConcentration() { return concentration; }
    public void setConcentration(float concentration) {
        this.concentration = Math.max(0, Math.min(1, concentration));
    }
    public void modifyConcentration(float delta) { setConcentration(this.concentration + delta); }
    public void decay() {
        if (halfLife > 0) {
            float decayRate = (float) Math.pow(0.5, 1.0 / halfLife);
            concentration *= decayRate;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putFloat("Concentration", concentration);
        return tag;
    }

    public static Chemical load(CompoundTag tag) {
        return new Chemical(tag.getString("Name"), tag.getFloat("Concentration"), 0);
    }
}