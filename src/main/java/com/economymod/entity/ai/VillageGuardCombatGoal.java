package com.economymod.entity.ai;

import com.economymod.entity.VillageGuardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumSet;

public class VillageGuardCombatGoal extends Goal {
    private final VillageGuardEntity guard;
    private LivingEntity target;
    private int attackCooldown = 0;
    private int blockCooldown = 0;
    private int blockDuration = 0;
    private int stuckTimer = 0;
    private BlockPos lastPos = null;

    public VillageGuardCombatGoal(VillageGuardEntity guard) {
        this.guard = guard;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity livingEntity = this.guard.getTarget();
        if (livingEntity != null && livingEntity.isAlive()) {
            if (livingEntity instanceof net.minecraft.world.entity.player.Player player) {
                if (player.isCreative() || player.isSpectator()) {
                    this.guard.setTarget(null);
                    return false;
                }
            }
            this.target = livingEntity;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        this.guard.setAggressive(true);
        this.attackCooldown = 0;
        this.blockCooldown = 0;
        this.blockDuration = 0;
        this.stuckTimer = 0;
        this.lastPos = null;
    }

    @Override
    public void stop() {
        this.guard.setAggressive(false);
        this.guard.stopUsingItem();
        this.target = null;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) return;

        this.guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = this.guard.distanceToSqr(target);
        double dist = Math.sqrt(distSqr);

        // Детектор застревания
        BlockPos currentPos = this.guard.blockPosition();
        if (lastPos != null && lastPos.equals(currentPos)) {
            stuckTimer++;
            if (stuckTimer > 30) {
                this.guard.getNavigation().stop();
                this.guard.getJumpControl().jump();
                stuckTimer = 0;
            }
        } else {
            lastPos = currentPos;
            stuckTimer = 0;
        }

        // Атака столбов
        double dy = target.getY() - this.guard.getY();
        if (dy > 1.5D) {
            BlockPos pillarPos = BlockPos.containing(target.getX(), target.getY() - 1, target.getZ());
            BlockState state = this.guard.level().getBlockState(pillarPos);
            if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                this.guard.getNavigation().moveTo(pillarPos.getX() + 0.5, pillarPos.getY(), pillarPos.getZ() + 0.5, 1.2D);
                if (dist < 4.0) {
                    this.guard.swing(InteractionHand.MAIN_HAND);
                }
                return;
            }
        }

        // Движение: приближаемся или кружим
        if (dist > 3.0) {
            this.guard.getNavigation().moveTo(target, 1.2D);
        } else {
            // Кружение вокруг цели
            boolean clockwise = (this.guard.getId() % 2 == 0);
            double angle = (this.guard.tickCount * 0.1) * (clockwise ? 1 : -1);
            double radius = 2.5D;
            double tx = target.getX() + Math.cos(angle) * radius;
            double tz = target.getZ() + Math.sin(angle) * radius;
            this.guard.getNavigation().moveTo(tx, target.getY(), tz, 1.0D);
        }

        // Поворот к цели
        double dx = target.getX() - this.guard.getX();
        double dz = target.getZ() - this.guard.getZ();
        float yaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        this.guard.setYRot(yaw);
        this.guard.setYBodyRot(yaw);
        this.guard.setYHeadRot(yaw);

        // Блок щитом
        handleShieldBlock(distSqr);

        // Атака
        if (attackCooldown > 0) attackCooldown--;

        boolean canSee = this.guard.getSensing().hasLineOfSight(target);
        if (dist <= 3.0 && attackCooldown <= 0 && canSee) {
            this.attackCooldown = 12 + this.guard.getRandom().nextInt(6);
            performMeleeAttack();
        }
    }

    private void handleShieldBlock(double distSqr) {
        if (blockCooldown > 0) blockCooldown--;
        if (this.guard.getOffhandItem().getItem() instanceof ShieldItem) {
            if (this.guard.isUsingItem()) {
                blockDuration--;
                if (blockDuration <= 0) {
                    this.guard.stopUsingItem();
                    this.blockCooldown = 30 + this.guard.getRandom().nextInt(20);
                }
            } else if (blockCooldown <= 0 && distSqr <= 16.0) {
                if (target.swingTime > 0 || this.guard.getRandom().nextFloat() < 0.15f) {
                    this.guard.startUsingItem(InteractionHand.OFF_HAND);
                    this.blockDuration = 10 + this.guard.getRandom().nextInt(10);
                }
            }
        }
    }

    private void performMeleeAttack() {
        this.guard.swing(InteractionHand.MAIN_HAND);

        // Крит: 20% шанс или прыжок
        boolean isCrit = (!this.guard.onGround() && this.guard.getDeltaMovement().y < 0) ||
                this.guard.getRandom().nextFloat() < 0.2f;

        if (isCrit) {
            if (this.guard.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
            }
            target.level().playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        }

        this.guard.doHurtTarget(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity livingEntity = this.guard.getTarget();
        return livingEntity != null && livingEntity.isAlive() && this.guard.distanceToSqr(livingEntity) < 256.0;
    }
}