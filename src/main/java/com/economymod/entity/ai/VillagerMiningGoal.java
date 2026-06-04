package com.economymod.entity.ai;

import com.economymod.EconomyMod;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import com.economymod.entity.ai.mining.BridgeBuilder;
import com.economymod.entity.ai.mining.TorchPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.*;

public class VillagerMiningGoal extends Goal {
    private final Villager villager;
    private BlockPos targetBlockPos;
    private BlockPos activeVeinBlockPos;
    private int breakProgress = 0;
    private int maxBreakTicks = 20;
    private int currentStep = 0;
    private boolean isBridging = false;
    private long lastLogTick = 0;
    private int startY = -1;
    private final Set<BlockPos> recentlyDestroyed = new HashSet<>();
    private long lastCleanTick = 0;
    private long lastEscapeTick = 0;
    private int escapeAttempts = 0;
    private BlockPos lastEscapeTarget = null;
    private long targetStartTime = 0;
    private static final long TARGET_TIMEOUT_TICKS = 200;
    private long lastNoTargetLog = 0;
    private BlockPos lastPos = null;
    private long lastMoveTick = 0;
    private static final long STUCK_TIMEOUT_TICKS = 40;

    // Переменные асинхронного сканера
    private volatile BlockPos asyncTargetBlockPos = null;
    private volatile boolean asyncScanRunning = false;
    private volatile long lastAsyncScanTime = 0;
    private static final long ASYNC_SCAN_COOLDOWN = 100; // Сканируем мир раз в 5 секунд

    // Черный список недостижимых целей
    private final Map<BlockPos, Integer> failCount = new HashMap<>();
    private final Map<BlockPos, Long> blockedUntil = new HashMap<>();
    private static final int MAX_FAILS = 3;
    private static final long BLOCK_DURATION_TICKS = 400;

    public VillagerMiningGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean isFluid(BlockState state) {
        return !state.getFluidState().isEmpty();
    }

    private int countNonFluidNeighbors(BlockPos pos) {
        int count = 0;
        for (Direction dir : Direction.values()) {
            if (!isFluid(villager.level().getBlockState(pos.relative(dir)))) {
                count++;
            }
        }
        return count;
    }

