package com.economymod.creatures;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.UUID;

public class RuleCompiler implements Opcodes {

    private static final DynamicClassLoader CLASS_LOADER = new DynamicClassLoader(RuleCompiler.class.getClassLoader());

    public static CompiledRule compile(SVRule rule) throws Exception {
        String className = "com/economymod/creatures/CompiledRule_Dynamic_" + UUID.randomUUID().toString().replace("-", "_");
        byte[] classBytes = generateBytecode(className, rule);

        Class<?> compiledClass = CLASS_LOADER.defineClass(className.replace('/', '.'), classBytes);
        return (CompiledRule) compiledClass.getDeclaredConstructor().newInstance();
    }

    private static byte[] generateBytecode(String className, SVRule rule) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Объявляем класс, реализующий CompiledRule
        cw.visit(V17, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", new String[]{"com/economymod/creatures/CompiledRule"});

        // Генерация конструктора по умолчанию ()V
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        // ИСПРАВЛЕНО: Добавлены L и ; вокруг пути класса в дескрипторе метода!
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "execute", "([[[FIILcom/economymod/creatures/SensorProvider;)V", null, null);
        mv.visitCode();

        // Инициализируем аккумулятор нулем (acc = 0.0f)
        mv.visitInsn(FCONST_0);
        mv.visitVarInsn(FSTORE, 5);

        List<SVRule.Operation> ops = rule.getOperations();
        int size = ops.size();

        // Заготавливаем ASM-метки для каждой операции
        Label[] labels = new Label[size + 2];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = new Label();
        }
        Label exitLabel = labels[size];

        for (int k = 0; k < size; k++) {
            mv.visitLabel(labels[k]);
            SVRule.Operation op = ops.get(k);

            switch (op.opcode) {
                case LOAD:
                    pushOperand(mv, op);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case STORE:
                    if (op.operandType == SVRule.OperandType.REGISTER) {
                        storeRegister(mv, op.registerIndex);
                    }
                    break;

                case ADD:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FADD);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case SUB:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FSUB);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case MUL:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FMUL);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case DIV:
                    pushOperand(mv, op);
                    mv.visitInsn(DUP);
                    mv.visitInsn(FCONST_0);
                    mv.visitInsn(FCMPL);
                    Label skipDiv = new Label();
                    mv.visitJumpInsn(IFEQ, skipDiv);

                    mv.visitVarInsn(FLOAD, 5);
                    mv.visitInsn(SWAP);
                    mv.visitInsn(FDIV);
                    mv.visitVarInsn(FSTORE, 5);

                    mv.visitLabel(skipDiv);
                    break;

                case CONST_ZERO:
                    mv.visitInsn(FCONST_0);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case CONST_ONE:
                    mv.visitInsn(FCONST_1);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case CONST_RANDOM:
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "random", "()D", false);
                    mv.visitInsn(D2F);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case TEND_TO:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitVarInsn(FLOAD, 5);
                    mv.visitInsn(FSUB);
                    mv.visitLdcInsn(0.1f);
                    mv.visitInsn(FMUL);
                    mv.visitInsn(FADD);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case BOUND_0_1:
                    mv.visitVarInsn(FLOAD, 5);
                    mv.visitInsn(FCONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
                    mv.visitInsn(FCONST_1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case BOUND_NEG1_1:
                    mv.visitVarInsn(FLOAD, 5);
                    mv.visitLdcInsn(-1.0f);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
                    mv.visitInsn(FCONST_1);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case DECAY:
                    mv.visitVarInsn(FLOAD, 5);
                    mv.visitLdcInsn(0.99f);
                    mv.visitInsn(FMUL);
                    mv.visitVarInsn(FSTORE, 5);
                    break;

                case COPY_REGISTER:
                    loadRegister(mv, op.registerIndex);
                    storeRegister(mv, op.registerIndex);
                    break;

                case GOTO:
                    mv.visitJumpInsn(GOTO, labels[op.labelIndex]);
                    break;

                case IF_EQ:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FCMPL);
                    mv.visitJumpInsn(IFNE, labels[k + 2]);
                    break;

                case IF_NE:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FCMPL);
                    mv.visitJumpInsn(IFEQ, labels[k + 2]);
                    break;

                case IF_GT:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FCMPL);
                    mv.visitJumpInsn(IFLE, labels[k + 2]);
                    break;

                case IF_LT:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FCMPG);
                    mv.visitJumpInsn(IFGE, labels[k + 2]);
                    break;

                case IF_GE:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FCMPL);
                    mv.visitJumpInsn(IFLT, labels[k + 2]);
                    break;

                case IF_LE:
                    mv.visitVarInsn(FLOAD, 5);
                    pushOperand(mv, op);
                    mv.visitInsn(FCMPG);
                    mv.visitJumpInsn(IFGT, labels[k + 2]);
                    break;

                case LABEL:
                    break;
            }
        }

        mv.visitLabel(exitLabel);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void loadRegister(MethodVisitor mv, int reg) {
        mv.visitVarInsn(ALOAD, 1); // neurons
        mv.visitVarInsn(ILOAD, 2); // x
        mv.visitInsn(AALOAD);
        mv.visitVarInsn(ILOAD, 3); // y
        mv.visitInsn(AALOAD);
        mv.visitLdcInsn(reg);
        mv.visitInsn(FALOAD);
    }

    private static void storeRegister(MethodVisitor mv, int reg) {
        mv.visitVarInsn(ALOAD, 1); // neurons
        mv.visitVarInsn(ILOAD, 2); // x
        mv.visitInsn(AALOAD);
        mv.visitVarInsn(ILOAD, 3); // y
        mv.visitInsn(AALOAD);
        mv.visitLdcInsn(reg);
        mv.visitVarInsn(FLOAD, 5); // acc
        mv.visitInsn(FASTORE);
    }

    private static void pushOperand(MethodVisitor mv, SVRule.Operation op) {
        switch (op.operandType) {
            case LITERAL:
                mv.visitLdcInsn(op.literalValue);
                break;
            case REGISTER:
                loadRegister(mv, op.registerIndex);
                break;
            case SENSOR_INPUT:
                mv.visitVarInsn(ALOAD, 4); // sensors
                mv.visitLdcInsn(op.sensorName);
                // ИСПРАВЛЕНО: Добавлены L и ; в сигнатуру интерфейса
                mv.visitMethodInsn(INVOKEINTERFACE, "com/economymod/creatures/SensorProvider", "getSensorValue", "(Ljava/lang/String;)F", true);
                break;
            default:
                mv.visitInsn(FCONST_0);
        }
    }

    public static class DynamicClassLoader extends ClassLoader {
        public DynamicClassLoader(ClassLoader parent) {
            super(parent);
        }
        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}