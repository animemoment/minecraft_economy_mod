package com.economymod.entity.ai;

import com.economymod.entity.VillageGuardEntity;
import net.minecraft.core.BlockPos; // Добавлено
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.state.BlockState; // Добавлено

import java.util.EnumSet;

public class VillageGuardCombatGoal extends Goal {
    private final VillageGuardEntity guard;
    private LivingEntity target;
    private int attackCooldown = 0;
    private int strafeChangeTimer = 0;
    private boolean strafeRight = true;
    private int blockCooldown = 0;
    private int blockDuration = 0;

    public VillageGuardCombatGoal(VillageGuardEntity guard) {
        this.guard = guard;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));

        // ИСПРАВЛЕНО: Распределяем направление стрейфа по ID.
        // Половина воинов пойдет влево, половина вправо — они зажмут игрока в клещи!
        this.strafeRight = (guard.getId() % 2 == 0);
    }

    @Override
    public boolean canUse() {
        LivingEntity livingEntity = this.guard.getTarget();
        if (livingEntity != null && livingEntity.isAlive()) {

            // ИСПРАВЛЕНО: Если цель — игрок в Креативе или Спектаторе, мы его полностью игнорируем!
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

        // Постоянный зрительный фокус на враге
        this.guard.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSqr = this.guard.distanceToSqr(target);
        boolean canSee = this.guard.getSensing().hasLineOfSight(target);

        // АНТИ-АБУЗ СТОЛБОВ
        if (target.getY() - this.guard.getY() > 1.5D) {
            BlockPos pillarPos = BlockPos.containing(target.getX(), target.getY() - 0.5D, target.getZ());
            BlockState state = this.guard.level().getBlockState(pillarPos);

            if (!state.isAir() && !state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                this.guard.getLookControl().setLookAt(pillarPos.getX() + 0.5, pillarPos.getY() + 0.5, pillarPos.getZ() + 0.5);
                this.guard.getNavigation().moveTo(pillarPos.getX() + 0.5, pillarPos.getY(), pillarPos.getZ() + 0.5, 1.1D);

                if (this.guard.distanceToSqr(pillarPos.getX() + 0.5, pillarPos.getY(), pillarPos.getZ() + 0.5) < 6.0) {
                    this.guard.getNavigation().stop();
                    this.guard.swing(InteractionHand.MAIN_HAND);
                    if (this.guard.getRandom().nextInt(15) == 0 && this.guard.level() instanceof ServerLevel sl) {
                        sl.destroyBlock(pillarPos, true, this.guard);
                    }
                }
                return;
            }
        }

        // Блокировка щитом
        handleShieldBlock(distSqr);

        // PvP стрейф боком и тактический маятник (взад-вперед)
        if (canSee && distSqr <= 16.0) {
            this.guard.getNavigation().stop();

            strafeChangeTimer++;
            if (strafeChangeTimer >= 25 + this.guard.getRandom().nextInt(25)) {
                strafeRight = !strafeRight;
                strafeChangeTimer = 0;
            }

            float sidewaysSpeed = strafeRight ? 0.5F : -0.5F;
            if (this.guard.isUsingItem()) {
                sidewaysSpeed *= 0.3F;
            }

            // ИСПРАВЛЕНО: Тактический маятник (Движение взад-вперед)
            float forwardSpeed = 0.1F; // По дефолту медленно кружимся по спирали

            if (distSqr < 4.0D) {
                // Слишком близко — отходим назад, чтобы держать врага на конце меча
                forwardSpeed = -0.3F;
            } else if (this.guard.isUsingItem()) {
                // Зажались щитом — медленно отступаем назад, держа оборону
                forwardSpeed = -0.15F;
            } else if (attackCooldown <= 1) {
                // Кулдаун удара прошел — делаем молниеносный выпад вперед!
                forwardSpeed = 0.5F;
            }

            this.guard.getMoveControl().strafe(forwardSpeed, sidewaysSpeed);

            // Фиксация тела на игрока
            double dx = target.getX() - this.guard.getX();
            double dz = target.getZ() - this.guard.getZ();
            float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            this.guard.setYRot(yaw);
            this.guard.setYBodyRot(yaw);
            this.guard.setYHeadRot(yaw);
        } else {
            this.guard.getNavigation().moveTo(target, 1.1D);
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (distSqr <= 9.0 && attackCooldown <= 0 && canSee) {
            this.attackCooldown = 10 + this.guard.getRandom().nextInt(4);

            if (this.guard.onGround() && this.guard.getRandom().nextFloat() < 0.6f && !this.guard.isUsingItem()) {
                this.guard.getJumpControl().jump();
            }

            performMeleeAttack();
        }
    }

    private void handleShieldBlock(double distSqr) {
        if (blockCooldown > 0) blockCooldown--;

        ItemStack offhand = this.guard.getOffhandItem();
        if (offhand.getItem() instanceof ShieldItem) {
            if (this.guard.isUsingItem()) {
                blockDuration--;
                if (blockDuration <= 0) {
                    this.guard.stopUsingItem();
                    this.blockCooldown = 20 + this.guard.getRandom().nextInt(15);
                }
            } else if (blockCooldown <= 0 && distSqr <= 9.0) {
                if (target.swingTime > 0 || target.getRandom().nextFloat() < 0.20f) {
                    this.guard.startUsingItem(InteractionHand.OFF_HAND);
                    this.blockDuration = 8 + this.guard.getRandom().nextInt(8);
                }
            }
        }
    }

    private void performMeleeAttack() {
        this.guard.swing(InteractionHand.MAIN_HAND);

        boolean isCrit = !this.guard.onGround() && this.guard.getDeltaMovement().y < 0;

        float damage = (float) this.guard.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (isCrit) {
            damage *= 1.5F;
            if (this.guard.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.2, 0.2, 0.2, 0.15);
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