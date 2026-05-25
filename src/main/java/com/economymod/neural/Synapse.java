package com.economymod.neural;

public class Synapse {
    private final Neuron from;
    private final Neuron to;
    private float weight;
    private float lastFromActivation = 0.0f;

    public Synapse(Neuron from, Neuron to, float initialWeight) {
        this.from = from;
        this.to = to;
        this.weight = Math.min(NeuralConfig.MAX_WEIGHT, Math.max(NeuralConfig.MIN_WEIGHT, initialWeight));
    }

    public float getWeight() { return weight; }
    public void setWeight(float w) {
        this.weight = Math.min(NeuralConfig.MAX_WEIGHT, Math.max(NeuralConfig.MIN_WEIGHT, w));
    }
    public Neuron getFrom() { return from; }
    public Neuron getTo() { return to; }

    public void transmit(float fromActivation) {
        float signal = fromActivation * weight;
        to.receiveSignal(signal);

        if (fromActivation > 0.5f && lastFromActivation > 0.5f && to.getActivation() > 0.5f) {
            weight += NeuralConfig.LEARNING_RATE;
            weight = Math.min(NeuralConfig.MAX_WEIGHT, Math.max(NeuralConfig.MIN_WEIGHT, weight));
        }
        lastFromActivation = fromActivation;
    }
}