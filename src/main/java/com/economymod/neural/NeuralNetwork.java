package com.economymod.neural;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;

import java.util.*;

public class NeuralNetwork {
    private final LivingEntity entity; // Житель или тестовое существо, к которому привязан мозг
    private final Map<String, Neuron> neurons = new HashMap<>();
    private final List<Neuron> sensorNeurons = new ArrayList<>();
    private final List<Neuron> outputNeurons = new ArrayList<>();
    private final List<Neuron> associativeNeurons = new ArrayList<>();

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

    public NeuralNetwork(LivingEntity entity) {
        this.entity = entity;
        buildNetwork();
    }

    private void buildNetwork() {
        // 1. Создаем базовые сенсорные нейроны
        createNeuron(SENSOR_HUNGER, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_FATIGUE, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_HEALTH, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_SOCIAL, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_HOSTILE, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_COMFORT, Neuron.NeuronType.SENSOR);
        createNeuron(SENSOR_CURIOSITY, Neuron.NeuronType.SENSOR);

        // 2. Создаем внутренние биохимические нейроны
        createNeuron(CHEM_DOPAMINE, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_SEROTONIN, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_CORTISOL, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_ENDORPHIN, Neuron.NeuronType.INTERNAL);
        createNeuron(CHEM_OXITOCIN, Neuron.NeuronType.INTERNAL);

        // 3. Создаем базовые выходные нейроны действий
        createNeuron(OUTPUT_EAT, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_SLEEP, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_TRADE, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_RUN_AWAY, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_EXPLORE, Neuron.NeuronType.OUTPUT);
        createNeuron(OUTPUT_SOCIALIZE, Neuron.NeuronType.OUTPUT);

        // 4. Генерируем эволюционный пул из 100 свободных ("пустых") ассоциативных нейронов памяти
        for (int i = 0; i < 100; i++) {
            createNeuron("ASSOC_" + i, Neuron.NeuronType.ASSOCIATIVE);
        }

        // 5. Выстраиваем врожденные жесткие связи (подложка эволюции)
        connect(SENSOR_HUNGER, CHEM_DOPAMINE, -0.5f, false);
        connect(SENSOR_HUNGER, CHEM_CORTISOL, 0.6f, false);
        connect(SENSOR_FATIGUE, CHEM_SEROTONIN, -0.4f, false);
        connect(SENSOR_FATIGUE, CHEM_CORTISOL, 0.5f, false);
        connect(SENSOR_HEALTH, CHEM_ENDORPHIN, -0.3f, false);
        connect(SENSOR_HEALTH, CHEM_CORTISOL, 0.7f, false);
        connect(SENSOR_SOCIAL, CHEM_OXITOCIN, 0.8f, false);
        connect(SENSOR_SOCIAL, CHEM_DOPAMINE, 0.5f, false);
        connect(SENSOR_HOSTILE, CHEM_CORTISOL, 1.2f, false);
        connect(SENSOR_HOSTILE, CHEM_SEROTONIN, -0.6f, false);
        connect(SENSOR_COMFORT, CHEM_SEROTONIN, 0.7f, false);
        connect(SENSOR_COMFORT, CHEM_DOPAMINE, 0.4f, false);
        connect(SENSOR_CURIOSITY, CHEM_DOPAMINE, 0.3f, false);

        connect(CHEM_CORTISOL, OUTPUT_RUN_AWAY, 1.0f, false);
        connect(CHEM_CORTISOL, OUTPUT_TRADE, -0.5f, false);
        connect(CHEM_DOPAMINE, OUTPUT_EAT, 0.6f, false);
        connect(CHEM_DOPAMINE, OUTPUT_EXPLORE, 0.4f, false);
        connect(CHEM_SEROTONIN, OUTPUT_SLEEP, 0.7f, false);
        connect(CHEM_SEROTONIN, OUTPUT_EAT, -0.3f, false);
        connect(CHEM_OXITOCIN, OUTPUT_SOCIALIZE, 0.9f, false);
        connect(CHEM_ENDORPHIN, OUTPUT_RUN_AWAY, -0.5f, false);

        // Врожденные безусловные рефлексы
        connect(SENSOR_HUNGER, OUTPUT_EAT, 0.8f, false);
        connect(SENSOR_FATIGUE, OUTPUT_SLEEP, 0.9f, false);
        connect(SENSOR_HOSTILE, OUTPUT_RUN_AWAY, 1.2f, false);
        connect(SENSOR_SOCIAL, OUTPUT_SOCIALIZE, 0.7f, false);
    }

    private Neuron createNeuron(String name, Neuron.NeuronType type) {
        Neuron n = new Neuron(name, type);
        neurons.put(name, n);
        if (type == Neuron.NeuronType.SENSOR) sensorNeurons.add(n);
        if (type == Neuron.NeuronType.OUTPUT) outputNeurons.add(n);
        if (type == Neuron.NeuronType.ASSOCIATIVE) associativeNeurons.add(n);
        return n;
    }

    private void connect(String from, String to, float weight, boolean plastic) {
        Neuron fromNeuron = neurons.get(from);
        Neuron toNeuron = neurons.get(to);
        if (fromNeuron != null && toNeuron != null) {
            fromNeuron.addOutput(new Synapse(fromNeuron, toNeuron, weight, plastic));
        }
    }

    private boolean hasConnection(Neuron from, Neuron to) {
        for (Synapse synapse : from.getOutputs()) {
            if (synapse.getTo() == to) return true;
        }
        return false;
    }

