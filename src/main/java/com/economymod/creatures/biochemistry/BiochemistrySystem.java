package com.economymod.creatures.biochemistry;

import com.economymod.entity.TestCreatureEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

public class BiochemistrySystem {
    private final List<Chemical> chemicals = new ArrayList<>();
    private final List<Reaction> reactions = new ArrayList<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private final List<Receptor> receptors = new ArrayList<>();
    private final List<NeuroEmitter> neuroEmitters = new ArrayList<>();

    public void addChemical(Chemical c) { chemicals.add(c); }
    public Chemical getChemical(String name) {
        return chemicals.stream().filter(c -> c.name.equals(name)).findFirst().orElse(null);
    }
    public void addReaction(Reaction r) { reactions.add(r); }
    public void addEmitter(Emitter e) { emitters.add(e); }
    public void addReceptor(Receptor r) { receptors.add(r); }
    public void addNeuroEmitter(NeuroEmitter ne) { neuroEmitters.add(ne); }

    public void tick(TestCreatureEntity entity) {
        for (Chemical c : chemicals) c.decay();
        for (Emitter e : emitters) e.tick();
        for (NeuroEmitter ne : neuroEmitters) ne.tick();
        for (Reaction r : reactions) r.tick();
        for (Receptor r : receptors) r.apply();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Chemical c : chemicals) list.add(c.save());
        tag.put("Chemicals", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        ListTag list = tag.getList("Chemicals", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ctag = list.getCompound(i);
            Chemical c = getChemical(ctag.getString("Name"));
            if (c != null) c.setConcentration(ctag.getFloat("Concentration"));
        }
    }
}