package com.economymod.entity;

import com.economymod.neural.NeuralNetwork;
import com.economymod.neural.NeuralConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.EnumSet;

public class TestCreatureEntity extends Mob {

    private NeuralNetwork neuralNetwork;
    private int wanderCooldown = 0;
    private int hunger = 50;
    private int fatigue = 50;
    private int socialCooldown = 0;
    private int lastMoveTick = 0;
    private BlockPos lastPos = null;

    public TestCreatureEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        if (!level.isClientSide) {
            this.neuralNetwork = new NeuralNetwork(this);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new NeuralWanderGoal());
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && neuralNetwork != null && NeuralConfig.ENABLED) {
            updateSensors();
            neuralNetwork.tick();
            applyNeuralOutput();
        }
    }

    private void updateSensors() {
        if (neuralNetwork == null) return;

        neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_HUNGER, hunger / 100.0f);
        neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_FATIGUE, fatigue / 100.0f);
        neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_HEALTH, 1.0f - (getHealth() / getMaxHealth()));

        boolean hasNearby = level().getEntitiesOfClass(TestCreatureEntity.class, getBoundingBox().inflate(8), e -> e != this).size() > 0;
        neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_SOCIAL, hasNearby ? 0.7f : 0.0f);

        boolean hasHostile = !level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, getBoundingBox().inflate(16)).isEmpty();
        neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_HOSTILE, hasHostile ? 1.0f : 0.0f);

        int light = level().getBrightness(LightLayer.BLOCK, blockPosition());
        neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_COMFORT, light / 15.0f);

        BlockPos currentPos = blockPosition();
        if (lastPos != null && !lastPos.equals(currentPos)) {
            neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_CURIOSITY, 0.5f);
            lastMoveTick = tickCount;
        } else if (tickCount - lastMoveTick < 100) {
            neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_CURIOSITY, 0.2f);
        } else {
            neuralNetwork.stimulateSensor(NeuralNetwork.SENSOR_CURIOSITY, 0.0f);
        }
        lastPos = currentPos;
    }

    private void applyNeuralOutput() {
        if (neuralNetwork == null) return;

        float eat = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_EAT);
        float sleep = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_SLEEP);
        float run = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_RUN_AWAY);
        float explore = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_EXPLORE);
        float socialize = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_SOCIALIZE);

        if (eat > 0.7f && hunger > 0) {
            hunger = Math.max(0, hunger - 2);
            setHealth(Math.min(getMaxHealth(), getHealth() + 1.0f));
        }

        if (sleep > 0.7f) {
            fatigue = Math.max(0, fatigue - 2);
        }

        if (socialize > 0.7f && socialCooldown == 0) {
            socialCooldown = 100;
            var nearby = level().getEntitiesOfClass(TestCreatureEntity.class, getBoundingBox().inflate(10), e -> e != this);
            if (!nearby.isEmpty()) {
                navigation.moveTo(nearby.get(0), 0.8);
            }
        }
        if (socialCooldown > 0) socialCooldown--;

        if (tickCount % 20 == 0 && navigation.isInProgress()) {
            fatigue = Math.min(100, fatigue + 1);
        }

        if (tickCount % 60 == 0) {
            hunger = Math.min(100, hunger + 1);
        }
    }

    public NeuralNetwork getNeuralNetwork() { return neuralNetwork; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Hunger", hunger);
        tag.putInt("Fatigue", fatigue);
        if (neuralNetwork != null) {
            tag.put("NeuralNetwork", neuralNetwork.save());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hunger = tag.getInt("Hunger");
        fatigue = tag.getInt("Fatigue");
        if (neuralNetwork != null && tag.contains("NeuralNetwork")) {
            neuralNetwork.load(tag.getCompound("NeuralNetwork"));
        }
    }

    class NeuralWanderGoal extends Goal {
        public NeuralWanderGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (neuralNetwork == null) return false;
            if (wanderCooldown > 0) {
                wanderCooldown--;
                return false;
            }
            float explore = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_EXPLORE);
            float run = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_RUN_AWAY);
            return explore > 0.5f || run > 0.6f;
        }

        @Override
        public void start() {
            BlockPos pos = TestCreatureEntity.this.blockPosition();
            float run = neuralNetwork.getOutputActivation(NeuralNetwork.OUTPUT_RUN_AWAY);
            double speed = (run > 0.6f) ? 1.2f : 0.6f;
            BlockPos target = pos.offset(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
            navigation.moveTo(target.getX(), target.getY(), target.getZ(), speed);
            wanderCooldown = 60;
        }

        @Override
        public boolean canContinueToUse() {
            return !navigation.isDone();
        }
    }
}