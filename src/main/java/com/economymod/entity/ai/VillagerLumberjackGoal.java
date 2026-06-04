package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import com.economymod.zones.ZoneInstance;
import com.economymod.zones.ZoneRegistry;
import com.economymod.zones.ZoneType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

import java.util.*;

public class VillagerLumberjackGoal extends Goal {
    private final Villager villager;
    private VillagerAttachment att; // Кэшируем аттачмент жителя
    private BlockPos targetLogPos;
    private BlockPos baseDirtPos;
    private int breakProgress = 0;
    private int maxBreakTicks = 20;

    // Очередь брёвен снизу вверх
    private final List<BlockPos> chopQueue = new ArrayList<>();
    private int queueIndex = 0;

    // Уникальный ID текущей зоны дерева, которую мы рубим
    private UUID activeTreeZoneId = null;

    private long lastScanTime = 0;
    private static final long SCAN_COOLDOWN = 60; // 3 секунды кулдауна на поиск нового дерева

    private BlockPos lastTargetPos = null;
    private int stuckTicks = 0;

    public VillagerLumberjackGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().isNight()) return false;
        if (isUnderAttack()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.FLETCHER) return false;

        this.att = villager.getData(ModAttachments.VILLAGER.get());
        if (this.att == null || this.att.getHunger() < 3.0) return false;
        if (getAxeFromInventory(this.att).isEmpty()) return false;

        long now = villager.level().getGameTime();

        // Если мы уже рубим дерево и в очереди есть бревна
        if (activeTreeZoneId != null && !chopQueue.isEmpty() && queueIndex < chopQueue.size()) {
            advanceQueue();
            if (queueIndex < chopQueue.size()) {
                this.targetLogPos = chopQueue.get(queueIndex);
                return true;
            }
        }

        if (now - lastScanTime < SCAN_COOLDOWN) return false;
        lastScanTime = now;

        if (!(villager.level() instanceof ServerLevel sl)) return false;

        // БЫСТРЫЙ ПОИСК ЧЕРЕЗ ЗОНЫ: Ищем все зоны TREE в радиусе 16 блоков от жителя
        ZoneRegistry registry = ZoneRegistry.get(sl);
        ChunkPos currentChunk = new ChunkPos(villager.blockPosition());

        // ЛЕНИВОЕ СКАНИРОВАНИЕ: Если чанк под жителем еще не был просканирован, сканируем его прямо сейчас!
        if (!registry.isChunkScanned(currentChunk)) {
            registry.markChunkAsScanned(currentChunk);
            for (com.economymod.zones.detectors.ZoneDetector detector : com.economymod.zones.detectors.ZoneDetectionManager.getDetectors()) {
                try {
                    detector.scanChunk(sl, currentChunk);
                } catch (Exception e) {
                    com.economymod.EconomyMod.LOGGER.error("Ошибка при ленивом сканировании чанка {}", currentChunk, e);
                }
            }
        }

        Set<ZoneInstance> nearTrees = registry.getZonesNear(villager.blockPosition(), 16.0D);

        ZoneInstance nearestTree = null;
        double minDist = Double.MAX_VALUE;

        for (ZoneInstance tree : nearTrees) {
            if (tree.getType() == ZoneType.TREE) {
                double dist = tree.getBounds().distanceToSqr(villager.position());
                if (dist < minDist) {
                    minDist = dist;
                    nearestTree = tree;
                }
            }
        }

        if (nearestTree == null) return false;

        // Собираем бревна из найденной зоны дерева
        List<BlockPos> logs = new ArrayList<>();
        for (long posLong : nearestTree.getPositions()) {
            BlockPos pos = BlockPos.of(posLong);
            if (sl.getBlockState(pos).is(BlockTags.LOGS)) {
                logs.add(pos);
            }
        }

        if (logs.isEmpty()) return false;

        // Сортируем снизу вверх, чтобы дерево рубилось правильно и не зависало в воздухе
        logs.sort(Comparator.comparingInt(BlockPos::getY));

        this.chopQueue.clear();
        this.chopQueue.addAll(logs);
        this.queueIndex = 0;
        this.activeTreeZoneId = nearestTree.getId();
        this.targetLogPos = this.chopQueue.get(0);
        this.baseDirtPos = this.chopQueue.get(0).below().immutable();
        this.stuckTicks = 0;

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.level().isNight()) return false;
        if (isUnderAttack()) return false;

        if (this.att != null && this.att.getHunger() < 3.0) return false;
        if (activeTreeZoneId == null || chopQueue.isEmpty()) return false;

        // Проверяем, существует ли еще эта зона в реестре (может, её срубил игрок)
        if (villager.level() instanceof ServerLevel sl) {
            ZoneInstance currentZone = ZoneRegistry.get(sl).getZone(activeTreeZoneId);
            if (currentZone == null) {
                return false; // Зона была аннулирована/удалена
            }
        }

        // Осталось ли хоть одно бревно в нашей очереди
        for (int i = queueIndex; i < chopQueue.size(); i++) {
            if (villager.level().getBlockState(chopQueue.get(i)).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        this.breakProgress = 0;
        this.lastTargetPos = null;
        this.stuckTicks = 0;
        calculateBreakDuration();

        // ИСПРАВЛЕНО: Дровосек берет в руку топор (Axe) из инвентаря, а не кирку!
        if (this.att != null) {
            ItemStack axe = getAxeFromInventory(this.att);
            if (!axe.isEmpty()) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, axe.copy());
            }
        }
    }

    @Override
    public void stop() {
        if (targetLogPos != null && villager.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(villager.getId(), targetLogPos, -1);
        }
        this.chopQueue.clear();
        this.targetLogPos = null;
        this.activeTreeZoneId = null;
        this.queueIndex = 0;
        this.breakProgress = 0;
        this.stuckTicks = 0;
        // Убираем топор из руки
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        advanceQueue();

        if (queueIndex >= chopQueue.size()) {
            if (baseDirtPos != null && villager.level() instanceof ServerLevel sl) {
                tryPlantSapling(this.att, sl);
            }
            this.targetLogPos = null;
            return;
        }

        BlockPos target = chopQueue.get(queueIndex);
        this.targetLogPos = target;

        if (lastTargetPos != null && lastTargetPos.equals(target)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastTargetPos = target.immutable();
        }

        if (stuckTicks > 80) {
            Optional<BlockPos> alt = findReachableAlternative(queueIndex + 1);
            if (alt.isPresent()) {
                while (queueIndex < chopQueue.size() && !chopQueue.get(queueIndex).equals(alt.get())) {
                    queueIndex++;
                }
                this.targetLogPos = chopQueue.get(queueIndex);
            } else {
                this.chopQueue.clear();
                this.targetLogPos = null;
                this.activeTreeZoneId = null;
            }
            stuckTicks = 0;
            breakProgress = 0;
            return;
        }

        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);

        // ЦИЛИНДРИЧЕСКИЙ РАДИУС ДОСТИЖЕНИЯ:
        double dx = target.getX() + 0.5 - villager.getX();
        double dy = target.getY() + 0.5 - villager.getY();
        double dz = target.getZ() + 0.5 - villager.getZ();

        double horizontalDistSq = dx * dx + dz * dz;

        boolean canReach = false;
        if (horizontalDistSq <= 3.2 * 3.2) { // Стоим по горизонтали в пределах 3.2 блоков
            if (dy >= -2.0 && dy <= 15.0) {  // И высота бревна от 2 блоков ниже до 15 блоков выше нас
                canReach = true;
            }
        }

        if (!canReach) {
            // ИСПРАВЛЕНО: Надежная навигация к самому бревну. Ванильный навигатор сам найдет точку на земле у ствола!
            var nav = villager.getNavigation();
            nav.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6D);

            villager.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

            if (breakProgress > 0) {
                if (villager.level() instanceof ServerLevel sl) {
                    sl.destroyBlockProgress(villager.getId(), target, -1);
                }
                breakProgress = 0;
            }
        } else {
            // Стоим на месте у корней и рубим дерево
            villager.getNavigation().stop();
            villager.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

            breakProgress++;

            if (breakProgress % 4 == 0) {
                villager.swing(InteractionHand.MAIN_HAND);
            }
            if (breakProgress % 6 == 0) {
                villager.level().playSound(null, target,
                        net.minecraft.sounds.SoundEvents.WOOD_HIT,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.9f);
            }

            if (villager.level() instanceof ServerLevel sl) {
                int stage = (int) (((double) breakProgress / maxBreakTicks) * 10);
                sl.destroyBlockProgress(villager.getId(), target, Math.min(stage, 9));
            }

            if (breakProgress >= maxBreakTicks) {
                performBreak();
                breakProgress = 0;
            }
        }
    }

    private void performBreak() {
        if (!(villager.level() instanceof ServerLevel sl)) return;

        damageAxe();

        BlockState state = sl.getBlockState(targetLogPos);
        if (state.is(BlockTags.LOGS) && this.att != null) {
            // УМНЫЙ АВТОСБОР ДРОПА: Рассчитываем дроп блока программно
            List<ItemStack> drops = Block.getDrops(state, sl, targetLogPos, null, villager, getAxeFromInventory(this.att));

            // Ломаем блок физически
            sl.destroyBlock(targetLogPos, false, villager);

            // МГНОВЕННОЕ ОБНОВЛЕНИЕ ЗОНЫ: Извещаем детекторы, что блок сломан жителем!
            for (com.economymod.zones.detectors.ZoneDetector detector : com.economymod.zones.detectors.ZoneDetectionManager.getDetectors()) {
                detector.onBlockChanged(sl, targetLogPos);
            }

            // Помещаем дроп прямо в рюкзак дровосека
            for (ItemStack drop : drops) {
                ItemStack leftover = this.att.getInventory().addItem(drop);
                if (!leftover.isEmpty()) {
                    sl.addFreshEntity(new ItemEntity(sl, villager.getX(), villager.getY(), villager.getZ(), leftover));
                }
            }
        } else {
            sl.destroyBlockProgress(villager.getId(), targetLogPos, -1);
        }

        stuckTicks = 0;

        if (this.att != null) {
            this.att.decreaseHunger(0.35);
        }

        queueIndex++;
        advanceQueue();

        if (queueIndex < chopQueue.size()) {
            this.targetLogPos = chopQueue.get(queueIndex).immutable();
            calculateBreakDuration();
        } else {
            tryPlantSapling(this.att, sl);
            this.chopQueue.clear();
            this.targetLogPos = null;
            this.activeTreeZoneId = null;
        }
    }

    private void advanceQueue() {
        while (queueIndex < chopQueue.size() &&
                !villager.level().getBlockState(chopQueue.get(queueIndex)).is(BlockTags.LOGS)) {
            queueIndex++;
        }
    }

    private boolean isUnderAttack() {
        return villager.getLastHurtByMob() != null &&
                villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 100;
    }

    private ItemStack getAxeFromInventory(VillagerAttachment att) {
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof AxeItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private void calculateBreakDuration() {
        if (targetLogPos == null) return;
        BlockState state = villager.level().getBlockState(targetLogPos);
        double hardness = state.getDestroySpeed(villager.level(), targetLogPos);
        double toolSpeed = 1.0;

        if (this.att != null) {
            ItemStack axe = getAxeFromInventory(this.att);
            if (!axe.isEmpty()) toolSpeed = axe.getDestroySpeed(state);
        }

        this.maxBreakTicks = (int) Math.max(5, (hardness * 25.0) / toolSpeed);
    }

    private Optional<BlockPos> findReachableAlternative(int fromIndex) {
        for (int i = fromIndex; i < Math.min(chopQueue.size(), fromIndex + 15); i++) {
            BlockPos p = chopQueue.get(i);
            if (!villager.level().getBlockState(p).is(BlockTags.LOGS)) continue;
            var nav = villager.getNavigation();
            var path = nav.createPath(p, 1);
            if (path != null && path.canReach()) return Optional.of(p);
            var groundPath = nav.createPath(p.below(), 1);
            if (groundPath != null && groundPath.canReach()) return Optional.of(p);
        }
        return Optional.empty();
    }

    private void damageAxe() {
        if (this.att == null) return;
        ItemStack axe = getAxeFromInventory(this.att);
        if (axe.isEmpty()) return;

        int damage = axe.getDamageValue() + 1;
        axe.setDamageValue(damage);
        if (damage >= axe.getMaxDamage()) {
            axe.shrink(1);
            villager.level().playSound(null, villager.blockPosition(),
                    net.minecraft.sounds.SoundEvents.ITEM_BREAK,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    private void tryPlantSapling(VillagerAttachment att, ServerLevel sl) {
        if (baseDirtPos == null || att == null) return;

        BlockPos plantPos = baseDirtPos.above();
        BlockState belowState = sl.getBlockState(baseDirtPos);
        boolean isSoil = belowState.is(Blocks.GRASS_BLOCK) ||
                belowState.is(Blocks.DIRT) ||
                belowState.is(Blocks.COARSE_DIRT) ||
                belowState.is(Blocks.PODZOL);

        if (!sl.getBlockState(plantPos).isAir() || !isSoil) return;

        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item);
            if (block instanceof net.minecraft.world.level.block.SaplingBlock saplingBlock) {
                sl.setBlockAndUpdate(plantPos, saplingBlock.defaultBlockState());
                stack.shrink(1);
                sl.playSound(null, plantPos,
                        net.minecraft.sounds.SoundEvents.GRASS_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                break;
            }
        }
    }
}