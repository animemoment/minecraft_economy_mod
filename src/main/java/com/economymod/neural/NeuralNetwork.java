package com.economymod.neural;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;

import java.util.*;

public class NeuralNetwork {
    private final LivingEntity entity; // теперь LivingEntity (может быть Villager или TestCreatureEntity)
    private final Map<String, Neuron> neurons = new HashMap<>();
    private final List<Neuron> sensorNeurons = new ArrayList<>();
    private final List<Neuron> outputNeurons = new ArrayList<>();

    public static final String OUTPUT_EAT = "EAT";
    public static final String OUTPUT_SLEEP = "SLEEP";
    public static final String OUTPUT_TRADE = "TRADE";
    public static final String OUTPUT_RUN_AWAY = "RUN_AWAY";
    public static final String OUTPUT_EXPLORE = "EXPLORE";
    public static final String OUTPUT_SOCIALIZE = "SOCIALIZE";

    public static final String SENSOR_HUNGER = "HUNGER";
    public static final String SENSOR_FATIGUE = "FATIGUE";
    public static final String SENSOR_HEALTH = "HEALTH";
    public static final String SENSOR_SOCIAL = "SOCIAL";
    public static final String SENSOR_HOSTILE = "HOSTILE";
    public static final String SENSOR_COMFORT = "COMFORT";
    public static final String SENSOR_CURIOSITY = "CURIOSITY";

    public static final String CHEM_DOPAMINE = "DOPAMINE";
    public static final String CHEM_SEROTONIN = "SEROTONIN";
    public static final String CHEM_CORTISOL = "CORTISOL";
    public static final String CHEM_ENDORPHIN = "ENDORPHIN";
    public static final String CHEM_OXITOCIN = "OXITOCIN";

    private int tickCounter = 0;

    public NeuralNetwork(LivingEntity entity) {
        this.entity = entity;
        buildNetwork();
    }

    private void buildNetwork() {
        // Создаём нейроны (как раньше)
        createNeuron(SENSOR_HUNGER, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_FATIGUE, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_HEALTH, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_SOCIAL, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_HOSTILE, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_COMFORT, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_CURIOSITY, Neuron.NeuronType.SENSOR);

        createNeuron(CHEM_DOPAMINE, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_SEROTONIN, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_CORTISOL, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_ENDORPHIN, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_OXITOCIN, Neuron.NeuronType.INTERNAL);

        createNeuron(OUTPUT_EAT, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_SLEEP, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_TRADE, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_RUN_AWAY, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_EXPLORE, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_SOCIALIZE, Neuron.NeuronType.OUTPUT);

        // Связи (как раньше)
        connect(SENSOR_HUNGER, CHEM_DOPAMINE, -0.5f);
        connect(SENSOR_HUNGER, CHEM_CORTISOL, 0.6f);
        connect(SENSOR_FATIGUE, CHEM_SEROTONIN, -0.4f);
        connect(SENSOR_FATIGUE, CHEM_CORTISOL, 0.5f);
        connect(SENSOR_HEALTH, CHEM_ENDORPHIN, -0.3f);
        connect(SENSOR_HEALTH, CHEM_CORTISOL, 0.7f);
        connect(SENSOR_SOCIAL, CHEM_OXITOCIN, 0.8f);
        connect(SENSOR_SOCIAL, CHEM_DOPAMINE, 0.5f);
        connect(SENSOR_HOSTILE, CHEM_CORTISOL, 1.2f);
        connect(SENSOR_HOSTILE, CHEM_SEROTONIN, -0.6f);
        connect(SENSOR_COMFORT, CHEM_SEROTONIN, 0.7f);
        connect(SENSOR_COMFORT, CHEM_DOPAMINE, 0.4f);
        connect(SENSOR_CURIOSITY, CHEM_DOPAMINE, 0.3f);

        connect(CHEM_CORTISOL, OUTPUT_RUN_AWAY, 1.0f);
        connect(CHEM_CORTISOL, OUTPUT_TRADE, -0.5f);
        connect(CHEM_DOPAMINE, OUTPUT_EAT, 0.6f);
        connect(CHEM_DOPAMINE, OUTPUT_EXPLORE, 0.4f);
        connect(CHEM_SEROTONIN, OUTPUT_SLEEP, 0.7f);
        connect(CHEM_SEROTONIN, OUTPUT_EAT, -0.3f);
        connect(CHEM_OXITOCIN, OUTPUT_SOCIALIZE, 0.9f);
        connect(CHEM_ENDORPHIN, OUTPUT_RUN_AWAY, -0.5f);

        connect(SENSOR_HUNGER, OUTPUT_EAT, 0.8f);
        connect(SENSOR_FATIGUE, OUTPUT_SLEEP, 0.9f);
        connect(SENSOR_HOSTILE, OUTPUT_RUN_AWAY, 1.2f);
        connect(SENSOR_SOCIAL, OUTPUT_SOCIALIZE, 0.7f);
    }

