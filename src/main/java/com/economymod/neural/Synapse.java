package com.economymod.neural;

public class Synapse {
    private final Neuron from;
    private final Neuron to;
    private float weight;
    private float lastFromActivation = 0.0f;
    private final boolean plastic; // true - самозародившийся синапс (разрушаем), false - врожденный

    public Synapse(Neuron from, Neuron to, float initialWeight, boolean plastic) {
        this.from = from;
        this.to = to;
        this.plastic = plastic;
        this.weight = Math.min(NeuralConfig.MAX_WEIGHT, Math.max(NeuralConfig.MIN_WEIGHT, initialWeight));
    }

    public float getWeight() { return weight; }
    public void setWeight(float w) {
        this.weight = Math.min(NeuralConfig.MAX_WEIGHT, Math.max(NeuralConfig.MIN_WEIGHT, w));
    }

    public Neuron getFrom() { return from; }
    public Neuron getTo() { return to; }
    public boolean isPlastic() { return plastic; }

    /**
     * Медленный пассивный распад неиспользуемых связей во времени
     */
    public void decay() {
        if (plastic) {
            this.weight *= 0.998f; // Медленное угасание связи
        }
    }

    /**
     * Передача возбуждения и Хеббовское обучение пластичности
     */
    public void transmit(float fromActivation) {
        float signal = fromActivation * weight;
        to.receiveSignal(signal);

        // Правило Хебба: если предыдущий нейрон возбужден, и следующий нейрон тоже активен, связь усиливается
        if (fromActivation > 0.5f && lastFromActivation > 0.5f && to.getActivation() > 0.5f) {
            // Пластичные связи учатся быстрее врожденных
            float rateMultiplier = plastic ? 4.0f : 1.0f;
            weight += NeuralConfig.LEARNING_RATE * rateMultiplier;
            weight = Math.min(NeuralConfig.MAX_WEIGHT, Math.max(NeuralConfig.MIN_WEIGHT, weight));
        }
        lastFromActivation = fromActivation;
    }
}