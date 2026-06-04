package com.economymod.neural;

import java.util.ArrayList;
import java.util.List;

public class Neuron {
    public enum NeuronType {
        SENSOR,      // Входной нейрон (сенсорный датчик тела)
        INTERNAL,    // Внутренний гормональный/биохимический нейрон
        ASSOCIATIVE, // Свободный ассоциативный нейрон памяти
        OUTPUT       // Выходной нейрон (желание/действие)
    }

    public final String name;
    public final NeuronType type;
    private float activation = 0.0f;      // Текущая активация (0.0f - 1.0f)
    private float potential = 0.0f;       // Накопленный потенциал возбуждения
    private final List<Synapse> outputs = new ArrayList<>(); // Исходящие синапсы

    public Neuron(String name, NeuronType type) {
        this.name = name;
        this.type = type;
    }

    public float getActivation() { return activation; }
    public void setActivation(float value) {
        this.activation = Math.min(1.0f, Math.max(0.0f, value));
    }

    public float getPotential() { return potential; }
    public void setPotential(float potential) { this.potential = potential; }

    public void addOutput(Synapse synapse) {
        outputs.add(synapse);
    }

    public List<Synapse> getOutputs() { return outputs; }

    /**
     * Получить электрический сигнал от дендрита (суммирует потенциал)
     */
    public void receiveSignal(float signal) {
        potential += signal;
    }

    /**
     * Обновление нейрона: потенциал перетекает в активацию, затем сигнал передается дальше
     */
    public void update() {
        // Утечка потенциала (затухание заряда мембраны)
        potential *= (1.0f - NeuralConfig.LEAK_RATE);

        // Затухание активации во времени
        activation *= (1.0f - NeuralConfig.DECAY_RATE);

        // Порог активации (спайк)
        if (potential >= NeuralConfig.ACTIVATION_THRESHOLD) {
            activation = 1.0f;
            potential = 0.0f; // Разряд мембраны после спайка
        }

        // Передача сигнала по синапсам к следующим нейронам
        for (Synapse synapse : outputs) {
            synapse.transmit(activation);
        }
    }

    /**
     * Прямая внешняя стимуляция сенсорных или ассоциативных нейронов
     */
    public void stimulate(float intensity) {
        if (type == NeuronType.SENSOR || type == NeuronType.INTERNAL || type == NeuronType.ASSOCIATIVE) {
            receiveSignal(intensity);
        }
    }

    /**
     * Добавление химического концентрата для внутренних нейронов
     */
    public void addChemical(float delta) {
        if (type == NeuronType.INTERNAL) {
            receiveSignal(delta);
        }
    }
}