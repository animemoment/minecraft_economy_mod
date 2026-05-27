package com.economymod.creatures;

import java.util.ArrayList;
import java.util.List;

public class SVRule {
    public enum Opcode {
        ADD, SUB, MUL, DIV,
        LOAD, STORE,
        CONST_ZERO, CONST_ONE, CONST_RANDOM,
        TEND_TO, BOUND_0_1, BOUND_NEG1_1,
        IF_EQ, IF_NE, IF_GT, IF_LT, IF_GE, IF_LE,
        GOTO,
        DECAY, COPY_REGISTER,
        LABEL
    }

    public enum OperandType {
        LITERAL, REGISTER, SENSOR_INPUT, CHEMICAL
    }

    public static class Operation {
        public final Opcode opcode;
        public final OperandType operandType;
        public final float literalValue;
        public final int registerIndex;
        public final String sensorName;
        public final int labelIndex;

        public Operation(Opcode opcode, float literal) {
            this.opcode = opcode;
            this.operandType = OperandType.LITERAL;
            this.literalValue = literal;
            this.registerIndex = -1;
            this.sensorName = null;
            this.labelIndex = -1;
        }

        public Operation(Opcode opcode, int register) {
            this.opcode = opcode;
            this.operandType = OperandType.REGISTER;
            this.literalValue = 0;
            this.registerIndex = register;
            this.sensorName = null;
            this.labelIndex = -1;
        }

        public Operation(Opcode opcode, String sensor) {
            this.opcode = opcode;
            this.operandType = OperandType.SENSOR_INPUT;
            this.literalValue = 0;
            this.registerIndex = -1;
            this.sensorName = sensor;
            this.labelIndex = -1;
        }

        public Operation(Opcode opcode, int labelIndex, boolean isLabel) {
            this.opcode = opcode;
            this.operandType = OperandType.LITERAL;
            this.literalValue = 0;
            this.registerIndex = -1;
            this.sensorName = null;
            this.labelIndex = labelIndex;
        }
    }

    private final List<Operation> operations = new ArrayList<>();

    public void addOperation(Operation op) {
        operations.add(op);
    }

    public List<Operation> getOperations() {
        return operations;
    }
}