    private Neuron createNeuron(String name, Neuron.NeuronType type) {
        Neuron n = new Neuron(name, type);
        neurons.put(name, n);
        if (type == Neuron.NeuronType.SENSOR) sensorNeurons.add(n);
        if (type == Neuron.NeuronType.OUTPUT) outputNeurons.add(n);
        return n;
    }

    private void connect(String from, String to, float weight) {
        Neuron fromNeuron = neurons.get(from);
        Neuron toNeuron = neurons.get(to);
        if (fromNeuron != null && toNeuron != null) {
            fromNeuron.addOutput(new Synapse(fromNeuron, toNeuron, weight));
        }
    }

    public void tick() {
        if (!NeuralConfig.ENABLED) return;
        updateSensors();
        for (Neuron n : neurons.values()) {
            n.update();
        }
        applyChemistryEffects();
        tickCounter++;
    }

    private void updateSensors() {
        if (entity == null) {
            // Заглушка для тестирования без entity
            stimulate(SENSOR_HUNGER, 0.5f);
            stimulate(SENSOR_FATIGUE, 0.3f);
            stimulate(SENSOR_HEALTH, 0.2f);
            stimulate(SENSOR_SOCIAL, 0.1f);
            stimulate(SENSOR_HOSTILE, 0.0f);
            stimulate(SENSOR_COMFORT, 0.7f);
            stimulate(SENSOR_CURIOSITY, 0.4f);
            return;
        }

        // Специфичные сенсоры для разных типов сущностей можно добавить здесь
        // Пока оставим заглушки — реальные значения обновляются в самом entity (например, в TestCreatureEntity)
        // Этот метод вызывается каждый тик, но сенсоры должны стимулироваться извне через stimulateSensor()
    }

    public void stimulateSensor(String sensorName, float intensity) {
        Neuron n = neurons.get(sensorName);
        if (n != null) n.stimulate(intensity);
    }

    private void stimulate(String neuronName, float intensity) {
        Neuron n = neurons.get(neuronName);
        if (n != null) n.stimulate(intensity);
    }

    private void applyChemistryEffects() {
        if (entity == null) return;
        Neuron serotonin = neurons.get(CHEM_SEROTONIN);
        if (serotonin != null && serotonin.getActivation() > 0.7f && entity.tickCount % 40 == 0 && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(0.5f);
        }
    }

    public float getOutputActivation(String outputName) {
        Neuron n = neurons.get(outputName);
        return n != null ? n.getActivation() : 0.0f;
    }

    public String getStrongestDesire() {
        float max = 0.0f;
        String best = null;
        for (Neuron n : outputNeurons) {
            if (n.getActivation() > max) {
                max = n.getActivation();
                best = n.name;
            }
        }
        return best;
    }

    // ========== СОХРАНЕНИЕ И ЗАГРУЗКА ВЕСОВ СИНАПСОВ ==========
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag synapsesTag = new ListTag();

        // Сохраняем все синапсы всех нейронов
        for (Neuron neuron : neurons.values()) {
            for (Synapse synapse : neuron.getOutputs()) {
                CompoundTag synTag = new CompoundTag();
                synTag.putString("From", synapse.getFrom().name);
                synTag.putString("To", synapse.getTo().name);
                synTag.putFloat("Weight", synapse.getWeight());
                synapsesTag.add(synTag);
            }
        }
        tag.put("Synapses", synapsesTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Synapses")) return;
        ListTag synapsesTag = tag.getList("Synapses", Tag.TAG_COMPOUND);

        // Восстанавливаем веса
        for (int i = 0; i < synapsesTag.size(); i++) {
            CompoundTag synTag = synapsesTag.getCompound(i);
            String fromName = synTag.getString("From");
            String toName = synTag.getString("To");
            float weight = synTag.getFloat("Weight");

            Neuron from = neurons.get(fromName);
            Neuron to = neurons.get(toName);
            if (from != null && to != null) {
                for (Synapse synapse : from.getOutputs()) {
                    if (synapse.getTo() == to) {
                        synapse.setWeight(weight);
                        break;
                    }
                }
            }
        }
    }
}