    private boolean handleWaterSource(BlockPos waterPos, VillagerAttachment att) {
        Set<BlockPos> waterBlocks = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(waterPos);
        waterBlocks.add(waterPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!waterBlocks.contains(neighbor) && isFluid(villager.level().getBlockState(neighbor))) {
                    waterBlocks.add(neighbor);
                    queue.add(neighbor);
                }
            }
            if (waterBlocks.size() > 30) break;
        }

        List<BlockPos> sorted = new ArrayList<>(waterBlocks);
        sorted.sort((a, b) -> Integer.compare(countNonFluidNeighbors(b), countNonFluidNeighbors(a)));

        for (BlockPos pos : sorted) {
            ItemStack block = BridgeBuilder.getPlaceableBlock(att);
            if (block.isEmpty()) return false;
            BridgeBuilder.performPlaceBlock(villager.level(), pos, block);
            recentlyDestroyed.add(pos);
        }
        return true;
    }

    private boolean handleFlowingWater(BlockPos pos, VillagerAttachment att) {
        ItemStack block = BridgeBuilder.getPlaceableBlock(att);
        if (block.isEmpty()) return false;
        BridgeBuilder.performPlaceBlock(villager.level(), pos, block);
        recentlyDestroyed.add(pos);
        return true;
    }

    private boolean handleWaterIfNeeded(BlockPos pos, VillagerAttachment att, int n, String label) {
        if (!isFluid(villager.level().getBlockState(pos))) return false;
        BlockState state = villager.level().getBlockState(pos);
        if (state.getFluidState().isSource()) {
            EconomyMod.LOGGER.debug("[MINING] {} обнаружил источник воды в {}, осушаю область", villager.getName().getString(), pos.toShortString());
            return handleWaterSource(pos, att);
        } else {
            EconomyMod.LOGGER.debug("[MINING] {} обнаружил текучую воду в {}, затыкаю", villager.getName().getString(), pos.toShortString());
            return handleFlowingWater(pos, att);
        }
    }

    @Override
    public boolean canUse() {
        long now = villager.level().getGameTime();

        if (villager.isInWater()) {
            if (!villager.hasEffect(MobEffects.MOVEMENT_SPEED) ||
                    villager.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() < 20) {
                villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, true));
            }
        } else {
            if (villager.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                villager.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
        }

        if (villager.level().isNight()) return false;
        if (villager.isSleeping()) return false;
        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 120) return false;
        if (isEnemyNearby()) return false;

        if (now - lastCleanTick > 100) {
            recentlyDestroyed.clear();
            blockedUntil.entrySet().removeIf(entry -> entry.getValue() < now);
            lastCleanTick = now;
        }

        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (prof != VillagerProfession.TOOLSMITH && prof != VillagerProfession.ARMORER && prof != VillagerProfession.WEAPONSMITH) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;

        // ПРЕДОХРАНИТЕЛЬ УСТАЛОСТИ: Если житель выгорел (усталость > 85%), он отказывается копать!
        com.economymod.creatures.VillagerBrainWrapper brainWrapper = com.economymod.creatures.VillagerBrainWrapper.get(villager);
        if (brainWrapper != null && brainWrapper.getSensorValue("Fatigue") > 0.85f) {
            return false;
        }

        boolean hasFreeSlot = false;
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                hasFreeSlot = true;
                break;
            }
        }
        if (!hasFreeSlot) return false;

        ItemStack pickaxe = att.getActivePickaxe();
        if (pickaxe.isEmpty()) return false;

        if (att.getMiningStartY() != -1) {
            this.startY = att.getMiningStartY();
        } else {
            BlockPos chestPos = att.getPersonalChestPos();
            if (chestPos != null) {
                BlockPos startPos = chestPos.offset(0, 0, 1);
                this.startY = startPos.getY();
                att.setMiningStartY(this.startY);
            }
        }

        // Если у нас уже есть активная цель, продолжаем выполнять ее
        if (targetBlockPos != null) {
            return true;
        }

        // Если фоновое сканирование еще идет в другом потоке, просто выходим (не блокируем сервер)
        if (asyncScanRunning) {
            return false;
        }

        // Если асинхронный сканер нашел цель, забираем ее
        if (asyncTargetBlockPos != null) {
            this.targetBlockPos = asyncTargetBlockPos;
            this.asyncTargetBlockPos = null; // сбрасываем буфер
            this.isBridging = false;
            this.targetStartTime = now;
            return true;
        }

        // Запуск асинхронного сканирования по кулдауну
        if (now - lastAsyncScanTime > ASYNC_SCAN_COOLDOWN) {
            lastAsyncScanTime = now;
            asyncScanRunning = true;

            // Копируем необходимые потокобезопасные переменные для передачи в фоновый поток
            BlockPos currentPos = villager.blockPosition().immutable();
            Level level = villager.level();
            ItemStack pickaxeCopy = pickaxe.copy();
            int currentStartY = this.startY;
            BlockPos chestPos = att.getPersonalChestPos();

            AsyncTaskScheduler.submit(() -> {
                try {
                    BlockPos found = performBackgroundScan(level, currentPos, pickaxeCopy, currentStartY, chestPos, att);
                    if (found != null) {
                        this.asyncTargetBlockPos = found; // безопасно публикуем результат
                    }
                } catch (Exception ignored) {
                } finally {
                    asyncScanRunning = false;
                }
            });
        }

        return false;
    }
    /**
     * Тяжелый цикл сканирования, выполняемый асинхронно в параллельном потоке
     */
    private BlockPos performBackgroundScan(Level level, BlockPos current, ItemStack pickaxe, int currentStartY, BlockPos chestPos, VillagerAttachment att) {
        long now = level.getGameTime();

        // 1. Поиск руды в радиусе 12x6x12
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-12, -6, -12), current.offset(12, 6, 12))) {
            if (recentlyDestroyed.contains(pos)) continue;
            if (currentStartY != -1 && pos.getY() > currentStartY + 10) continue;
            if (pos.getY() > current.getY() + 3) continue;
            if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;

            // Потокобезопасность: работаем только с загруженными чанками!
            if (!level.hasChunkAt(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) && isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(pos, att)) {
                if (hasLineOfSightToBlock(pos)) {
                    return pos.immutable();
                }
            }
        }

        // Проверяем рабочее время
        long dayTime = level.getDayTime() % 24000;
        boolean isWorkingTime = (dayTime >= 6000 && dayTime < 18000);
        if (!isWorkingTime) {
            return null;
        }

        // 2. Генерация шахтерского туннеля (до 100 шагов вниз)
        BlockPos startPos = (chestPos != null) ? chestPos.offset(0, 0, 1) : current;
        int x = startPos.getX();

        for (int n = 0; n < 100; n++) {
            int z = startPos.getZ() + n;
            int floorY = (chestPos != null) ? ((n < 3) ? (startPos.getY() - 1) : (startPos.getY() - ((n - 3) / 2) - 1)) : (startPos.getY() - (n / 2) - 1);
            BlockPos feetL = new BlockPos(x, floorY + 1, z);
            BlockPos belowL = feetL.below();
            BlockPos feetR = new BlockPos(x + 1, floorY + 1, z);
            BlockPos belowR = feetR.below();

            if (!level.hasChunkAt(feetL) || !level.hasChunkAt(feetR)) break;

            // Проверка необходимости мостов в шахте
            if (isPathPassable(level, feetL) && isHazardousOrVoid(level, belowL)) {
                if (!recentlyDestroyed.contains(belowL) && !(blockedUntil.containsKey(belowL) && blockedUntil.get(belowL) > now)) {
                    if (!BridgeBuilder.getPlaceableBlock(att).isEmpty()) return belowL.immutable();
                }
            }
            if (isPathPassable(level, feetR) && isHazardousOrVoid(level, belowR)) {
                if (!recentlyDestroyed.contains(belowR) && !(blockedUntil.containsKey(belowR) && blockedUntil.get(belowR) > now)) {
                    if (!BridgeBuilder.getPlaceableBlock(att).isEmpty()) return belowR.immutable();
                }
            }

            BlockState stateFeetL = level.getBlockState(feetL);
            BlockState stateHeadL = level.getBlockState(feetL.above());
            BlockState stateCeilL = level.getBlockState(feetL.above(2));
            BlockState stateFeetR = level.getBlockState(feetR);
            BlockState stateHeadR = level.getBlockState(feetR.above());
            BlockState stateCeilR = level.getBlockState(feetR.above(2));

            BlockPos ceilL = feetL.above(2);
            BlockPos ceilR = feetR.above(2);

            // Поиск горизонтальных ответвлений
            if (n >= 12 && n % 6 == 0 && !isPathBlocked(stateFeetL) && !isPathBlocked(stateHeadL) && !isPathBlocked(stateCeilL) &&
                    level.getBlockState(ceilL.above()).isSolid()) {
                BlockPos branchTarget = scanHorizontalBranch(level, x, floorY, z, 1, pickaxe);
                if (branchTarget != null && !recentlyDestroyed.contains(branchTarget) && !(blockedUntil.containsKey(branchTarget) && blockedUntil.get(branchTarget) > now)) {
                    return branchTarget;
                }
                branchTarget = scanHorizontalBranch(level, x, floorY, z, -1, pickaxe);
                if (branchTarget != null && !recentlyDestroyed.contains(branchTarget) && !(blockedUntil.containsKey(branchTarget) && blockedUntil.get(branchTarget) > now)) {
                    return branchTarget;
                }
            }

            BlockState stateBelowL = level.getBlockState(belowL);
            BlockState stateBelowR = level.getBlockState(belowR);
            if (stateBelowL.is(Tags.Blocks.ORES) && isMinable(stateBelowL) && isSafeToMine(belowL, att) && !recentlyDestroyed.contains(belowL) && !(blockedUntil.containsKey(belowL) && blockedUntil.get(belowL) > now)) {
                return belowL.immutable();
            }
            if (stateBelowR.is(Tags.Blocks.ORES) && isMinable(stateBelowR) && isSafeToMine(belowR, att) && !recentlyDestroyed.contains(belowR) && !(blockedUntil.containsKey(belowR) && blockedUntil.get(belowR) > now)) {
                return belowR.immutable();
            }

            // Нахождение физического препятствия в туннеле
            if (isPathBlocked(stateFeetL) || isPathBlocked(stateHeadL) || isPathBlocked(stateCeilL) ||
                    isPathBlocked(stateFeetR) || isPathBlocked(stateHeadR) || isPathBlocked(stateCeilR)) {

                BlockPos headL = feetL.above();
                BlockPos headR = feetR.above();

                if (isPathBlocked(stateCeilL) && isMinable(stateCeilL) && isSafeToMine(ceilL, att) && !recentlyDestroyed.contains(ceilL) && !(blockedUntil.containsKey(ceilL) && blockedUntil.get(ceilL) > now)) return ceilL.immutable();
                if (isPathBlocked(stateCeilR) && isMinable(stateCeilR) && isSafeToMine(ceilR, att) && !recentlyDestroyed.contains(ceilR) && !(blockedUntil.containsKey(ceilR) && blockedUntil.get(ceilR) > now)) return ceilR.immutable();
                if (isPathBlocked(stateHeadL) && isMinable(stateHeadL) && isSafeToMine(headL, att) && !recentlyDestroyed.contains(headL) && !(blockedUntil.containsKey(headL) && blockedUntil.get(headL) > now)) return headL.immutable();
                if (isPathBlocked(stateHeadR) && isMinable(stateHeadR) && isSafeToMine(headR, att) && !recentlyDestroyed.contains(headR) && !(blockedUntil.containsKey(headR) && blockedUntil.get(headR) > now)) return headR.immutable();
                if (isPathBlocked(stateFeetL) && isMinable(stateFeetL) && isSafeToMine(feetL, att) && !recentlyDestroyed.contains(feetL) && !(blockedUntil.containsKey(feetL) && blockedUntil.get(feetL) > now)) return feetL.immutable();
                if (isPathBlocked(stateFeetR) && isMinable(stateFeetR) && isSafeToMine(feetR, att) && !recentlyDestroyed.contains(feetR) && !(blockedUntil.containsKey(feetR) && blockedUntil.get(feetR) > now)) return feetR.immutable();

                break;
            }
        }
        return null;
    }

    private boolean isEnemyNearby() {
        AABB searchBox = villager.getBoundingBox().inflate(12.0D);
        List<Monster> monsters = villager.level().getEntitiesOfClass(Monster.class, searchBox, Entity::isAlive);
        return !monsters.isEmpty();
    }

    private boolean isOreExposed(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (villager.level().getBlockState(pos.relative(dir)).isAir()) return true;
        }
        return false;
    }

    private boolean hasLineOfSightToBlock(BlockPos pos) {
        Vec3 eyePos = new Vec3(villager.getX(), villager.getY() + villager.getEyeHeight(), villager.getZ());
        Vec3 targetPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        ClipContext context = new ClipContext(eyePos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, villager);
        BlockHitResult result = villager.level().clip(context);
        return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(pos);
    }

    private boolean isPathBlocked(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) ||
                state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) ||
                state.is(Blocks.LADDER)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) return true;
        return state.isSolid();
    }

    private boolean isPathPassable(Level level, BlockPos pos) {
        return !isPathBlocked(level.getBlockState(pos));
    }

    private boolean isHazardousOrVoid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.POWDER_SNOW) || !state.getFluidState().isEmpty();
    }

    private boolean isFluidOrUnstable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && (!state.getFluidState().isEmpty() || state.is(Blocks.POWDER_SNOW));
    }

    private boolean isCollidingWithVillager(BlockPos pos) {
        BlockPos feetPos = villager.blockPosition();
        BlockPos headPos = feetPos.above();
        return pos.equals(feetPos) || pos.equals(headPos);
    }

    private boolean tryRequestBridge(BlockPos pos, VillagerAttachment att, boolean shouldLog, int n, String label) {
        if (recentlyDestroyed.contains(pos)) return false;
        if (isCollidingWithVillager(pos)) return false;
        ItemStack block = BridgeBuilder.getPlaceableBlock(att);
        if (!block.isEmpty()) {
            this.targetBlockPos = pos.immutable();
            this.isBridging = true;
            if (shouldLog) {
                EconomyMod.LOGGER.debug("[MINING] {} строит мост ({}) шаг {}: {}", villager.getName().getString(), label, n, pos.toShortString());
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("unused")
    private boolean tryPlugBlock(BlockPos pos, VillagerAttachment att, boolean shouldLog, int n, String label) {
        if (recentlyDestroyed.contains(pos)) return false;
        if (isCollidingWithVillager(pos)) return false;
        ItemStack block = BridgeBuilder.getPlaceableBlock(att);
        if (!block.isEmpty()) {
            this.targetBlockPos = pos.immutable();
            this.isBridging = true;
            if (shouldLog) {
                EconomyMod.LOGGER.debug("[MINING] {} закупоривает жидкость ({}) шаг {}: {}", villager.getName().getString(), label, n, pos.toShortString());
            }
            return true;
        }
        return false;
    }

    private BlockPos scanHorizontalBranch(Level level, int startX, int y, int z, int dirX, ItemStack pickaxe) {
        for (int b = 1; b <= 16; b++) {
            int cx = startX + (b * dirX);
            BlockPos bFloor = new BlockPos(cx, y, z);
            BlockPos bHead = new BlockPos(cx, y + 1, z);
            BlockPos bCeiling = new BlockPos(cx, y + 2, z);

            if (!level.hasChunkAt(bFloor)) break;

            BlockState stateFloor = level.getBlockState(bFloor);
            BlockState stateHead = level.getBlockState(bHead);
            BlockState stateClass = level.getBlockState(bCeiling);
            if (!stateHead.isAir() || !stateClass.isAir()) {
                if (!stateClass.isAir() && isMinable(stateClass) && isToolTiredCorrect(pickaxe, stateClass) && isSafeToMine(bCeiling, null)) {
                    return bCeiling.immutable();
                }
                if (!stateHead.isAir() && isMinable(stateHead) && isToolTiredCorrect(pickaxe, stateHead) && isSafeToMine(bHead, null)) {
                    return bHead.immutable();
                }
                break;
            }
        }
        return null;
    }

    private boolean isMinable(BlockState state) {
        if (state.getDestroySpeed(villager.level(), BlockPos.ZERO) < 0) return false;
        return !state.is(Blocks.BEDROCK) && !state.is(Blocks.CHEST) &&
                !state.is(Blocks.WATER) && !state.is(Blocks.LAVA) &&
                !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH) &&
                !state.is(Blocks.SOUL_TORCH) && !state.is(Blocks.SOUL_WALL_TORCH) &&
                !state.is(Blocks.LADDER);
    }

    private boolean isSafeToMine(BlockPos pos, VillagerAttachment att) {
        if (att != null && !BridgeBuilder.getPlaceableBlock(att).isEmpty()) return true;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockState adjState = villager.level().getBlockState(pos.relative(dir));
            if (!adjState.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    private boolean isToolTiredCorrect(ItemStack pickaxe, BlockState state) {
        if (state.requiresCorrectToolForDrops()) {
            return pickaxe.isCorrectToolForDrops(state);
        }
        return true;
    }

    private BlockPos findSafeStandPos(BlockPos target) {
        int currentY = villager.blockPosition().getY();
        int maxAllowedY = (startY != -1) ? Math.min(startY + 3, currentY + 1) : currentY + 1;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = target.relative(dir);
            if (p.getY() > maxAllowedY) continue;
            BlockState state = villager.level().getBlockState(p);
            BlockState stateAbove = villager.level().getBlockState(p.above());
            BlockState stateBelow = villager.level().getBlockState(p.below());
            if (state.isAir() && stateAbove.isAir() && stateBelow.isSolid()) {
                return p.immutable();
            }
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = target.below().relative(dir);
            if (p.getY() > maxAllowedY) continue;
            BlockState state = villager.level().getBlockState(p);
            BlockState stateAbove = villager.level().getBlockState(p.above());
            BlockState stateBelow = villager.level().getBlockState(p.below());
            if (state.isAir() && stateAbove.isAir() && stateBelow.isSolid()) {
                return p.immutable();
            }
        }
        for (Direction dir : Direction.values()) {
            BlockPos p = target.relative(dir);
            if (p.getY() > maxAllowedY) continue;
            BlockState state = villager.level().getBlockState(p);
            if (state.isAir() && villager.level().getBlockState(p.below()).isSolid()) {
                return p.immutable();
            }
        }
        return villager.blockPosition();
    }

    @Override
    public void start() {
        this.breakProgress = 0;
        this.escapeAttempts = 0;
        this.lastEscapeTarget = null;
        this.lastPos = null;
        calculateBreakDuration();
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null) {
            ItemStack pickaxe = att.getActivePickaxe();
            if (!pickaxe.isEmpty()) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, pickaxe.copy());
            }
        }
        if (targetBlockPos != null) {
            EconomyMod.LOGGER.debug("[MINING] {} начал копку. Цель: {}", villager.getName().getString(), targetBlockPos.toShortString());
        }
        targetStartTime = villager.level().getGameTime();
    }

    private void calculateBreakDuration() {
        if (targetBlockPos == null) return;
        BlockState state = villager.level().getBlockState(targetBlockPos);

        if (isFluid(state)) {
            EconomyMod.LOGGER.debug("[MINING] {} целевой блок жидкость, сброс цели", villager.getName().getString());
            recentlyDestroyed.add(targetBlockPos);
            this.targetBlockPos = null;
            this.maxBreakTicks = 1;
            return;
        }

        if (isBridging && state.isAir()) {
            EconomyMod.LOGGER.debug("[MINING] {} строительство моста, игнорируем воздух", villager.getName().getString());
            this.maxBreakTicks = 10;
            return;
        }

        if (state.isAir()) {
            EconomyMod.LOGGER.debug("[MINING] {} целевой блок стал воздухом, сброс цели", villager.getName().getString());
            recentlyDestroyed.add(targetBlockPos);
            this.targetBlockPos = null;
            this.maxBreakTicks = 1;
            return;
        }

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        double hardness = state.getDestroySpeed(villager.level(), targetBlockPos);
        double toolSpeed = 1.0;
        if (att != null) {
            ItemStack pickaxe = att.getActivePickaxe();
            if (!pickaxe.isEmpty()) {
                toolSpeed = pickaxe.getDestroySpeed(state);
            }
        }
        this.maxBreakTicks = (int) Math.max(5, (hardness * 30.0) / toolSpeed);
    }

    @Override
    public void tick() {
        if (targetBlockPos == null) {
            return;
        }

        long nowGame = villager.level().getGameTime();
        if (nowGame - targetStartTime > TARGET_TIMEOUT_TICKS && breakProgress == 0) {
            EconomyMod.LOGGER.debug("[MINING] {} таймаут цели {}, сброс", villager.getName().getString(), targetBlockPos.toShortString());
            int fails = failCount.getOrDefault(targetBlockPos, 0) + 1;
            failCount.put(targetBlockPos, fails);
            if (fails >= MAX_FAILS) {
                blockedUntil.put(targetBlockPos, nowGame + BLOCK_DURATION_TICKS);
                EconomyMod.LOGGER.debug("[MINING] {} блокируем цель {} на {} тиков", villager.getName().getString(), targetBlockPos.toShortString(), BLOCK_DURATION_TICKS);
            }
            stop();
            this.targetBlockPos = null;
            this.activeVeinBlockPos = null;
            return;
        }

        if (breakProgress == 0 && villager.getNavigation().isDone()) {
            BlockPos currentPos = villager.blockPosition();
            if (lastPos != null && lastPos.equals(currentPos)) {
                if (villager.level().getGameTime() - lastMoveTick > STUCK_TIMEOUT_TICKS) {
                    EconomyMod.LOGGER.debug("[MINING] {} застрял на месте, сброс цели {}", villager.getName().getString(), targetBlockPos);
                    stop();
                    this.targetBlockPos = null;
                    this.activeVeinBlockPos = null;
                    return;
                }
            } else {
                lastPos = currentPos;
                lastMoveTick = villager.level().getGameTime();
            }
        } else {
            lastPos = null;
        }

        if (villager.getBrain() != null) {
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.INTERACTION_TARGET);
        }

        if (breakProgress > maxBreakTicks + 120) {
            EconomyMod.LOGGER.debug("[MINING] {} застрял на прогрессе! Сброс. Цель: {}",
                    villager.getName().getString(), targetBlockPos != null ? targetBlockPos.toShortString() : "null");
            stop();
            this.targetBlockPos = null;
            this.activeVeinBlockPos = null;
            return;
        }

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        BlockPos standPos = findSafeStandPos(targetBlockPos);

        if (startY != -1 && standPos.getY() > startY + 5) {
            EconomyMod.LOGGER.debug("[MINING] {} standPos выше разрешенного, сброс цели", villager.getName().getString());
            stop();
            this.targetBlockPos = null;
            return;
        }

        BlockPos feetPos = villager.blockPosition();
        long now = villager.level().getGameTime();
        if (feetPos.getY() < standPos.getY() - 2 && now - lastEscapeTick > 40 && att != null && targetBlockPos != null && targetBlockPos.getY() < feetPos.getY()) {
            if (lastEscapeTarget != null && lastEscapeTarget.equals(targetBlockPos)) {
                escapeAttempts++;
                if (escapeAttempts >= 3) {
                    EconomyMod.LOGGER.debug("[MINING] {} слишком много попыток выбраться для цели {}, сброс", villager.getName().getString(), targetBlockPos.toShortString());
                    stop();
                    this.targetBlockPos = null;
                    this.activeVeinBlockPos = null;
                    escapeAttempts = 0;
                    lastEscapeTarget = null;
                    return;
                }
            } else {
                escapeAttempts = 1;
                lastEscapeTarget = targetBlockPos;
            }

            ItemStack blockStack = BridgeBuilder.getPlaceableBlock(att);
            if (!blockStack.isEmpty()) {
                BlockPos ceilingPos = feetPos.above(2);
                if (villager.level().getBlockState(ceilingPos).isAir()) {
                    villager.getJumpControl().jump();
                    net.minecraft.world.level.block.Block b = net.minecraft.world.level.block.Block.byItem(blockStack.getItem());
                    villager.level().setBlockAndUpdate(feetPos, b.defaultBlockState());
                    blockStack.shrink(1);
                    villager.teleportTo(villager.getX(), feetPos.getY() + 1.0D, villager.getZ());
                    villager.level().playSound(null, feetPos, net.minecraft.sounds.SoundEvents.STONE_PLACE,
                            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                    lastEscapeTick = now;
                    EconomyMod.LOGGER.debug("[MINING] {} выбрался из ямы, подмостив блок", villager.getName().getString());
                    return;
                }
            }
        }

        double dx = targetBlockPos.getX() + 0.5 - villager.getX();
        double dy = targetBlockPos.getY() + 0.5 - (villager.getY() + villager.getEyeHeight());
        double dz = targetBlockPos.getZ() + 0.5 - villager.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float)(-(Math.atan2(dy, dh) * 180.0D / Math.PI));
        villager.setYRot(yaw);
        villager.setYBodyRot(yaw);
        villager.setYHeadRot(yaw);
        villager.setXRot(pitch);
        villager.getLookControl().setLookAt(targetBlockPos.getX() + 0.5, targetBlockPos.getY() + 0.5, targetBlockPos.getZ() + 0.5);

        TorchPlacer.tryPlaceTorch(villager, att);

        double dist = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);

        if (dist < 9.0) {
            villager.getNavigation().stop();
            breakProgress++;

            if (isBridging) {
                if (breakProgress >= maxBreakTicks) {
                    ItemStack block = BridgeBuilder.getPlaceableBlock(att);
                    BridgeBuilder.performPlaceBlock(villager.level(), targetBlockPos, block);
                    EconomyMod.LOGGER.debug("[MINING] {} завершил постройку моста/затычку в {}", villager.getName().getString(), targetBlockPos.toShortString());
                    this.targetBlockPos = null;
                    this.isBridging = false;
                    breakProgress = 0;
                    escapeAttempts = 0;
                    lastEscapeTarget = null;
                }
                return;
            } else {
                if (villager.level().getBlockState(targetBlockPos).isAir()) {
                    recentlyDestroyed.add(targetBlockPos);
                    this.targetBlockPos = null;
                    this.breakProgress = 0;
                    return;
                }
            }

            if (breakProgress % 3 == 0) villager.swing(InteractionHand.MAIN_HAND);
            if (villager.level() instanceof ServerLevel sl) {
                int stage = (int) (((double)breakProgress / maxBreakTicks) * 10);
                sl.destroyBlockProgress(villager.getId(), targetBlockPos, stage);
            }
            if (breakProgress % 5 == 0) {
                villager.level().playSound(null, targetBlockPos, net.minecraft.sounds.SoundEvents.STONE_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
            }

            if (breakProgress >= maxBreakTicks) {
                performBreak();
                breakProgress = 0;
            }
        } else {
            villager.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 0.5D);
        }
    }

    private void performBreak() {
        if (villager.level() instanceof ServerLevel sl) {
            BlockState state = sl.getBlockState(targetBlockPos);
            if (state.isAir()) {
                recentlyDestroyed.add(targetBlockPos);
                this.targetBlockPos = null;
                return;
            }

            recentlyDestroyed.add(targetBlockPos.immutable());
            failCount.remove(targetBlockPos);
            blockedUntil.remove(targetBlockPos);

            EconomyMod.LOGGER.info("[MINING] {} успешно добыл блок {} в {}",
                    villager.getName().getString(), state.getBlock().getName().getString(), targetBlockPos.toShortString());

            damageTool();
            sl.destroyBlock(targetBlockPos, true, villager);
            sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);

            VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
            if (att != null) {
                att.decreaseHunger(0.35);
            }

            BlockPos abovePos = targetBlockPos.above();
            BlockState aboveState = sl.getBlockState(abovePos);
            if (isPathBlocked(aboveState) && isMinable(aboveState)) {
                this.activeVeinBlockPos = abovePos.immutable();
                EconomyMod.LOGGER.debug("[MINING] {} нашел следующий блок жилы над: {}", villager.getName().getString(), abovePos.toShortString());
            } else {
                this.activeVeinBlockPos = findAdjacentOre(targetBlockPos);
                if (this.activeVeinBlockPos != null) {
                    EconomyMod.LOGGER.debug("[MINING] {} нашел смежную руду: {}", villager.getName().getString(), this.activeVeinBlockPos.toShortString());
                }
            }

            escapeAttempts = 0;
            lastEscapeTarget = null;
            this.targetBlockPos = null;
        }
    }

    private void damageTool() {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null) {
            ItemStack pickaxe = att.getActivePickaxe();
            if (!pickaxe.isEmpty()) {
                int damage = pickaxe.getDamageValue() + 1;
                pickaxe.setDamageValue(damage);
                if (damage >= pickaxe.getMaxDamage()) {
                    pickaxe.shrink(1);
                    villager.level().playSound(null, villager.blockPosition(),
                            net.minecraft.sounds.SoundEvents.ITEM_BREAK,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    EconomyMod.LOGGER.info("[MINING] {} сломал кирку!", villager.getName().getString());
                    this.activeVeinBlockPos = null;
                    villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
        }
    }

    private BlockPos findAdjacentOre(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos adj = pos.relative(dir);
            if (villager.level().getBlockState(adj).is(Tags.Blocks.ORES)) {
                return adj.immutable();
            }
        }
        return null;
    }

    @Override
    public void stop() {
        if (targetBlockPos != null && villager.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);
        }
        if (targetBlockPos != null) {
            recentlyDestroyed.add(targetBlockPos);
        }
        this.targetBlockPos = null;
        this.breakProgress = 0;
        this.isBridging = false;
        this.activeVeinBlockPos = null;
        this.escapeAttempts = 0;
        this.lastEscapeTarget = null;
        this.lastPos = null;
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.level().isNight()) return false;
        if (villager.isSleeping()) return false;
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null || att.getActivePickaxe().isEmpty()) {
            if (targetBlockPos != null && villager.level() instanceof ServerLevel sl) {
                sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);
            }
            return false;
        }
        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 120) {
            if (targetBlockPos != null && villager.level() instanceof ServerLevel sl) {
                sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);
            }
            return false;
        }
        if (isEnemyNearby()) {
            if (targetBlockPos != null && villager.level() instanceof ServerLevel sl) {
                sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);
            }
            return false;
        }
        if (att.getPersonalChestPos() == null && targetBlockPos != null && currentStep > 0) {
            if (villager.level() instanceof ServerLevel sl) {
                sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);
            }
            this.targetBlockPos = null;
            return false;
        }
        return targetBlockPos != null && (isMinable(villager.level().getBlockState(targetBlockPos)) || villager.level().getBlockState(targetBlockPos).isAir());
    }
}