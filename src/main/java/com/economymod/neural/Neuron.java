package com.economymod.neural;

import java.util.ArrayList;
import java.util.List;

public class Neuron {
    public enum NeuronType {
        SENSOR,     // входной (получает сигнал извне)
        INTERNAL,   // внутренний (химическая концентрация)
        OUTPUT      // выходной (генерирует желание)
    }

    public final String name;
    public final NeuronType type;
    private float activation = 0.0f;      // текущая активация (0-1)
    private float potential = 0.0f;       // накопленный потенциал
    private final List<Synapse> outputs = new ArrayList<>();  // исходящие связи

    public Neuron(String name, NeuronType type) {
        this.name = name;
        this.type = type;
    }

    public float getActivation() { return activation; }
    public void setActivation(float value) { activation = Math.min(1.0f, Math.max(0.0f, value)); }

    public void addOutput(Synapse synapse) {
        outputs.add(synapse);
    }

    public List<Synapse> getOutputs() { return outputs; }

    /** Получить сигнал от другого нейрона (добавить к потенциалу) */
    public void receiveSignal(float signal) {
        potential += signal;
    }

    /** Обновить нейрон: потенциал → активация, затем отправить сигнал по синапсам */
    public void update() {
        // Утечка потенциала
        potential *= (1.0f - NeuralConfig.LEAK_RATE);
        // Затухание активации
        activation *= (1.0f - NeuralConfig.DECAY_RATE);

        // Если потенциал превысил порог – нейрон активируется
        if (potential >= NeuralConfig.ACTIVATION_THRESHOLD) {
            activation = 1.0f;
            potential = 0.0f;  // сброс после активации
        }

        // Отправить сигнал по всем исходящим синапсам
        for (Synapse synapse : outputs) {
            synapse.transmit(activation);
        }
    }

    /** Прямая стимуляция сенсорного нейрона (извне) */
    public void stimulate(float intensity) {
        if (type == NeuronType.SENSOR || type == NeuronType.INTERNAL) {
            receiveSignal(intensity);
        }
    }

    /** Для внутренних нейронов – изменение химической концентрации */
    public void addChemical(float delta) {
        if (type == NeuronType.INTERNAL) {
            receiveSignal(delta);
        }
    }
}