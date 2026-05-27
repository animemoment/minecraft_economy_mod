package com.economymod.creatures;

import java.util.ArrayList;
import java.util.List;

public class DendriteSVRule {
    private final List<DendriteOperation> operations = new ArrayList<>();

    public void addOperation(DendriteOperation op) {
        operations.add(op);
    }

    public List<DendriteOperation> getOperations() {
        return operations;
    }

    public float execute(float sourceValue) {
        float accumulator = sourceValue;
        for (DendriteOperation op : operations) {
            switch (op.opcode) {
                case LOAD:
                    accumulator = getOperandValue(op, sourceValue);
                    break;
                case ADD:
                    accumulator += getOperandValue(op, sourceValue);
                    break;
                case SUB:
                    accumulator -= getOperandValue(op, sourceValue);
                    break;
                case MUL:
                    accumulator *= getOperandValue(op, sourceValue);
                    break;
                case DIV: {
                    float val = getOperandValue(op, sourceValue);
                    if (val != 0) accumulator /= val;
                    break;
                }
                case NEG:
                    accumulator = -accumulator;
                    break;
                case CONST_ZERO:
                    accumulator = 0f;
                    break;
                case CONST_ONE:
                    accumulator = 1f;
                    break;
                case CONST_RANDOM:
                    accumulator = (float) Math.random();
                    break;
                case BOUND_0_1:
                    accumulator = Math.min(1f, Math.max(0f, accumulator));
                    break;
                case BOUND_NEG1_1:
                    accumulator = Math.min(1f, Math.max(-1f, accumulator));
                    break;
                case TEND_TO: {
                    float target = getOperandValue(op, sourceValue);
                    accumulator = accumulator + (target - accumulator) * 0.1f;
                    break;
                }
                case IDENTITY:
                default:
                    break;
            }
        }
        return accumulator;
    }

    private float getOperandValue(DendriteOperation op, float sourceValue) {
        switch (op.operandType) {
            case LITERAL:
                return op.literalValue;
            case SOURCE_VALUE:
                return sourceValue;
            default:
                return 0f;
        }
    }

    public static class DendriteOperation {
        public enum Opcode {
            LOAD, ADD, SUB, MUL, DIV, NEG,
            CONST_ZERO, CONST_ONE, CONST_RANDOM,
            BOUND_0_1, BOUND_NEG1_1, TEND_TO, IDENTITY
        }
        public enum OperandType { LITERAL, SOURCE_VALUE }

        public final Opcode opcode;
        public final OperandType operandType;
        public final float literalValue;

        public DendriteOperation(Opcode opcode) {
            this(opcode, OperandType.LITERAL, 0f);
        }
        public DendriteOperation(Opcode opcode, float literal) {
            this(opcode, OperandType.LITERAL, literal);
        }
        public DendriteOperation(Opcode opcode, OperandType type, float literal) {
            this.opcode = opcode;
            this.operandType = type;
            this.literalValue = literal;
        }
    }

    public static DendriteSVRule identity() {
        DendriteSVRule rule = new DendriteSVRule();
        rule.addOperation(new DendriteOperation(DendriteOperation.Opcode.IDENTITY));
        return rule;
    }
}