package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import com.economymod.entity.ai.mining.BridgeBuilder;
import com.economymod.entity.ai.mining.TorchPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.EnumSet;
import java.util.List;

public class VillagerMiningGoal extends Goal {
    private final Villager villager;
    private BlockPos targetBlockPos;
    private BlockPos activeVeinBlockPos;
    private int breakProgress = 0;
    private int maxBreakTicks = 20;
    private int currentStep = 0;
    private boolean isBridging = false;

    public VillagerMiningGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().isNight()) return false;

        // КРИТИЧНЫЙ БЛОК: Сон полностью священен
        if (villager.isSleeping()) return false;

        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 120) {
            return false;
        }

        if (isEnemyNearby()) return false;

        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (prof != VillagerProfession.TOOLSMITH && prof != VillagerProfession.ARMORER && prof != VillagerProfession.WEAPONSMITH) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;

        ItemStack pickaxe = att.getActivePickaxe();
        if (pickaxe.isEmpty()) return false;

        // Защита от застревания внутри блоков (игнорируем кровати и сундуки)
        BlockPos feetPos = villager.blockPosition();
        BlockPos headPos = feetPos.above();
        BlockState stateFeetPos = villager.level().getBlockState(feetPos);
        BlockState stateHeadPos = villager.level().getBlockState(headPos);

        if (isPathBlocked(stateFeetPos) && isMinable(stateFeetPos) &&
                !(stateFeetPos.getBlock() instanceof net.minecraft.world.level.block.BedBlock) &&
                !(stateFeetPos.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
            this.targetBlockPos = feetPos;
            this.isBridging = false;
            return true;
        }
        if (isPathBlocked(stateHeadPos) && isMinable(stateHeadPos) &&
                !(stateHeadPos.getBlock() instanceof net.minecraft.world.level.block.BedBlock) &&
                !(stateHeadPos.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
            this.targetBlockPos = headPos;
            this.isBridging = false;
            return true;
        }

        boolean shouldLog = (villager.level().getGameTime() % 100 == 0);

        if (activeVeinBlockPos != null) {
            BlockState state = villager.level().getBlockState(activeVeinBlockPos);
            if (isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(activeVeinBlockPos, att) && hasLineOfSightToBlock(activeVeinBlockPos)) {
                this.targetBlockPos = activeVeinBlockPos;
                this.isBridging = false;
                return true;
            } else {
                this.activeVeinBlockPos = null;
            }
        }

        // Сверх-приоритет: сканирование обнаженных руд в сфере 10 блоков (с проверкой видимости)
        if (villager.level().getGameTime() % 40 == 0 || targetBlockPos != null) {
            BlockPos current = villager.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -5, -10), current.offset(10, 5, 10))) {
                BlockState state = villager.level().getBlockState(pos);
                if (state.is(Tags.Blocks.ORES) && isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(pos, att)) {
                    if (isOreExposed(pos) && hasLineOfSightToBlock(pos)) {
                        this.targetBlockPos = pos.immutable();
                        this.isBridging = false;
                        if (shouldLog) com.economymod.EconomyMod.LOGGER.info("МАЙНИНГ ДЕБАГ: Обнаружена руда в поле зрения: {}", pos.toShortString());
                        return true;
                    }
                }
            }
        }

        BlockPos chestPos = att.getPersonalChestPos();
        if (chestPos == null) {
            BlockPos cur = villager.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(cur.offset(-8, -2, -8), cur.offset(8, 2, 8))) {
                if (villager.level().getBlockState(pos).is(Blocks.CHEST)) {
                    att.setPersonalChestPos(pos.immutable());
                    chestPos = pos.immutable();
                    break;
                }
            }
        }

        // ИСПРАВЛЕНО (Умная отвязка): Если мы полностью раскопали туннель на 100 блоков и копать больше нечего — автоматически отвязываемся от этого сундука!
        // Это позволит жителю уйти в город заниматься другими делами, а игроку — переставить сундук в новую гору для новой шахты
        if (chestPos != null && currentStep >= 98) {
            att.setPersonalChestPos(null);
            chestPos = null;
            if (shouldLog) com.economymod.EconomyMod.LOGGER.info("МАЙНИНГ: Шахта на 100 блоков полностью завершена! Сбрасываю привязку к сундуку.");
        }

        BlockPos startPos = (chestPos != null) ? chestPos.offset(0, 0, 1) : villager.blockPosition().immutable();
        int x = startPos.getX();

        // Цикл 1: Ремонт пола и устранение протечек
        for (int n = 0; n < 100; n++) {
            int z = startPos.getZ() + n;
            int floorY = (chestPos != null) ? ((n < 3) ? (startPos.getY() - 1) : (startPos.getY() - ((n - 3) / 2) - 1)) : (startPos.getY() - (n / 2) - 1);

            BlockPos feetL = new BlockPos(x, floorY + 1, z);
            BlockPos belowL = feetL.below();
            BlockPos feetR = new BlockPos(x + 1, floorY + 1, z);
            BlockPos belowR = feetR.below();

            if (isPathPassable(villager.level(), feetL) && isHazardousOrVoid(villager.level(), belowL)) {
                return tryRequestBridge(belowL, att, shouldLog, n, "ЛЕВЫЙ ПОЛ");
            }
            if (isPathPassable(villager.level(), feetR) && isHazardousOrVoid(villager.level(), belowR)) {
                return tryRequestBridge(belowR, att, shouldLog, n, "ПРАВЫЙ ПОЛ");
            }

            if (n > 0) {
                if (isFluidOrUnstable(villager.level(), feetL)) return tryPlugBlock(feetL, att, shouldLog, n, "ЛЕВЫЕ НОГИ");
                if (isFluidOrUnstable(villager.level(), feetR)) return tryPlugBlock(feetR, att, shouldLog, n, "ПРАВЫЕ НОГИ");
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

        // Цикл 2: Расширение шахты вперед (2x3 туннель)
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
            BlockPos MathR = feetR.below();

            if (isPathPassable(villager.level(), feetL) && isHazardousOrVoid(villager.level(), belowL)) {
                return tryRequestBridge(belowL, att, shouldLog, n, "ЛЕВЫЙ ПОЛ");
            }
            if (isPathPassable(villager.level(), feetR) && isHazardousOrVoid(villager.level(), MathR)) {
                return tryRequestBridge(MathR, att, shouldLog, n, "ПРАВЫЙ ПОЛ");
            }

            if (n > 0) {
                if (isFluidOrUnstable(villager.level(), feetL)) return tryPlugBlock(feetL, att, shouldLog, n, "ЛЕВЫЕ НОГИ (Ц2)");
                if (isFluidOrUnstable(villager.level(), feetR)) return tryPlugBlock(feetR, att, shouldLog, n, "ПРАВЫЕ НОГИ (Ц2)");
                if (isFluidOrUnstable(villager.level(), headL)) return tryPlugBlock(headL, att, shouldLog, n, "ЛЕВАЯ ГОЛОВА (Ц2)");
                if (isFluidOrUnstable(villager.level(), headR)) return tryPlugBlock(headR, att, shouldLog, n, "ПРАВАЯ ГОЛОВА (Ц2)");
            }

            BlockState stateFeetL = villager.level().getBlockState(feetL);
            BlockState stateHeadL = villager.level().getBlockState(headL);
            BlockState stateCeilL = villager.level().getBlockState(ceilL);
            BlockState stateFeetR = villager.level().getBlockState(feetR);
            BlockState stateHeadR = villager.level().getBlockState(headR);
            BlockState stateCeilR = villager.level().getBlockState(ceilR);

            if (n >= 12 && n % 6 == 0 &&
                    !isPathBlocked(stateFeetL) && !isPathBlocked(stateHeadL) && !isPathBlocked(stateCeilL) &&
                    villager.level().getBlockState(ceilL.above()).isSolid()) {

                BlockPos branchTarget = scanHorizontalBranch(x, floorY, z, 1, pickaxe);
                if (branchTarget != null) { this.targetBlockPos = branchTarget; return true; }
                branchTarget = scanHorizontalBranch(x, floorY, z, -1, pickaxe);
                if (branchTarget != null) { this.targetBlockPos = branchTarget; return true; }
            }

            // Автоматическое выкапывание руд в полу туннеля под ногами
            BlockState stateBelowL = villager.level().getBlockState(belowL);
            BlockState stateBelowR = villager.level().getBlockState(MathR);
            if (stateBelowL.is(Tags.Blocks.ORES) && isMinable(stateBelowL) && isSafeToMine(belowL, att)) {
                this.targetBlockPos = belowL.immutable();
                return true;
            }
            if (stateBelowR.is(Tags.Blocks.ORES) && isMinable(stateBelowR) && isSafeToMine(MathR, att)) {
                this.targetBlockPos = MathR.immutable();
                return true;
            }

            if (isPathBlocked(stateFeetL) || isPathBlocked(stateHeadL) || isPathBlocked(stateCeilL) ||
                    isPathBlocked(stateFeetR) || isPathBlocked(stateHeadR) || isPathBlocked(stateCeilR)) {

                this.currentStep = n;
                this.isBridging = false;

                // Упреждающая защита от затопления (peek-ahead n + 1)
                int nextZ = z + 1;
                int nextFloorY = (chestPos != null) ? (((n + 1) < 3) ? (startPos.getY() - 1) : (startPos.getY() - (((n + 1) - 3) / 2) - 1)) : (startPos.getY() - ((n + 1) / 2) - 1);
                BlockPos nextFeetL = new BlockPos(x, nextFloorY + 1, nextZ);
                BlockPos nextFeetR = new BlockPos(x + 1, nextFloorY + 1, nextZ);

                if (isFluidOrUnstable(villager.level(), nextFeetL)) return tryPlugBlock(nextFeetL, att, shouldLog, n + 1, "УПРЕЖДАЮЩИЙ ЛЕВЫЙ ПОЛ");
                if (isFluidOrUnstable(villager.level(), nextFeetR)) return tryPlugBlock(nextFeetR, att, shouldLog, n + 1, "УПРЕЖДАЮЩИЙ ПРАВЫЙ ПОЛ");
                if (isFluidOrUnstable(villager.level(), nextFeetL.above())) return tryPlugBlock(nextFeetL.above(), att, shouldLog, n + 1, "УПРЕЖДАЮЩАЯ ЛЕВАЯ ГОЛОВА");
                if (isFluidOrUnstable(villager.level(), nextFeetR.above())) return tryPlugBlock(nextFeetR.above(), att, shouldLog, n + 1, "УПРЕЖДАЮЩАЯ ПРАВАЯ ГОЛОВА");

                // Копаем блоки сечения 2х3 сверху-вниз
                if (isPathBlocked(stateCeilL) && isMinable(stateCeilL) && isSafeToMine(ceilL, att)) this.targetBlockPos = ceilL;
                else if (isPathBlocked(stateCeilR) && isMinable(stateCeilR) && isSafeToMine(ceilR, att)) this.targetBlockPos = ceilR;
                else if (isPathBlocked(stateHeadL) && isMinable(stateHeadL) && isSafeToMine(headL, att)) this.targetBlockPos = headL;
                else if (isPathBlocked(stateHeadR) && isMinable(stateHeadR) && isSafeToMine(headR, att)) this.targetBlockPos = headR;
                else if (isPathBlocked(stateFeetL) && isMinable(stateFeetL) && isSafeToMine(feetL, att)) this.targetBlockPos = feetL;
                else if (isPathBlocked(stateFeetR) && isMinable(stateFeetR) && isSafeToMine(feetR, att)) this.targetBlockPos = feetR;

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
        ClipContext context = new ClipContext(
                eyePos,
                targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                villager
        );
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
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
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
        if (isCollidingWithVillager(pos)) return false;
        ItemStack block = BridgeBuilder.getPlaceableBlock(att);
        if (!block.isEmpty()) {
            this.targetBlockPos = pos.immutable();
            this.isBridging = true;
            return true;
        }
        return false;
    }

    private boolean tryPlugBlock(BlockPos pos, VillagerAttachment att, boolean shouldLog, int n, String label) {
        if (isCollidingWithVillager(pos)) return false;
        ItemStack bridgeBlock = BridgeBuilder.getPlaceableBlock(att);
        if (!bridgeBlock.isEmpty()) {
            this.targetBlockPos = pos.immutable();
            this.isBridging = true;
            if (shouldLog) com.economymod.EconomyMod.LOGGER.info("МАЙНИНГ: Закупориваем жидкость ({}) на шаге {}: {}", label, n, pos.toShortString());
            return true;
        }
        return false;
    }

    private BlockPos scanExposedTunnelWalls(BlockPos floor, BlockPos head, BlockPos ceiling) {
        BlockPos[] checkPositions = {
                floor.west(), floor.east(),
                head.west(), head.east(),
                ceiling.west(), ceiling.east(), ceiling.above()
        };

        for (BlockPos pos : checkPositions) {
            BlockState state = villager.level().getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) && isMinable(state)) {
                return pos.immutable();
            }
        }
        return null;
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
        if (state.getDestroySpeed(villager.level(), BlockPos.ZERO) < 0) {
            return false;
        }
        return !state.is(Blocks.BEDROCK) && !state.is(Blocks.CHEST) &&
                !state.is(Blocks.WATER) && !state.is(Blocks.LAVA) &&
                !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH) &&
                !state.is(Blocks.SOUL_TORCH) && !state.is(Blocks.SOUL_WALL_TORCH) &&
                !state.is(Blocks.LADDER);
    }

    private boolean isSafeToMine(BlockPos pos, VillagerAttachment att) {
        if (att != null && !BridgeBuilder.getPlaceableBlock(att).isEmpty()) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockState adjState = villager.level().getBlockState(pos.relative(dir));
            if (!adjState.getFluidState().isEmpty()) {
                return false;
            }
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
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = target.relative(dir);
            if (villager.level().getBlockState(p).isAir() &&
                    villager.level().getBlockState(p.above()).isAir() &&
                    villager.level().getBlockState(p.below()).isSolid()) {
                return p.immutable();
            }
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = target.below().relative(dir);
            if (villager.level().getBlockState(p).isAir() &&
                    villager.level().getBlockState(p.above()).isAir() &&
                    villager.level().getBlockState(p.below()).isSolid()) {
                return p.immutable();
            }
        }
        for (Direction dir : Direction.values()) {
            BlockPos p = target.relative(dir);
            if (villager.level().getBlockState(p).isAir() && villager.level().getBlockState(p.below()).isSolid()) {
                return p.immutable();
            }
        }
        return target.immutable();
    }

    @Override
    public void start() {
        this.breakProgress = 0;
        calculateBreakDuration();

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att != null) {
            ItemStack pickaxe = att.getActivePickaxe();
            if (!pickaxe.isEmpty()) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, pickaxe.copy());
            }
        }
    }

    private void calculateBreakDuration() {
        if (targetBlockPos == null) return;
        BlockState state = villager.level().getBlockState(targetBlockPos);

        if (state.isAir()) {
            this.maxBreakTicks = 10;
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
        if (targetBlockPos == null) return;

        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.INTERACTION_TARGET);

        if (breakProgress > maxBreakTicks + 120) {
            com.economymod.EconomyMod.LOGGER.warn("МАЙНИНГ ВАТЧДОГ: Житель {} застрял при копке блока на {}. Сброс цели.",
                    villager.getName().getString(), targetBlockPos.toShortString());
            stop();
            this.targetBlockPos = null;
            this.activeVeinBlockPos = null;
            return;
        }

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        BlockPos standPos = findSafeStandPos(targetBlockPos);

        BlockPos feetPos = villager.blockPosition();
        if (feetPos.getY() < standPos.getY() && villager.onGround() && att != null) {
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

                    com.economymod.EconomyMod.LOGGER.info("МАЙНИНГ: Житель {} выбрался из ямы, подмостив под себя блок!", villager.getName().getString());
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
                    this.targetBlockPos = null;
                    this.isBridging = false;
                    breakProgress = 0;
                }
                return;
            } else {
                if (villager.level().getBlockState(targetBlockPos).isAir()) {
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
            } else {
                this.activeVeinBlockPos = findAdjacentOre(targetBlockPos);
            }

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
                    com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА: У жителя {} сломалась кирка!", villager.getName().getString());

                    this.activeVeinBlockPos = null;
                    villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
        }
    }

    private BlockPos findAdjacentOre(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos adj = pos.relative(dir);
            BlockState state = villager.level().getBlockState(adj);

            if (state.is(Tags.Blocks.ORES)) {
                VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
                if (att != null && att.getPersonalChestPos() != null) {
                    BlockPos startPos = att.getPersonalChestPos().offset(0, 0, 1);
                    int relativeZ = adj.getZ() - startPos.getZ();

                    int floorY = (relativeZ < 3) ? (startPos.getY() - 1) : (startPos.getY() - ((relativeZ - 3) / 2) - 1);
                    if (Math.abs(adj.getX() - startPos.getX()) <= 4) {
                        return adj.immutable();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        if (targetBlockPos != null && villager.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);
        }
        this.targetBlockPos = null;
        this.breakProgress = 0;
        this.isBridging = false;

        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.level().isNight()) return false;

        if (villager.isSleeping()) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null || att.getActivePickaxe().isEmpty()) return false;

        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 120) {
            return false;
        }

        if (isEnemyNearby()) return false;

        return targetBlockPos != null && (isMinable(villager.level().getBlockState(targetBlockPos)) || villager.level().getBlockState(targetBlockPos).isAir());
    }
}