    private boolean isValidConnectionPair(Neuron from, Neuron to) {
        // Ограничиваем потенциальный синаптический взрыв (O(N^2)) только логичными путями передачи сигнала:
        // Сенсоры -> Ассоциативные, Ассоциативные -> Ассоциативные, Ассоциативные -> Выходы действий
        if (from.type == Neuron.NeuronType.SENSOR && to.type == Neuron.NeuronType.ASSOCIATIVE) return true;
        if (from.type == Neuron.NeuronType.ASSOCIATIVE && to.type == Neuron.NeuronType.ASSOCIATIVE) return true;
        if (from.type == Neuron.NeuronType.ASSOCIATIVE && to.type == Neuron.NeuronType.OUTPUT) return true;
        return false;
    }

    public void tick() {
        if (!NeuralConfig.ENABLED) return;

        // 1. Считываем данные с сенсоров тела
        updateSensors();

        // 2. Рассчитываем электрические сигналы во всех нейронах
        for (Neuron n : neurons.values()) {
            n.update();
        }

        // 3. ДИНАМИЧЕСКИЙ СИНАПТОГЕНЕЗ (Автоматическое самозарождение связей)
        // Сканируем все активные нейроны в данный момент времени
        List<Neuron> activeNeurons = new ArrayList<>();
        for (Neuron n : neurons.values()) {
            if (n.getActivation() > 0.5f) {
                activeNeurons.add(n);
            }
        }

        // Если одновременно активны несколько нейронов, выстраиваем между ними синапсы
        if (activeNeurons.size() > 1) {
            for (int i = 0; i < activeNeurons.size(); i++) {
                for (int j = 0; j < activeNeurons.size(); j++) {
                    if (i == j) continue;
                    Neuron from = activeNeurons.get(i);
                    Neuron to = activeNeurons.get(j);

                    if (isValidConnectionPair(from, to)) {
                        if (!hasConnection(from, to)) {
                            // Связи создаются сами, без нашей ручной прокладки! Стартовый вес слабый (0.1f)
                            connect(from.name, to.name, 0.1f, true);
                        }
                    }
                }
            }
        }

        // 4. СИНАПТИЧЕСКИЙ РАСПАД И СЕЛЕКЦИЯ (ОТМИРАНИЕ СВЯЗЕЙ)
        // Проверяем все исходящие связи во всех нейронах
        for (Neuron n : neurons.values()) {
            n.getOutputs().removeIf(synapse -> {
                if (synapse.isPlastic()) {
                    synapse.decay(); // Медленное затухание веса синапса со временем

                    // Если вес упал ниже минимума (0.02) — связь отмирает навсегда!
                    return synapse.getWeight() < 0.02f;
                }
                return false;
            });
        }

        // 5. Влияние химии на физиологию
        applyChemistryEffects();
    }

    private void updateSensors() {
        if (entity == null) {
            stimulate(SENSOR_HUNGER, 0.5f);
            stimulate(SENSOR_FATIGUE, 0.3f);
            stimulate(SENSOR_HEALTH, 0.2f);
            stimulate(SENSOR_SOCIAL, 0.1f);
            stimulate(SENSOR_HOSTILE, 0.0f);
            stimulate(SENSOR_COMFORT, 0.7f);
            stimulate(SENSOR_CURIOSITY, 0.4f);
        }
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

    // ========== СОХРАНЕНИЕ И ЗАГРУЗКА ИЗУЧЕННОЙ СЕТИ И СИНАПСОВ ==========
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag synapsesTag = new ListTag();

        for (Neuron neuron : neurons.values()) {
            for (Synapse synapse : neuron.getOutputs()) {
                CompoundTag synTag = new CompoundTag();
                synTag.putString("From", synapse.getFrom().name);
                synTag.putString("To", synapse.getTo().name);
                synTag.putFloat("Weight", synapse.getWeight());
                synTag.putBoolean("Plastic", synapse.isPlastic());
                synapsesTag.add(synTag);
            }
        }
        tag.put("Synapses", synapsesTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Synapses")) return;
        ListTag synapsesTag = tag.getList("Synapses", Tag.TAG_COMPOUND);

        // Очищаем старые динамические синапсы, чтобы избежать наложения при загрузке
        for (Neuron neuron : neurons.values()) {
            neuron.getOutputs().removeIf(Synapse::isPlastic);
        }

        // Восстанавливаем синапсы (как эволюционные, так и самозародившиеся)
        for (int i = 0; i < synapsesTag.size(); i++) {
            CompoundTag synTag = synapsesTag.getCompound(i);
            String fromName = synTag.getString("From");
            String toName = synTag.getString("To");
            float weight = synTag.getFloat("Weight");
            boolean plastic = synTag.getBoolean("Plastic");

            Neuron from = neurons.get(fromName);
            Neuron to = neurons.get(toName);
            if (from != null && to != null) {
                // Ищем существующий врожденный синапс
                Synapse existing = null;
                for (Synapse s : from.getOutputs()) {
                    if (s.getTo() == to) {
                        existing = s;
                        break;
                    }
                }

                if (existing != null) {
                    existing.setWeight(weight);
                } else {
                    // Если синапс самозародился во время жизни жителя — создаем его заново при загрузке мира
                    from.addOutput(new Synapse(from, to, weight, plastic));
                }
            }
        }
    }
}