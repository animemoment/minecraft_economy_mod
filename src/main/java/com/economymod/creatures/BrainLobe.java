package com.economymod.creatures;

import java.util.List;

public class BrainLobe {
    private final int width;
    private final int height;
    private final int registersPerNeuron;
    private final float[][][] neurons;
    private final SVRule updateRule;

    private final float[][][] leakRate;
    private final float[][][] threshold;
    private final float[][][] restState;
    private final float[][][] inputGain;

    private float accumulator = 0f;
    private int pc = 0;

    public BrainLobe(int width, int height, int registersPerNeuron, SVRule updateRule) {
        this.width = width;
        this.height = height;
        this.registersPerNeuron = registersPerNeuron;
        this.updateRule = updateRule;
        this.neurons = new float[width][height][registersPerNeuron];
        this.leakRate = new float[width][height][1];
        this.threshold = new float[width][height][1];
        this.restState = new float[width][height][1];
        this.inputGain = new float[width][height][1];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                leakRate[x][y][0] = 0f;
                threshold[x][y][0] = 0f;
                restState[x][y][0] = 0f;
                inputGain[x][y][0] = 1f;
            }
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRegistersPerNeuron() { return registersPerNeuron; }

    public float getRegister(int x, int y, int reg) { return neurons[x][y][reg]; }
    public void setRegister(int x, int y, int reg, float value) { neurons[x][y][reg] = value; }

    public void setLeakRate(int x, int y, float value) {
        if (x >= 0 && x < width && y >= 0 && y < height) leakRate[x][y][0] = Math.max(0f, Math.min(1f, value));
    }
    public void setThreshold(int x, int y, float value) {
        if (x >= 0 && x < width && y >= 0 && y < height) threshold[x][y][0] = Math.max(0f, Math.min(1f, value));
    }
    public void setRestState(int x, int y, float value) {
        if (x >= 0 && x < width && y >= 0 && y < height) restState[x][y][0] = Math.max(-1f, Math.min(1f, value));
    }
    public void setInputGain(int x, int y, float value) {
        if (x >= 0 && x < width && y >= 0 && y < height) inputGain[x][y][0] = Math.max(0f, Math.min(5f, value));
    }
    public float getLeakRate(int x, int y) { return leakRate[x][y][0]; }
    public float getThreshold(int x, int y) { return threshold[x][y][0]; }
    public float getRestState(int x, int y) { return restState[x][y][0]; }
    public float getInputGain(int x, int y) { return inputGain[x][y][0]; }
    public float getActivation(int x, int y) { return neurons[x][y][0]; }

    public void tick(SensorProvider sensorProvider) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Временно отключаем затухание и порог
                executeRule(x, y, sensorProvider);
            }
        }
    }

    private void executeRule(int x, int y, SensorProvider sensorProvider) {
        accumulator = 0f;
        pc = 0;
        boolean skipNext = false;
        List<SVRule.Operation> ops = updateRule.getOperations();

        while (pc < ops.size()) {
            SVRule.Operation op = ops.get(pc);
            pc++;

            if (skipNext) {
                skipNext = false;
                continue;
            }

            switch (op.opcode) {
                case LOAD:
                    accumulator = getOperandValue(op, x, y, sensorProvider);
                    break;
                case STORE:
                    if (op.operandType == SVRule.OperandType.REGISTER) {
                        neurons[x][y][op.registerIndex] = accumulator;
                    }
                    break;
                case ADD:
                    accumulator += getOperandValue(op, x, y, sensorProvider);
                    break;
                case SUB:
                    accumulator -= getOperandValue(op, x, y, sensorProvider);
                    break;
                case MUL:
                    accumulator *= getOperandValue(op, x, y, sensorProvider);
                    break;
                case DIV: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (val != 0) accumulator /= val;
                    break;
                }
                case CONST_ZERO:
                    accumulator = 0f;
                    break;
                case CONST_ONE:
                    accumulator = 1f;
                    break;
                case CONST_RANDOM:
                    accumulator = (float) Math.random();
                    break;
                case TEND_TO: {
                    float target = getOperandValue(op, x, y, sensorProvider);
                    accumulator = accumulator + (target - accumulator) * 0.1f;
                    break;
                }
                case BOUND_0_1:
                    accumulator = Math.min(1f, Math.max(0f, accumulator));
                    break;
                case BOUND_NEG1_1:
                    accumulator = Math.min(1f, Math.max(-1f, accumulator));
                    break;
                case DECAY:
                    accumulator *= 0.99f;
                    break;
                case COPY_REGISTER: {
                    float src = getRegister(x, y, op.registerIndex);
                    neurons[x][y][op.registerIndex] = src;
                    break;
                }
                case IF_EQ: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (accumulator != val) skipNext = true;
                    break;
                }
                case IF_NE: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (accumulator == val) skipNext = true;
                    break;
                }
                case IF_GT: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (!(accumulator > val)) skipNext = true;
                    break;
                }
                case IF_LT: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (!(accumulator < val)) skipNext = true;
                    break;
                }
                case IF_GE: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (!(accumulator >= val)) skipNext = true;
                    break;
                }
                case IF_LE: {
                    float val = getOperandValue(op, x, y, sensorProvider);
                    if (!(accumulator <= val)) skipNext = true;
                    break;
                }
                case GOTO:
                    pc = op.labelIndex;
                    break;
                case LABEL:
                    break;
                default:
                    break;
            }
        }
    }

    private float getOperandValue(SVRule.Operation op, int x, int y, SensorProvider sensorProvider) {
        switch (op.operandType) {
            case LITERAL:
                return op.literalValue;
            case REGISTER:
                return neurons[x][y][op.registerIndex];
            case SENSOR_INPUT:
                if (sensorProvider != null) {
                    return sensorProvider.getSensorValue(op.sensorName);
                }
                return 0f;
            default:
                return 0f;
        }
    }
}