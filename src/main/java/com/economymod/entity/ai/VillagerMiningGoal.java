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

    // Чёрный список недостижимых целей
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
            EconomyMod.LOGGER.info("[MINING] {} обнаружил источник воды в {}, осушаю область", villager.getName().getString(), pos.toShortString());
            return handleWaterSource(pos, att);
        } else {
            EconomyMod.LOGGER.info("[MINING] {} обнаружил текучую воду в {}, затыкаю", villager.getName().getString(), pos.toShortString());
            return handleFlowingWater(pos, att);
        }
    }

    @Override
    public boolean canUse() {
        long now = villager.level().getGameTime();
        boolean shouldLog = (now - lastLogTick > 100);

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

        // ========== ВЫСШИЙ ПРИОРИТЕТ: РУДА (даже вне рабочего времени) ==========
        // Проверка руды всегда разрешена
        if (villager.level().getGameTime() % 10 == 0 || targetBlockPos == null) {
            BlockPos current = villager.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(current.offset(-12, -6, -12), current.offset(12, 6, 12))) {
                if (recentlyDestroyed.contains(pos)) continue;
                if (startY != -1 && pos.getY() > startY + 10) continue;
                if (pos.getY() > villager.blockPosition().getY() + 3) continue;
                if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;
                BlockState state = villager.level().getBlockState(pos);
                if (state.is(Tags.Blocks.ORES) && isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(pos, att)) {
                    if (hasLineOfSightToBlock(pos)) {
                        this.targetBlockPos = pos.immutable();
                        this.isBridging = false;
                        EconomyMod.LOGGER.info("[MINING] {} нашёл руду {} в {}", villager.getName().getString(), state.getBlock().getName().getString(), pos.toShortString());
                        targetStartTime = villager.level().getGameTime();
                        return true;
                    }
                }
            }
        }

        // Проверка рабочего времени: если не рабочее время, запрещаем все остальные действия (кроме руды)
        long dayTime = villager.level().getGameTime() % 24000;
        boolean isWorkingTime = (dayTime >= 6000 && dayTime < 18000);
        if (!isWorkingTime) {
            return false;
        }

        BlockPos feetPos = villager.blockPosition();
        BlockPos headPos = feetPos.above();
        BlockState stateFeetPos = villager.level().getBlockState(feetPos);
        BlockState stateHeadPos = villager.level().getBlockState(headPos);

        if (isPathBlocked(stateFeetPos) && isMinable(stateFeetPos) &&
                !(stateFeetPos.getBlock() instanceof net.minecraft.world.level.block.BedBlock) &&
                !(stateFeetPos.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
            if (blockedUntil.containsKey(feetPos) && blockedUntil.get(feetPos) > now) return false;
            this.targetBlockPos = feetPos;
            this.isBridging = false;
            EconomyMod.LOGGER.info("[MINING] {} копает блок под ногами: {}", villager.getName().getString(), feetPos.toShortString());
            targetStartTime = villager.level().getGameTime();
            return true;
        }
        if (isPathBlocked(stateHeadPos) && isMinable(stateHeadPos) &&
                !(stateHeadPos.getBlock() instanceof net.minecraft.world.level.block.BedBlock) &&
                !(stateHeadPos.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
            if (blockedUntil.containsKey(headPos) && blockedUntil.get(headPos) > now) return false;
            this.targetBlockPos = headPos;
            this.isBridging = false;
            EconomyMod.LOGGER.info("[MINING] {} копает блок на уровне головы: {}", villager.getName().getString(), headPos.toShortString());
            targetStartTime = villager.level().getGameTime();
            return true;
        }

        if (activeVeinBlockPos != null) {
            BlockState state = villager.level().getBlockState(activeVeinBlockPos);
            if (isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(activeVeinBlockPos, att) && hasLineOfSightToBlock(activeVeinBlockPos)) {
                if (blockedUntil.containsKey(activeVeinBlockPos) && blockedUntil.get(activeVeinBlockPos) > now) {
                    this.activeVeinBlockPos = null;
                } else {
                    this.targetBlockPos = activeVeinBlockPos;
                    this.isBridging = false;
                    EconomyMod.LOGGER.info("[MINING] {} продолжает жилу в {}", villager.getName().getString(), activeVeinBlockPos.toShortString());
                    targetStartTime = villager.level().getGameTime();
                    return true;
                }
            } else {
                this.activeVeinBlockPos = null;
            }
        }

        BlockPos chestPos = att.getPersonalChestPos();
        if (chestPos == null) {
            BlockPos cur = villager.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(cur.offset(-8, -2, -8), cur.offset(8, 2, 8))) {
                if (villager.level().getBlockState(pos).is(Blocks.CHEST)) {
                    att.setPersonalChestPos(pos.immutable());
                    chestPos = pos.immutable();
                    EconomyMod.LOGGER.info("[MINING] {} нашёл сундук для шахты: {}", villager.getName().getString(), chestPos.toShortString());
                    BlockPos startPos = chestPos.offset(0, 0, 1);
                    this.startY = startPos.getY();
                    att.setMiningStartY(this.startY);
                    break;
                }
            }
        }

        if (chestPos != null && currentStep >= 98) {
            att.setPersonalChestPos(null);
            att.setMiningStartY(-1);
            this.startY = -1;
            chestPos = null;
            EconomyMod.LOGGER.info("[MINING] {} завершил шахту (98 шагов), ищет новый сундук", villager.getName().getString());
        }

        BlockPos startPos = (chestPos != null) ? chestPos.offset(0, 0, 1) : villager.blockPosition().immutable();
        if (this.startY == -1) {
            this.startY = startPos.getY();
            att.setMiningStartY(this.startY);
        }
        int x = startPos.getX();

        for (int n = 0; n < 100; n++) {
            int z = startPos.getZ() + n;
            int floorY = (chestPos != null) ? ((n < 3) ? (startPos.getY() - 1) : (startPos.getY() - ((n - 3) / 2) - 1)) : (startPos.getY() - (n / 2) - 1);
            BlockPos feetL = new BlockPos(x, floorY + 1, z);
            BlockPos belowL = feetL.below();
            BlockPos feetR = new BlockPos(x + 1, floorY + 1, z);
            BlockPos belowR = feetR.below();

            if (isPathPassable(villager.level(), feetL) && isHazardousOrVoid(villager.level(), belowL)) {
                if (!recentlyDestroyed.contains(belowL) && !(blockedUntil.containsKey(belowL) && blockedUntil.get(belowL) > now)) {
                    if (tryRequestBridge(belowL, att, false, n, "ЛЕВЫЙ ПОЛ")) {
                        targetStartTime = villager.level().getGameTime();
                        return true;
                    }
                }
            }
            if (isPathPassable(villager.level(), feetR) && isHazardousOrVoid(villager.level(), belowR)) {
                if (!recentlyDestroyed.contains(belowR) && !(blockedUntil.containsKey(belowR) && blockedUntil.get(belowR) > now)) {
                    if (tryRequestBridge(belowR, att, false, n, "ПРАВЫЙ ПОЛ")) {
                        targetStartTime = villager.level().getGameTime();
                        return true;
                    }
                }
            }

            if (n > 0) {
                if (isFluidOrUnstable(villager.level(), feetL)) {
                    if (!recentlyDestroyed.contains(feetL) && !(blockedUntil.containsKey(feetL) && blockedUntil.get(feetL) > now)) {
                        if (handleWaterIfNeeded(feetL, att, n, "ЛЕВЫЕ НОГИ")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), feetR)) {
                    if (!recentlyDestroyed.contains(feetR) && !(blockedUntil.containsKey(feetR) && blockedUntil.get(feetR) > now)) {
                        if (handleWaterIfNeeded(feetR, att, n, "ПРАВЫЕ НОГИ")) return false;
                    }
                }
            }

            BlockState stateFeetL = villager.level().getBlockState(feetL);
            BlockState stateHeadL = villager.level().getBlockState(feetL.above());
            BlockState stateCeilL = villager.level().getBlockState(feetL.above(2));
            BlockState stateFeetR = villager.level().getBlockState(feetR);
            BlockState stateHeadR = villager.level().getBlockState(feetR.above());
            BlockState stateCeilR = villager.level().getBlockState(feetR.above(2));

            if (isPathBlocked(stateFeetL) || isPathBlocked(stateHeadL) || isPathBlocked(stateCeilL) ||
                    isPathBlocked(stateFeetR) || isPathBlocked(stateHeadR) || isPathBlocked(stateCeilR)) {
                break;
            }
        }

        for (int n = 0; n < 100; n++) {
            int z = startPos.getZ() + n;
            int floorY = (chestPos != null) ? ((n < 3) ? (startPos.getY() - 1) : (startPos.getY() - ((n - 3) / 2) - 1)) : (startPos.getY() - (n / 2) - 1);
            BlockPos feetL = new BlockPos(x, floorY + 1, z);
            BlockPos headL = feetL.above();
            BlockPos ceilL = feetL.above(2);
            BlockPos belowL = feetL.below();
            BlockPos feetR = new BlockPos(x + 1, floorY + 1, z);
            BlockPos headR = feetR.above();
            BlockPos ceilR = feetR.above(2);
            BlockPos belowR = feetR.below();

            if (isPathPassable(villager.level(), feetL) && isHazardousOrVoid(villager.level(), belowL)) {
                if (!recentlyDestroyed.contains(belowL) && !(blockedUntil.containsKey(belowL) && blockedUntil.get(belowL) > now)) {
                    if (tryRequestBridge(belowL, att, false, n, "ЛЕВЫЙ ПОЛ")) {
                        targetStartTime = villager.level().getGameTime();
                        return true;
                    }
                }
            }
            if (isPathPassable(villager.level(), feetR) && isHazardousOrVoid(villager.level(), belowR)) {
                if (!recentlyDestroyed.contains(belowR) && !(blockedUntil.containsKey(belowR) && blockedUntil.get(belowR) > now)) {
                    if (tryRequestBridge(belowR, att, false, n, "ПРАВЫЙ ПОЛ")) {
                        targetStartTime = villager.level().getGameTime();
                        return true;
                    }
                }
            }

            if (n > 0) {
                if (isFluidOrUnstable(villager.level(), feetL)) {
                    if (!recentlyDestroyed.contains(feetL) && !(blockedUntil.containsKey(feetL) && blockedUntil.get(feetL) > now)) {
                        if (handleWaterIfNeeded(feetL, att, n, "ЛЕВЫЕ НОГИ (Ц2)")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), feetR)) {
                    if (!recentlyDestroyed.contains(feetR) && !(blockedUntil.containsKey(feetR) && blockedUntil.get(feetR) > now)) {
                        if (handleWaterIfNeeded(feetR, att, n, "ПРАВЫЕ НОГИ (Ц2)")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), headL)) {
                    if (!recentlyDestroyed.contains(headL) && !(blockedUntil.containsKey(headL) && blockedUntil.get(headL) > now)) {
                        if (handleWaterIfNeeded(headL, att, n, "ЛЕВАЯ ГОЛОВА (Ц2)")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), headR)) {
                    if (!recentlyDestroyed.contains(headR) && !(blockedUntil.containsKey(headR) && blockedUntil.get(headR) > now)) {
                        if (handleWaterIfNeeded(headR, att, n, "ПРАВАЯ ГОЛОВА (Ц2)")) return false;
                    }
                }
            }

            BlockState stateFeetL = villager.level().getBlockState(feetL);
            BlockState stateHeadL = villager.level().getBlockState(headL);
            BlockState stateCeilL = villager.level().getBlockState(ceilL);
            BlockState stateFeetR = villager.level().getBlockState(feetR);
            BlockState stateHeadR = villager.level().getBlockState(headR);
            BlockState stateCeilR = villager.level().getBlockState(ceilR);

            if (n >= 12 && n % 6 == 0 && !isPathBlocked(stateFeetL) && !isPathBlocked(stateHeadL) && !isPathBlocked(stateCeilL) &&
                    villager.level().getBlockState(ceilL.above()).isSolid()) {
                BlockPos branchTarget = scanHorizontalBranch(x, floorY, z, 1, pickaxe);
                if (branchTarget != null && !recentlyDestroyed.contains(branchTarget) && !(blockedUntil.containsKey(branchTarget) && blockedUntil.get(branchTarget) > now)) {
                    this.targetBlockPos = branchTarget;
                    EconomyMod.LOGGER.info("[MINING] {} начал горизонтальное ответвление вправо", villager.getName().getString());
                    targetStartTime = villager.level().getGameTime();
                    return true;
                }
                branchTarget = scanHorizontalBranch(x, floorY, z, -1, pickaxe);
                if (branchTarget != null && !recentlyDestroyed.contains(branchTarget) && !(blockedUntil.containsKey(branchTarget) && blockedUntil.get(branchTarget) > now)) {
                    this.targetBlockPos = branchTarget;
                    EconomyMod.LOGGER.info("[MINING] {} начал горизонтальное ответвление влево", villager.getName().getString());
                    targetStartTime = villager.level().getGameTime();
                    return true;
                }
            }

            BlockState stateBelowL = villager.level().getBlockState(belowL);
            BlockState stateBelowR = villager.level().getBlockState(belowR);
            if (stateBelowL.is(Tags.Blocks.ORES) && isMinable(stateBelowL) && isSafeToMine(belowL, att) && !recentlyDestroyed.contains(belowL) && !(blockedUntil.containsKey(belowL) && blockedUntil.get(belowL) > now)) {
                this.targetBlockPos = belowL.immutable();
                EconomyMod.LOGGER.info("[MINING] {} копает руду под ногами слева: {}", villager.getName().getString(), belowL.toShortString());
                targetStartTime = villager.level().getGameTime();
                return true;
            }
            if (stateBelowR.is(Tags.Blocks.ORES) && isMinable(stateBelowR) && isSafeToMine(belowR, att) && !recentlyDestroyed.contains(belowR) && !(blockedUntil.containsKey(belowR) && blockedUntil.get(belowR) > now)) {
                this.targetBlockPos = belowR.immutable();
                EconomyMod.LOGGER.info("[MINING] {} копает руду под ногами справа: {}", villager.getName().getString(), belowR.toShortString());
                targetStartTime = villager.level().getGameTime();
                return true;
            }

            if (isPathBlocked(stateFeetL) || isPathBlocked(stateHeadL) || isPathBlocked(stateCeilL) ||
                    isPathBlocked(stateFeetR) || isPathBlocked(stateHeadR) || isPathBlocked(stateCeilR)) {
                this.currentStep = n;
                this.isBridging = false;

                int nextZ = z + 1;
                int nextFloorY = (chestPos != null) ? (((n + 1) < 3) ? (startPos.getY() - 1) : (startPos.getY() - (((n + 1) - 3) / 2) - 1)) : (startPos.getY() - ((n + 1) / 2) - 1);
                BlockPos nextFeetL = new BlockPos(x, nextFloorY + 1, nextZ);
                BlockPos nextFeetR = new BlockPos(x + 1, nextFloorY + 1, nextZ);

                if (isFluidOrUnstable(villager.level(), nextFeetL)) {
                    if (!recentlyDestroyed.contains(nextFeetL) && !(blockedUntil.containsKey(nextFeetL) && blockedUntil.get(nextFeetL) > now)) {
                        if (handleWaterIfNeeded(nextFeetL, att, n + 1, "УПРЕЖДАЮЩИЙ ЛЕВЫЙ ПОЛ")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), nextFeetR)) {
                    if (!recentlyDestroyed.contains(nextFeetR) && !(blockedUntil.containsKey(nextFeetR) && blockedUntil.get(nextFeetR) > now)) {
                        if (handleWaterIfNeeded(nextFeetR, att, n + 1, "УПРЕЖДАЮЩИЙ ПРАВЫЙ ПОЛ")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), nextFeetL.above())) {
                    if (!recentlyDestroyed.contains(nextFeetL.above()) && !(blockedUntil.containsKey(nextFeetL.above()) && blockedUntil.get(nextFeetL.above()) > now)) {
                        if (handleWaterIfNeeded(nextFeetL.above(), att, n + 1, "УПРЕЖДАЮЩАЯ ЛЕВАЯ ГОЛОВА")) return false;
                    }
                }
                if (isFluidOrUnstable(villager.level(), nextFeetR.above())) {
                    if (!recentlyDestroyed.contains(nextFeetR.above()) && !(blockedUntil.containsKey(nextFeetR.above()) && blockedUntil.get(nextFeetR.above()) > now)) {
                        if (handleWaterIfNeeded(nextFeetR.above(), att, n + 1, "УПРЕЖДАЮЩАЯ ПРАВАЯ ГОЛОВА")) return false;
                    }
                }

                if (isPathBlocked(stateCeilL) && isMinable(stateCeilL) && isSafeToMine(ceilL, att) && !recentlyDestroyed.contains(ceilL) && !(blockedUntil.containsKey(ceilL) && blockedUntil.get(ceilL) > now)) this.targetBlockPos = ceilL;
                else if (isPathBlocked(stateCeilR) && isMinable(stateCeilR) && isSafeToMine(ceilR, att) && !recentlyDestroyed.contains(ceilR) && !(blockedUntil.containsKey(ceilR) && blockedUntil.get(ceilR) > now)) this.targetBlockPos = ceilR;
                else if (isPathBlocked(stateHeadL) && isMinable(stateHeadL) && isSafeToMine(headL, att) && !recentlyDestroyed.contains(headL) && !(blockedUntil.containsKey(headL) && blockedUntil.get(headL) > now)) this.targetBlockPos = headL;
                else if (isPathBlocked(stateHeadR) && isMinable(stateHeadR) && isSafeToMine(headR, att) && !recentlyDestroyed.contains(headR) && !(blockedUntil.containsKey(headR) && blockedUntil.get(headR) > now)) this.targetBlockPos = headR;
                else if (isPathBlocked(stateFeetL) && isMinable(stateFeetL) && isSafeToMine(feetL, att) && !recentlyDestroyed.contains(feetL) && !(blockedUntil.containsKey(feetL) && blockedUntil.get(feetL) > now)) this.targetBlockPos = feetL;
                else if (isPathBlocked(stateFeetR) && isMinable(stateFeetR) && isSafeToMine(feetR, att) && !recentlyDestroyed.contains(feetR) && !(blockedUntil.containsKey(feetR) && blockedUntil.get(feetR) > now)) this.targetBlockPos = feetR;

                if (this.targetBlockPos != null) {
                    EconomyMod.LOGGER.info("[MINING] {} копает препятствие: {}", villager.getName().getString(), this.targetBlockPos.toShortString());
                    targetStartTime = villager.level().getGameTime();
                }
                return this.targetBlockPos != null;
            }
        }
        return false;
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
                EconomyMod.LOGGER.info("[MINING] {} строит мост ({}) шаг {}: {}", villager.getName().getString(), label, n, pos.toShortString());
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
                EconomyMod.LOGGER.info("[MINING] {} закупоривает жидкость ({}) шаг {}: {}", villager.getName().getString(), label, n, pos.toShortString());
            }
            return true;
        }
        return false;
    }

    private BlockPos scanHorizontalBranch(int startX, int y, int z, int dirX, ItemStack pickaxe) {
        for (int b = 1; b <= 16; b++) {
            int cx = startX + (b * dirX);
            BlockPos bFloor = new BlockPos(cx, y, z);
            BlockPos bHead = new BlockPos(cx, y + 1, z);
            BlockPos bCeiling = new BlockPos(cx, y + 2, z);
            BlockState stateFloor = villager.level().getBlockState(bFloor);
            BlockState stateHead = villager.level().getBlockState(bHead);
            BlockState stateClass = villager.level().getBlockState(bCeiling);
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
            EconomyMod.LOGGER.info("[MINING] {} начал копку. Цель: {}", villager.getName().getString(), targetBlockPos.toShortString());
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
            if (villager.level().getGameTime() - lastNoTargetLog > 100) {
                EconomyMod.LOGGER.info("[MINING] {} нет цели, стоит на месте", villager.getName().getString());
                lastNoTargetLog = villager.level().getGameTime();
            }
            return;
        }

        long nowGame = villager.level().getGameTime();
        if (nowGame - targetStartTime > TARGET_TIMEOUT_TICKS && breakProgress == 0) {
            EconomyMod.LOGGER.warn("[MINING] {} таймаут цели {}, сброс", villager.getName().getString(), targetBlockPos.toShortString());
            int fails = failCount.getOrDefault(targetBlockPos, 0) + 1;
            failCount.put(targetBlockPos, fails);
            if (fails >= MAX_FAILS) {
                blockedUntil.put(targetBlockPos, nowGame + BLOCK_DURATION_TICKS);
                EconomyMod.LOGGER.warn("[MINING] {} блокируем цель {} на {} тиков", villager.getName().getString(), targetBlockPos.toShortString(), BLOCK_DURATION_TICKS);
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
                    EconomyMod.LOGGER.warn("[MINING] {} застрял на месте, сброс цели {}", villager.getName().getString(), targetBlockPos);
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

        if (villager.level().getGameTime() % 100 == 0) {
            EconomyMod.LOGGER.debug("[MINING] {} смотрит на {}, позиция {}, цель {}",
                    villager.getName().getString(),
                    villager.getLookControl().getWantedX() + ", " + villager.getLookControl().getWantedY() + ", " + villager.getLookControl().getWantedZ(),
                    villager.blockPosition().toShortString(),
                    targetBlockPos != null ? targetBlockPos.toShortString() : "null");
        }

        if (villager.getBrain() != null) {
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.INTERACTION_TARGET);
        }

        if (breakProgress > maxBreakTicks + 120) {
            EconomyMod.LOGGER.warn("[MINING] {} ЗАСТРЯЛ! Прогресс {}, макс {}, целевой блок {}",
                    villager.getName().getString(), breakProgress, maxBreakTicks,
                    targetBlockPos != null ? targetBlockPos.toShortString() : "null");
            stop();
            this.targetBlockPos = null;
            this.activeVeinBlockPos = null;
            return;
        }

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        BlockPos standPos = findSafeStandPos(targetBlockPos);

        if (startY != -1 && standPos.getY() > startY + 5) {
            EconomyMod.LOGGER.warn("[MINING] {} standPos выше разрешённого ({} > {}), сброс цели",
                    villager.getName().getString(), standPos.getY(), startY + 5);
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
                    EconomyMod.LOGGER.warn("[MINING] {} слишком много попыток выбраться из ямы для цели {}, сброс цели", villager.getName().getString(), targetBlockPos.toShortString());
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
                    EconomyMod.LOGGER.info("[MINING] {} выбрался из ямы (глубина {}), подмостив блок под себя (попытка {})",
                            villager.getName().getString(), standPos.getY() - feetPos.getY(), escapeAttempts);
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
                    EconomyMod.LOGGER.info("[MINING] {} завершил постройку моста/затычку в {}", villager.getName().getString(), targetBlockPos.toShortString());
                    this.targetBlockPos = null;
                    this.isBridging = false;
                    breakProgress = 0;
                    escapeAttempts = 0;
                    lastEscapeTarget = null;
                }
                return;
            } else {
                if (villager.level().getBlockState(targetBlockPos).isAir()) {
                    if (villager.level().getGameTime() % 100 == 0) {
                        EconomyMod.LOGGER.debug("[MINING] {} целевой блок исчез, сброс", villager.getName().getString());
                    }
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
                EconomyMod.LOGGER.debug("[MINING] {} блок уже воздух, сброс цели", villager.getName().getString());
                recentlyDestroyed.add(targetBlockPos);
                this.targetBlockPos = null;
                return;
            }

            recentlyDestroyed.add(targetBlockPos.immutable());
            failCount.remove(targetBlockPos);
            blockedUntil.remove(targetBlockPos);

            EconomyMod.LOGGER.info("[MINING] {} разрушил блок {} в {}",
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
                EconomyMod.LOGGER.info("[MINING] {} нашёл следующий блок жилы над: {}", villager.getName().getString(), abovePos.toShortString());
            } else {
                this.activeVeinBlockPos = findAdjacentOre(targetBlockPos);
                if (this.activeVeinBlockPos != null) {
                    EconomyMod.LOGGER.info("[MINING] {} нашёл смежную руду: {}", villager.getName().getString(), this.activeVeinBlockPos.toShortString());
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
        EconomyMod.LOGGER.info("[MINING] {} остановил копку (цель сброшена)", villager.getName().getString());
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