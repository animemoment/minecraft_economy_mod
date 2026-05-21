package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

import java.util.EnumSet;

public class VillagerLumberjackGoal extends Goal {
    private final Villager villager;
    private BlockPos targetLogPos; // Текущее бревно для рубки
    private BlockPos baseDirtPos;   // Место земли под деревом (для посадки саженца)
    private int breakProgress = 0;
    private int maxBreakTicks = 20;
    private int scanCooldown = 0;

    public VillagerLumberjackGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().isNight()) return false;

        // Если жителя бьют — он спасается, а не рубит лес
        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 100) {
            return false;
        }

        // Профессия Дровосека — Лучник (Fletcher), работающий с деревом
        if (villager.getVillagerData().getProfession() != VillagerProfession.FLETCHER) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;

        // Если дровосек голоден (сытость < 3) — он бастует
        if (att.getHunger() < 3.0) return false;

        // Проверяем наличие топора в инвентаре
        ItemStack axe = getAxeFromInventory(att);
        if (axe.isEmpty()) return false;

        // Защита от слишком частого поиска пути (раз в 3 секунды)
        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }

        // Если мы уже рубили дерево и над ним осталось бревно — продолжаем рубить его
        if (targetLogPos != null) {
            BlockState state = villager.level().getBlockState(targetLogPos);
            if (state.is(BlockTags.LOGS)) {
                return true;
            }
        }

        // Ищем новое дерево в радиусе 12 блоков
        BlockPos foundTree = scanForTree();
        if (foundTree != null) {
            this.targetLogPos = foundTree;
            this.baseDirtPos = foundTree.below(); // Земля прямо под нижним бревном
            return true;
        }

        this.scanCooldown = 60; // Перезарядка поиска при неудаче
        return false;
    }

    private ItemStack getAxeFromInventory(VillagerAttachment att) {
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private BlockPos scanForTree() {
        BlockPos current = villager.blockPosition();
        // Сканируем 12x4x12 блоков вокруг
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-12, -2, -12), current.offset(12, 3, 12))) {
            BlockState state = villager.level().getBlockState(pos);
            // Нам нужно бревно, под которым находится земля, трава или подзол
            if (state.is(BlockTags.LOGS)) {
                BlockState belowState = villager.level().getBlockState(pos.below());
                if (belowState.is(Blocks.GRASS_BLOCK) || belowState.is(Blocks.DIRT) || belowState.is(Blocks.COARSE_DIRT) || belowState.is(Blocks.PODZOL)) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    @Override
    public void start() {
        this.breakProgress = 0;
        calculateBreakDuration();
    }

    private void calculateBreakDuration() {
        if (targetLogPos == null) return;
        BlockState state = villager.level().getBlockState(targetLogPos);
        double hardness = state.getDestroySpeed(villager.level(), targetLogPos);
        double toolSpeed = 1.0;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null) {
            ItemStack axe = getAxeFromInventory(att);
            if (!axe.isEmpty()) {
                toolSpeed = axe.getDestroySpeed(state);
            }
        }

        // Рассчитываем время рубки бревна (в тиках)
        this.maxBreakTicks = (int) Math.max(5, (hardness * 25.0) / toolSpeed);
    }

    @Override
    public void tick() {
        if (targetLogPos == null) return;

        // Блокируем стандартные хотелки ванильного ИИ
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);

        villager.getLookControl().setLookAt(targetLogPos.getX() + 0.5, targetLogPos.getY() + 0.5, targetLogPos.getZ() + 0.5);

        // Стоим рядом с блоком дерева
        BlockPos standPos = targetLogPos.relative(Direction.NORTH);
        if (!villager.level().getBlockState(standPos).isAir()) {
            // Если северный блок занят, ищем любую свободную сторону
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos testPos = targetLogPos.relative(dir);
                if (villager.level().getBlockState(testPos).isAir()) {
                    standPos = testPos;
                    break;
                }
            }
        }

        double dist = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);

        if (dist < 3.5) {
            villager.getNavigation().stop();
            breakProgress++;

            // Машем рукой с топором
            if (breakProgress % 4 == 0) {
                villager.swing(InteractionHand.MAIN_HAND);
            }

            // Звук ударов топора по дереву
            if (breakProgress % 6 == 0) {
                villager.level().playSound(null, targetLogPos, net.minecraft.sounds.SoundEvents.WOOD_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.9f);
            }

            // Отправляем визуальные трещины на блоке
            if (villager.level() instanceof ServerLevel sl) {
                int stage = (int) (((double) breakProgress / maxBreakTicks) * 10);
                sl.destroyBlockProgress(villager.getId(), targetLogPos, stage);
            }

            // Если время рубки вышло — ломаем бревно!
            if (breakProgress >= maxBreakTicks) {
                performBreak();
                breakProgress = 0;
            }
        } else {
            // Идем к точке рубки
            villager.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 0.55D);
        }
    }

    private void performBreak() {
        if (!(villager.level() instanceof ServerLevel sl)) return;

        // Снижаем прочность топора
        damageAxe();

        // Ломаем блок физически (он выпадет на землю)
        sl.destroyBlock(targetLogPos, true, villager);
        sl.destroyBlockProgress(villager.getId(), targetLogPos, -1);

        // Расходуем сытость дровосека
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null) {
            att.decreaseHunger(0.35);
        }

        // Посегментная рубка дерева вверх: ищем следующее бревно ровно над текущим
        BlockPos nextLog = targetLogPos.above();
        if (sl.getBlockState(nextLog).is(BlockTags.LOGS) && nextLog.getY() - baseDirtPos.getY() <= 6) {
            // Если прямо сверху есть бревно и оно не выше 6 блоков от земли — переключаемся на него
            this.targetLogPos = nextLog.immutable();
            calculateBreakDuration();
        } else {
            // Если дерево срублено до конца — пытаемся посадить саженец на очищенное место
            tryPlantSapling(att, sl);
            this.targetLogPos = null;
        }
    }

    private void damageAxe() {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null) {
            ItemStack axe = getAxeFromInventory(att);
            if (!axe.isEmpty()) {
                int damage = axe.getDamageValue() + 1;
                axe.setDamageValue(damage);

                if (damage >= axe.getMaxDamage()) {
                    axe.shrink(1);
                    villager.level().playSound(null, villager.blockPosition(),
                            net.minecraft.sounds.SoundEvents.ITEM_BREAK,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА: У дровосека {} сломался топор!", villager.getName().getString());
                }
            }
        }
    }

    private void tryPlantSapling(VillagerAttachment att, ServerLevel sl) {
        if (baseDirtPos == null || att == null) return;

        BlockPos plantPos = baseDirtPos.above();
        // Место для посадки должно быть пустым, а под ним должна быть пригодная почва
        BlockState belowState = sl.getBlockState(baseDirtPos);
        boolean isSoil = belowState.is(Blocks.GRASS_BLOCK) || belowState.is(Blocks.DIRT) || belowState.is(Blocks.COARSE_DIRT) || belowState.is(Blocks.PODZOL);

        if (sl.getBlockState(plantPos).isAir() && isSoil) {
            SimpleContainer inv = att.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    Item item = stack.getItem();
                    // Проверяем, является ли предмет саженцем
                    net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item);
                    if (block instanceof net.minecraft.world.level.block.SaplingBlock saplingBlock) {
                        sl.setBlockAndUpdate(plantPos, saplingBlock.defaultBlockState());
                        stack.shrink(1);
                        sl.playSound(null, plantPos, net.minecraft.sounds.SoundEvents.GRASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void stop() {
        if (targetLogPos != null && villager.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(villager.getId(), targetLogPos, -1);
        }
        this.targetLogPos = null;
        this.breakProgress = 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.level().isNight()) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null && att.getHunger() < 3.0) return false;

        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 100) {
            return false;
        }

        return targetLogPos != null && villager.level().getBlockState(targetLogPos).is(BlockTags.LOGS);
    }
}