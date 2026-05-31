package com.economymod.entity;

import com.economymod.creatures.*;
import com.economymod.creatures.biochemistry.*;
import com.economymod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class TestCreatureEntity extends Mob implements SensorProvider {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private int hunger = 50;
    private int fatigue = 50;
    private int socialCooldown = 0;
    private int lastMoveTick = 0;
    private BlockPos lastPos = null;

    private int matingCooldown = 0;
    private UUID partnerUUID = null;
    private int pregnancyTicks = 0;
    private static final int MATING_COOLDOWN_MAX = 24000;
    private static final int PREGNANCY_DURATION = 12000;

    private CreatureBrain brain;
    private BiochemistrySystem biochemistry;
    private LearningSystem learningSystem;
    private Genome genome;

    private Item lastSeenFoodItem = null;
    private int lastSeenFoodCooldown = 0;

    // Оптимизация
    private int brainTickCounter = 0;
    private int sensorTickCounter = 0;
    private static final int BRAIN_TICK_INTERVAL = 4;
    private static final int SENSOR_TICK_INTERVAL = 10;
    private float cachedHostileNearby = 0f;
    private float cachedFoodNearby = 0f;
    private float cachedSocialNearby = 0f;

    // Подкрепление
    private float lastReinforcement = 0f;
    private int reinforcementCooldown = 0;

    public TestCreatureEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        if (!level.isClientSide) {
            buildBiochemistry();
            buildBrain();
            this.learningSystem = new LearningSystem(this);
            this.genome = new Genome();
        }
    }

    private void buildBiochemistry() {
        biochemistry = new BiochemistrySystem();
        Chemical glucose = new Chemical("Glucose", 0.5f, 200);
        Chemical atp = new Chemical("ATP", 0.8f, 100);
        Chemical hungerChem = new Chemical("Hunger", 0.0f, 300);
        Chemical fatigueChem = new Chemical("Fatigue", 0.0f, 300);
        Chemical adrenaline = new Chemical("Adrenaline", 0.0f, 40);
        Chemical dopamine = new Chemical("Dopamine", 0.0f, 80);
        Chemical serotonin = new Chemical("Serotonin", 0.0f, 100);
        Chemical cortisol = new Chemical("Cortisol", 0.0f, 150);
        Chemical oxytocin = new Chemical("Oxytocin", 0.0f, 200);
        Chemical testosterone = new Chemical("Testosterone", 0.2f, 400);

        biochemistry.addChemical(glucose);
        biochemistry.addChemical(atp);
        biochemistry.addChemical(hungerChem);
        biochemistry.addChemical(fatigueChem);
        biochemistry.addChemical(adrenaline);
        biochemistry.addChemical(dopamine);
        biochemistry.addChemical(serotonin);
        biochemistry.addChemical(cortisol);
        biochemistry.addChemical(oxytocin);
        biochemistry.addChemical(testosterone);

        biochemistry.addEmitter(new Emitter(hungerChem, 0.02f, x -> x, () -> getSensorValue("Hunger")));
        biochemistry.addEmitter(new Emitter(fatigueChem, 0.02f, x -> x, () -> getSensorValue("Fatigue")));
        biochemistry.addEmitter(new Emitter(adrenaline, 0.5f, x -> x, () -> getSensorValue("HostileNearby")));
        biochemistry.addEmitter(new Emitter(oxytocin, 0.3f, x -> x, () -> getSensorValue("SocialNearby")));
        biochemistry.addEmitter(new Emitter(dopamine, 0.2f, x -> x, () -> getDesireValue("FindFood")));
        biochemistry.addEmitter(new Emitter(testosterone, 0.01f, x -> x, () -> 0.5f));

        biochemistry.addReaction(new Reaction(glucose, null, atp, 0.01f));
        biochemistry.addReaction(new Reaction(dopamine, null, serotonin, 0.005f));
        biochemistry.addReaction(new Reaction(adrenaline, null, cortisol, 0.01f));
    }

    public float getChemicalLevel(String name) {
        Chemical c = biochemistry.getChemical(name);
        return c == null ? 0f : c.getConcentration();
    }

    public void modifyChemical(String name, float delta) {
        Chemical c = biochemistry.getChemical(name);
        if (c != null) c.modifyConcentration(delta);
    }

    private float getDesireValue(String desireName) {
        if (brain == null) return 0f;
        BrainLobe desireLobe = brain.getLobe(1);
        if (desireLobe == null) return 0f;
        if ("FindFood".equals(desireName)) return desireLobe.getRegister(5, 0, 0);
        return 0f;
    }

    private void buildBrain() {
        brain = new CreatureBrain();
        SVRule emptyRule = new SVRule();
        BrainLobe sensorLobe = new BrainLobe(11, 1, 1, emptyRule);
        BrainLobe desireLobe = new BrainLobe(9, 1, 1, emptyRule);
        brain.addLobe(sensorLobe);
        brain.addLobe(desireLobe);

        addTract(sensorLobe, desireLobe, 0,0,0,0, 0,0,0,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 1,0,1,0, 1,0,1,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 4,0,4,0, 2,0,2,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 6,0,6,0, 3,0,3,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 7,0,7,0, 4,0,4,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 5,0,5,0, 5,0,5,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 10,0,10,0, 5,0,5,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 8,0,8,0, 6,0,6,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 7,0,7,0, 7,0,7,0, DendriteSVRule.identity());
        addTract(sensorLobe, desireLobe, 2,0,2,0, 8,0,8,0, DendriteSVRule.identity());
    }

    private void addTract(BrainLobe src, BrainLobe dst, int sx, int sy, int sx2, int sy2, int dx, int dy, int dx2, int dy2, DendriteSVRule rule) {
        brain.addTract(new BrainTract(src, dst, sx, sy, sx2, sy2, dx, dy, dx2, dy2, 0, 0, false, rule));
    }

    private void applyReinforcement(float reward) {
        if (brain == null) return;
        float learningRate = genome.learningRate;
        brain.reinforce(reward, learningRate);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FleeMonsterGoal());
        this.goalSelector.addGoal(2, new FightMonsterGoal());
        this.goalSelector.addGoal(3, new MoveToItemGoal());
        this.goalSelector.addGoal(4, new MateGoal());
        this.goalSelector.addGoal(5, new BehaviorGoal());
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    class FleeMonsterGoal extends Goal {
        @Override
        public boolean canUse() {
            BrainLobe desireLobe = brain.getLobe(1);
            if (desireLobe == null) return false;
            float desireRun = desireLobe.getRegister(2, 0, 0);
            return desireRun > 0.3f;
        }

        @Override
        public void start() {
            Monster nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (Monster m : level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(16), e -> e.isAlive())) {
                double d = distanceToSqr(m);
                if (d < bestDist) { bestDist = d; nearest = m; }
            }
            if (nearest != null) {
                double dx = getX() - nearest.getX();
                double dz = getZ() - nearest.getZ();
                double dist = Math.sqrt(dx*dx + dz*dz);
                if (dist > 0.01) { dx /= dist; dz /= dist; }
                BlockPos runTo = BlockPos.containing(getX() + dx*15, getY(), getZ() + dz*15);
                navigation.moveTo(runTo.getX(), runTo.getY(), runTo.getZ(), 1.2);
                applyReinforcement(0.05f);
            }
        }

        @Override
        public boolean canContinueToUse() { return !navigation.isDone(); }
    }

    class FightMonsterGoal extends Goal {
        private Monster target;
        @Override
        public boolean canUse() {
            BrainLobe desireLobe = brain.getLobe(1);
            if (desireLobe == null) return false;
            float desireFight = desireLobe.getRegister(6, 0, 0);
            float threshold = 0.5f * (1f - genome.aggression);
            if (desireFight < threshold) return false;
            List<Monster> monsters = level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(8), e -> e.isAlive());
            if (!monsters.isEmpty()) {
                target = monsters.get(0);
                return true;
            }
            return false;
        }
        @Override
        public void start() { navigation.moveTo(target, 1.0); }
        @Override
        public void tick() {
            if (target != null && distanceToSqr(target) < 4.0) {
                doHurtTarget(target);
                target.hurt(level().damageSources().mobAttack(TestCreatureEntity.this), 2.0f);
            }
        }
        @Override
        public boolean canContinueToUse() { return target != null && target.isAlive() && !navigation.isDone(); }
    }

    class MoveToItemGoal extends Goal {
        private ItemEntity targetItem;
        @Override
        public boolean canUse() {
            if (hunger < 50) return false;
            List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(10), e -> e.isAlive() && isFoodItem(e.getItem()));
            if (!items.isEmpty()) {
                targetItem = items.get(0);
                lastSeenFoodItem = targetItem.getItem().getItem();
                lastSeenFoodCooldown = 100;
                return true;
            }
            return false;
        }
        @Override
        public void start() { if (targetItem != null) navigation.moveTo(targetItem, 1.0); }
        @Override
        public void tick() {
            if (targetItem != null && targetItem.isAlive() && distanceToSqr(targetItem) < 2.0) {
                ItemStack stack = targetItem.getItem();
                if (isFoodItem(stack)) {
                    hunger = Math.max(0, hunger - 20);
                    modifyChemical("Glucose", 0.1f);
                    modifyChemical("Dopamine", 0.05f);
                    if (learningSystem != null) {
                        learningSystem.learnItemFear(stack.getItem(), 0f);
                        learningSystem.learnPositive(learningSystem.getCurrentContext(), 0.2f);
                    }
                    applyReinforcement(0.1f);
                    targetItem.discard();
                    level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                }
                targetItem = null;
            }
        }
        @Override
        public boolean canContinueToUse() { return targetItem != null && targetItem.isAlive() && !navigation.isDone(); }
    }

    class MateGoal extends Goal {
        private TestCreatureEntity partner;
        @Override
        public boolean canUse() {
            if (matingCooldown > 0) return false;
            BrainLobe desireLobe = brain.getLobe(1);
            if (desireLobe == null) return false;
            float desireMate = desireLobe.getRegister(7, 0, 0);
            float threshold = 0.6f * (1f - genome.socialNeed);
            if (desireMate < threshold) return false;
            List<TestCreatureEntity> candidates = level().getEntitiesOfClass(TestCreatureEntity.class, getBoundingBox().inflate(8), e -> e != TestCreatureEntity.this && e.matingCooldown == 0);
            if (!candidates.isEmpty()) {
                partner = candidates.get(0);
                return true;
            }
            return false;
        }
        @Override
        public void start() { navigation.moveTo(partner, 0.8); }
        @Override
        public void tick() {
            if (partner != null && distanceToSqr(partner) < 3.0) {
                partnerUUID = partner.getUUID();
                pregnancyTicks = PREGNANCY_DURATION;
                matingCooldown = MATING_COOLDOWN_MAX;
                partner.matingCooldown = MATING_COOLDOWN_MAX;
                modifyChemical("Oxytocin", 0.2f);
                partner.modifyChemical("Oxytocin", 0.2f);

                if (learningSystem != null) {
                    learningSystem.learnPositive(learningSystem.getCurrentContext(), 0.3f);
                }
                if (partner.learningSystem != null) {
                    partner.learningSystem.learnPositive(partner.learningSystem.getCurrentContext(), 0.3f);
                }

                if (learningSystem != null && partner.learningSystem != null) {
                    learningSystem.mergeFrom(partner.learningSystem, 0.5f);
                    partner.learningSystem.mergeFrom(learningSystem, 0.5f);
                }

                applyReinforcement(0.15f);
                partner.applyReinforcement(0.15f);
                partner = null;
            }
        }
        @Override
        public boolean canContinueToUse() { return partner != null && partner.isAlive() && !navigation.isDone(); }
    }

    class BehaviorGoal extends Goal {
        private int idleTicks = 0;
        @Override
        public boolean canUse() { return true; }
        @Override
        public void tick() {
            if (brain == null) return;
            BrainLobe desireLobe = brain.getLobe(1);
            if (desireLobe == null) return;

            float desireEat = desireLobe.getRegister(0, 0, 0);
            float desireSleep = desireLobe.getRegister(1, 0, 0);
            float desireRunAway = desireLobe.getRegister(2, 0, 0);
            float desireSocialize = desireLobe.getRegister(4, 0, 0);
            float desireExplore = desireLobe.getRegister(3, 0, 0);
            float desireFindFood = desireLobe.getRegister(5, 0, 0);
            float desireFight = desireLobe.getRegister(6, 0, 0);
            float desireMate = desireLobe.getRegister(7, 0, 0);
            float desireProtect = desireLobe.getRegister(8, 0, 0);

            desireExplore *= genome.basalCuriosity;
            desireFight *= (0.5f + genome.aggression);
            desireSocialize *= (0.5f + genome.socialNeed);

            LearningSystem.MemoryType primaryFear;
            float healthPercent = getHealth() / getMaxHealth();
            if (healthPercent < 0.6f) primaryFear = LearningSystem.MemoryType.DAMAGE;
            else if (hunger > 70f) primaryFear = LearningSystem.MemoryType.HUNGER;
            else if (fatigue > 70f) primaryFear = LearningSystem.MemoryType.FATIGUE;
            else primaryFear = LearningSystem.MemoryType.BOREDOM;

            if (learningSystem != null) {
                Item targetItem = (lastSeenFoodItem != null && lastSeenFoodCooldown > 0) ? lastSeenFoodItem : null;
                desireExplore = learningSystem.modifyDesire("Explore", desireExplore, null, primaryFear);
                desireFindFood = learningSystem.modifyDesire("FindFood", desireFindFood, targetItem, primaryFear);
                desireSocialize = learningSystem.modifyDesire("Socialize", desireSocialize, null, primaryFear);
                String context = learningSystem.getCurrentContext();
                desireExplore = learningSystem.modifyDesireWithPositive("Explore", desireExplore, context);
            }
            if (lastSeenFoodCooldown > 0) lastSeenFoodCooldown--;

            if (desireRunAway > 0.3f) return;
            if (desireFight > 0.5f) return;
            if (desireMate > 0.6f) return;

            if (desireEat > 0.7f && hunger > 0) {
                hunger = Math.max(0, hunger - 2);
                modifyChemical("Glucose", 0.05f);
                setHealth(Math.min(getMaxHealth(), getHealth() + 1.0f));
            }

            if (desireSleep > 0.7f) {
                fatigue = Math.max(0, fatigue - 2);
                navigation.stop();
                return;
            }

            if (desireFindFood > 0.5f && hunger > 50) {
                ItemEntity nearestFood = null;
                double bestDist = Double.MAX_VALUE;
                for (ItemEntity ie : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(16), e -> e.isAlive() && isFoodItem(e.getItem()))) {
                    double d = distanceToSqr(ie);
                    if (d < bestDist) { bestDist = d; nearestFood = ie; }
                }
                if (nearestFood != null) {
                    lastSeenFoodItem = nearestFood.getItem().getItem();
                    lastSeenFoodCooldown = 100;
                    navigation.moveTo(nearestFood, 1.0);
                    return;
                }
            }

            if (desireSocialize > 0.5f && socialCooldown == 0) {
                socialCooldown = 100;
                var nearby = level().getEntitiesOfClass(TestCreatureEntity.class, getBoundingBox().inflate(10), e -> e != TestCreatureEntity.this);
                if (!nearby.isEmpty()) {
                    navigation.moveTo(nearby.get(0), 0.8);
                    return;
                }
            }
            if (socialCooldown > 0) socialCooldown--;

            if (navigation.isDone() && random.nextInt(40) == 0) {
                BlockPos pos = blockPosition();
                BlockPos target = pos.offset(random.nextInt(21)-10, 0, random.nextInt(21)-10);
                navigation.moveTo(target.getX(), target.getY(), target.getZ(), 0.6);
                return;
            }

            if (learningSystem != null) {
                float boredom = 1f - learningSystem.getNovelty();
                if (boredom > 0.7f && random.nextInt(60) == 0) {
                    getJumpControl().jump();
                    level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.0f);
                }
            }

            if (!navigation.isInProgress()) {
                idleTicks++;
                if (idleTicks > 60) {
                    BlockPos pos = blockPosition();
                    int dx, dz;
                    do { dx = random.nextInt(21)-10; dz = random.nextInt(21)-10; } while (dx == 0 && dz == 0);
                    navigation.moveTo(pos.getX()+dx, pos.getY(), pos.getZ()+dz, 0.5);
                    idleTicks = 0;
                }
            } else {
                idleTicks = 0;
            }
        }
    }

    private void updateCachedSensors() {
        cachedHostileNearby = computeHostileNearby();
        cachedFoodNearby = computeFoodNearby();
        cachedSocialNearby = computeSocialNearby();
    }

    private float computeHostileNearby() {
        List<Monster> monsters = level().getEntitiesOfClass(Monster.class, getBoundingBox().inflate(16), e -> e.isAlive());
        return monsters.isEmpty() ? 0f : Math.min(1f, monsters.size() / 5f);
    }

    private float computeFoodNearby() {
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8), e -> e.isAlive() && isFoodItem(e.getItem()));
        return items.isEmpty() ? 0f : Math.min(1f, items.size() / 3f);
    }

    private float computeSocialNearby() {
        List<TestCreatureEntity> creatures = level().getEntitiesOfClass(TestCreatureEntity.class, getBoundingBox().inflate(10), e -> e != this && e.isAlive());
        return creatures.isEmpty() ? 0f : Math.min(1f, creatures.size() / 3f);
    }

    @Override
    public float getSensorValue(String sensorName) {
        switch (sensorName) {
            case "Hunger": return hunger / 100.0f;
            case "Fatigue": return fatigue / 100.0f;
            case "Health": return getHealth() / getMaxHealth();
            case "Light": return level().getBrightness(LightLayer.BLOCK, blockPosition()) / 15.0f;
            case "HostileNearby": return cachedHostileNearby;
            case "FoodNearby": return cachedFoodNearby;
            case "Curiosity": {
                float base = (tickCount - lastMoveTick < 100) ? 0.7f : 0.3f;
                return base * genome.basalCuriosity;
            }
            case "SocialNearby": return cachedSocialNearby;
            case "Temperature": {
                Biome biome = level().getBiome(blockPosition()).value();
                float temp = (biome.getBaseTemperature() - 0.2f) / 1.0f;
                return Math.min(1f, Math.max(0f, temp));
            }
            case "TimeOfDay": {
                long time = level().getDayTime() % 24000;
                return time > 12000 ? (time - 12000) / 12000f : 0f;
            }
            case "Glucose": return getChemicalLevel("Glucose");
            case "PlayerNearby": {
                boolean playerNear = !level().players().isEmpty() &&
                        level().players().stream().anyMatch(p -> p.distanceToSqr(this) < 64);
                return playerNear ? 1f : 0f;
            }
            default: return 0f;
        }
    }

    private boolean isFoodItem(ItemStack stack) {
        return stack.is(Items.BREAD) || stack.is(Items.APPLE) || stack.is(Items.WHEAT) ||
                stack.is(Items.COOKED_BEEF) || stack.is(Items.CARROT) || stack.is(Items.POTATO);
    }

    private void updateLearning() {
        if (learningSystem == null) return;
        float distress = learningSystem.calculateDistress(
                getHealth() / getMaxHealth(),
                hunger / 100f,
                fatigue / 100f
        );
        if (distress > 0.6f && tickCount % 20 == 0) {
            String context = learningSystem.getCurrentContext();
            float health = getHealth() / getMaxHealth();
            LearningSystem.MemoryType type;
            if (health < 0.5f) type = LearningSystem.MemoryType.DAMAGE;
            else if (hunger > 70f) type = LearningSystem.MemoryType.HUNGER;
            else if (fatigue > 70f) type = LearningSystem.MemoryType.FATIGUE;
            else type = LearningSystem.MemoryType.BOREDOM;
            float intensity = distress * genome.fearProne;
            learningSystem.learnNegative(type, context, intensity);
        }
        if (lastSeenFoodItem != null && lastSeenFoodCooldown > 0 && getHealth() < getMaxHealth() && tickCount % 10 == 0) {
            learningSystem.learnItemFear(lastSeenFoodItem, 0.3f);
        }
        if (getSensorValue("HostileNearby") > 0.2f && tickCount % 40 == 0) {
            String context = learningSystem.getCurrentContext();
            learningSystem.learnNegative(LearningSystem.MemoryType.MOB, context, 0.5f);
        }
        if (getSensorValue("PlayerNearby") > 0.5f && getHealth() < getMaxHealth() - 0.1f && tickCount % 40 == 0) {
            String context = learningSystem.getCurrentContext();
            learningSystem.learnNegative(LearningSystem.MemoryType.PLAYER, context, 0.6f);
        }
        learningSystem.tickForgetting();
    }

    private void updatePhysiology() {
        if (navigation.isInProgress()) modifyChemical("ATP", -0.002f * genome.energyEfficiency);
        modifyChemical("ATP", -0.001f);
        float glucose = getChemicalLevel("Glucose");
        if (glucose < 0.2f) hunger = Math.min(100, hunger + (int)(1 * genome.metabolismRate));
        else hunger = Math.max(0, hunger - (int)(1 * genome.metabolismRate));
        if (navigation.isInProgress()) fatigue = Math.min(100, fatigue + 1);
        else fatigue = Math.max(0, fatigue - 1);
        if (glucose > 0.3f && getHealth() < getMaxHealth() && tickCount % 40 == 0) {
            setHealth(Math.min(getMaxHealth(), getHealth() + 0.5f));
            modifyChemical("Glucose", -0.01f);
        }
        if (matingCooldown > 0) matingCooldown--;
        if (pregnancyTicks > 0) {
            pregnancyTicks--;
            if (pregnancyTicks == 0) giveBirth();
        }
    }

    private void giveBirth() {
        if (level() instanceof ServerLevel serverLevel) {
            TestCreatureEntity baby = ModEntities.TEST_CREATURE.get().create(serverLevel);
            if (baby != null) {
                baby.setPos(getX(), getY(), getZ());
                if (learningSystem != null && baby.learningSystem != null) {
                    baby.learningSystem.inheritFrom(learningSystem, 0.7f);
                }
                baby.genome = new Genome(this.genome, new Genome());
                serverLevel.addFreshEntity(baby);
                LOGGER.info("New creature born!");
            }
        }
        pregnancyTicks = 0;
    }

    private void updateCuriositySensor() {
        BlockPos currentPos = blockPosition();
        if (lastPos != null && !lastPos.equals(currentPos)) lastMoveTick = tickCount;
        lastPos = currentPos;
    }

    private void updateSensorsInBrain() {
        if (brain == null) return;
        BrainLobe sensorLobe = brain.getLobe(0);
        if (sensorLobe == null) return;
        sensorLobe.setRegister(0, 0, 0, getSensorValue("Hunger"));
        sensorLobe.setRegister(1, 0, 0, getSensorValue("Fatigue"));
        sensorLobe.setRegister(2, 0, 0, getSensorValue("Health"));
        sensorLobe.setRegister(3, 0, 0, getSensorValue("Light"));
        sensorLobe.setRegister(4, 0, 0, getSensorValue("HostileNearby"));
        sensorLobe.setRegister(5, 0, 0, getSensorValue("FoodNearby"));
        sensorLobe.setRegister(6, 0, 0, getSensorValue("Curiosity"));
        sensorLobe.setRegister(7, 0, 0, getSensorValue("SocialNearby"));
        sensorLobe.setRegister(8, 0, 0, getSensorValue("Temperature"));
        sensorLobe.setRegister(9, 0, 0, getSensorValue("TimeOfDay"));
        sensorLobe.setRegister(10, 0, 0, getSensorValue("Glucose"));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            updatePhysiology();
            if (++sensorTickCounter >= SENSOR_TICK_INTERVAL) {
                sensorTickCounter = 0;
                updateCachedSensors();
                updateSensorsInBrain();
            }
            if (++brainTickCounter >= BRAIN_TICK_INTERVAL) {
                brainTickCounter = 0;
                if (brain != null) brain.tick(this);
            }
            updateLearning();
            updateCuriositySensor();
            if (learningSystem != null) learningSystem.updateNovelty(blockPosition(), lastPos);
            if (biochemistry != null) biochemistry.tick(this);
            if (tickCount % 200 == 0 && brain != null && brain.getLobe(1) != null) {
                BrainLobe desire = brain.getLobe(1);
                String desiresStr = String.format(
                        "Eat=%.2f Sleep=%.2f Run=%.2f Explore=%.2f Social=%.2f FindFood=%.2f Fight=%.2f Mate=%.2f Protect=%.2f",
                        desire.getRegister(0,0,0), desire.getRegister(1,0,0), desire.getRegister(2,0,0),
                        desire.getRegister(3,0,0), desire.getRegister(4,0,0), desire.getRegister(5,0,0),
                        desire.getRegister(6,0,0), desire.getRegister(7,0,0), desire.getRegister(8,0,0));
                LOGGER.info("Desires: {}", desiresStr);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasHurt = super.hurt(source, amount);
        if (wasHurt && source.getEntity() instanceof Player) {
            applyReinforcement(-0.1f);
        }
        return wasHurt;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, net.minecraft.world.damagesource.DamageSource source) {
        boolean wasHurt = super.causeFallDamage(fallDistance, damageMultiplier, source);
        if (fallDistance > 2f) {
            applyReinforcement(-0.05f);
        }
        return wasHurt;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Hunger", hunger);
        tag.putInt("Fatigue", fatigue);
        tag.putInt("MatingCooldown", matingCooldown);
        if (partnerUUID != null) tag.putUUID("PartnerUUID", partnerUUID);
        tag.putInt("PregnancyTicks", pregnancyTicks);
        if (biochemistry != null) tag.put("Biochemistry", biochemistry.save());
        if (learningSystem != null) tag.put("LearningSystem", learningSystem.save());
        if (genome != null) tag.put("Genome", genome.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hunger = tag.getInt("Hunger");
        fatigue = tag.getInt("Fatigue");
        matingCooldown = tag.getInt("MatingCooldown");
        if (tag.contains("PartnerUUID")) partnerUUID = tag.getUUID("PartnerUUID");
        pregnancyTicks = tag.getInt("PregnancyTicks");
        if (biochemistry != null && tag.contains("Biochemistry")) biochemistry.load(tag.getCompound("Biochemistry"));
        if (learningSystem != null && tag.contains("LearningSystem")) learningSystem.load(tag.getCompound("LearningSystem"));
        if (tag.contains("Genome")) {
            genome = new Genome();
            genome.load(tag.getCompound("Genome"));
        } else {
            genome = new Genome();
        }
    }

    public BlockPos getLastPos() { return lastPos; }
    public LearningSystem getLearningSystem() { return learningSystem; }
    public CreatureBrain getCreatureBrain() { return brain; }
}