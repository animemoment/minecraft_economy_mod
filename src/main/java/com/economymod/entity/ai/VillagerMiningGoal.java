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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.EnumSet;

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

        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 100) {
            return false;
        }

        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (prof != VillagerProfession.TOOLSMITH && prof != VillagerProfession.ARMORER && prof != VillagerProfession.WEAPONSMITH) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;

        ItemStack pickaxe = att.getActivePickaxe();
        if (pickaxe.isEmpty()) return false;

        // ПРИОРИТЕТ 1: Копаем найденную жилу руды
        if (activeVeinBlockPos != null) {
            BlockState state = villager.level().getBlockState(activeVeinBlockPos);
            if (isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(activeVeinBlockPos)) {
                this.targetBlockPos = activeVeinBlockPos;
                this.isBridging = false;
                return true;
            }
        }

        // ИСПРАВЛЕНО: СВЕРХ-ПРИОРИТЕТ! Сканируем 3D-сферу 7х7х7 блоков вокруг ног жителя на ЛЮБЫЕ руды!
        // Если руда слева, справа, сверху — он бросит всё и пойдет копать её!
        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-3, -2, -3), current.offset(3, 3, 3))) {
            BlockState state = villager.level().getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) && isMinable(state) && isToolTiredCorrect(pickaxe, state) && isSafeToMine(pos)) {
                this.targetBlockPos = pos.immutable();
                this.isBridging = false;
                return true;
            }
        }

        // ИСПРАВЛЕНО: Делаем сундук НЕОБЯЗАТЕЛЬНЫМ. Если сундука нет, точкой старта шахты станут координаты спавна жителя!
        BlockPos chestPos = att.getPersonalChestPos();
        if (chestPos == null) {
            BlockPos cur = villager.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(cur.offset(-8, -2, -8), current.offset(8, 2, 8))) {
                if (villager.level().getBlockState(pos).is(Blocks.CHEST)) {
                    att.setPersonalChestPos(pos.immutable());
                    chestPos = pos.immutable();
                    break;
                }
            }
        }

        // Если сундука нет — копаем от ног жителя
        BlockPos startPos = (chestPos != null) ? chestPos.offset(0, 0, 3) : current.immutable();

        // Сканируем стены вырытой части на наличие обнаженной руды
        for (int n = 0; n < 100; n++) {
            int z = startPos.getZ() + n;
            int y = startPos.getY() - (n / 2);
            int x = startPos.getX();

            BlockPos floor = new BlockPos(x, y, z);
            BlockPos head = new BlockPos(x, y + 1, z);
            BlockPos ceiling = new BlockPos(x, y + 2, z);

            if (villager.level().getBlockState(floor).isAir() && villager.level().getBlockState(head).isAir() && villager.level().getBlockState(ceiling).isAir()) {
                BlockPos exposedOre = scanExposedTunnelWalls(floor, head, ceiling);
                if (exposedOre != null && isToolTiredCorrect(pickaxe, villager.level().getBlockState(exposedOre)) && isSafeToMine(exposedOre)) {
                    this.targetBlockPos = exposedOre;
                    this.isBridging = false;
                    return true;
                }
            } else {
                break;
            }
        }

        // Основная работа (Копаем туннель)
        for (int n = 0; n < 100; n++) {
            int z = startPos.getZ() + n;
            int y = startPos.getY() - (n / 2);
            int x = startPos.getX();

            BlockPos floor = new BlockPos(x, y, z);
            BlockPos head = new BlockPos(x, y + 1, z);
            BlockPos ceiling = new BlockPos(x, y + 2, z);

            BlockState stateFloor = villager.level().getBlockState(floor);
            BlockState stateHead = villager.level().getBlockState(head);
            BlockState stateCeiling = villager.level().getBlockState(ceiling);

            // ИСПРАВЛЕНО: Строим мост ВСЕГДА, когда на уровне пола ступени воздух (stateFloor.isAir())!
            // Это гарантирует ровный пол лестницы и защищает от проваливания вниз и петель "поставил-сломал"
            if (stateFloor.isAir()) {
                ItemStack bridgeBlock = getPlaceableBlock();
                if (!bridgeBlock.isEmpty()) {
                    this.targetBlockPos = floor;
                    this.isBridging = true;
                    return true;
                } else {
                    return false; // Нет блоков для моста — стоп шахта
                }
            }

            // Копаем боковые ветки «Ёлочки»
            if (n > 0 && n % 6 == 0 && stateFloor.isAir() && stateHead.isAir() && stateCeiling.isAir()) {
                BlockPos branchTarget = scanHorizontalBranch(x, y, z, 1, pickaxe);
                if (branchTarget != null) {
                    this.targetBlockPos = branchTarget;
                    this.isBridging = false;
                    return true;
                }
                branchTarget = scanHorizontalBranch(x, y, z, -1, pickaxe);
                if (branchTarget != null) {
                    this.targetBlockPos = branchTarget;
                    this.isBridging = false;
                    return true;
                }
            }

            // Копаем основную лестницу вниз (Копаем ТОЛЬКО потолок и голову, никогда не рубим блок пола!)
            if (!stateFloor.isAir() || !stateHead.isAir() || !stateCeiling.isAir()) {
                this.currentStep = n;
                this.isBridging = false;

                if (!stateCeiling.isAir() && isMinable(stateCeiling) && isSafeToMine(ceiling)) {
                    this.targetBlockPos = ceiling;
                } else if (!stateHead.isAir() && isMinable(stateHead) && isSafeToMine(head)) {
                    this.targetBlockPos = head;
                }
                return this.targetBlockPos != null;
            }
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
        for (int b = 1; b <= 8; b++) {
            int cx = startX + (b * dirX);
            BlockPos bFloor = new BlockPos(cx, y, z);
            BlockPos bHead = new BlockPos(cx, y + 1, z);
            BlockPos bCeiling = new BlockPos(cx, y + 2, z);

            BlockState stateFloor = villager.level().getBlockState(bFloor);
            BlockState stateHead = villager.level().getBlockState(bHead);
            BlockState stateCeiling = villager.level().getBlockState(bCeiling);

            if (!stateFloor.isAir() || !stateHead.isAir() || !stateCeiling.isAir()) {
                if (!stateCeiling.isAir() && isMinable(stateCeiling) && isToolTiredCorrect(pickaxe, stateCeiling) && isSafeToMine(bCeiling)) return bCeiling;
                if (!stateHead.isAir() && isMinable(stateHead) && isToolTiredCorrect(pickaxe, stateHead) && isSafeToMine(bHead)) return bHead;
                break;
            }
        }
        return null;
    }

    private boolean isMinable(BlockState state) {
        return !state.is(Blocks.BEDROCK) && !state.is(Blocks.CHEST) && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA);
    }

    private boolean isSafeToMine(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockState adjState = villager.level().getBlockState(pos.relative(dir));
            if (!adjState.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ИСПРАВЛЕНО: Разрешаем строить мосты из ЛЮБОГО твердого блока (включая Песок и Гравий в пустыне!)
    private ItemStack getPlaceableBlock() {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return ItemStack.EMPTY;
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            if (block != Blocks.AIR) {
                BlockState defaultState = block.defaultBlockState();
                if (defaultState.isSolid() &&
                        block != Blocks.CHEST && block != Blocks.CRAFTING_TABLE && block != Blocks.FURNACE) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack getTorchFromInventory() {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return ItemStack.EMPTY;
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.TORCH)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean isToolTiredCorrect(ItemStack pickaxe, BlockState state) {
        if (state.requiresCorrectToolForDrops()) {
            return pickaxe.isCorrectToolForDrops(state);
        }
        return true;
    }

    @Override
    public void start() {
        this.breakProgress = 0;
        calculateBreakDuration();
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

        villager.getLookControl().setLookAt(targetBlockPos.getX() + 0.5, targetBlockPos.getY() + 0.5, targetBlockPos.getZ() + 0.5);

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        BlockPos standPos;

        if (att != null && att.getPersonalChestPos() != null) {
            BlockPos startPos = att.getPersonalChestPos().offset(0, 0, 3);

            if (targetBlockPos.getX() > startPos.getX()) {
                standPos = new BlockPos(targetBlockPos.getX() - 1, targetBlockPos.getY(), targetBlockPos.getZ());
            } else if (targetBlockPos.getX() < startPos.getX()) {
                standPos = new BlockPos(targetBlockPos.getX() + 1, targetBlockPos.getY(), targetBlockPos.getZ());
            } else {
                int relativeZ = targetBlockPos.getZ() - 1 - startPos.getZ();
                int floorY = startPos.getY() - (relativeZ / 2);
                standPos = new BlockPos(targetBlockPos.getX(), floorY + 1, targetBlockPos.getZ() - 1);
            }
        } else {
            standPos = targetBlockPos.north();
        }

        if (villager.level().getGameTime() % 40 == 0) {
            ItemStack torch = getTorchFromInventory();
            if (!torch.isEmpty()) {
                BlockPos currentPos = villager.blockPosition();
                int light = villager.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, currentPos);
                if (light < 4 && villager.level().getBlockState(currentPos).isAir() && villager.level().getBlockState(currentPos.below()).isSolid()) {
                    villager.level().setBlockAndUpdate(currentPos, Blocks.TORCH.defaultBlockState());
                    torch.shrink(1);
                    villager.level().playSound(null, currentPos, net.minecraft.sounds.SoundEvents.WOOD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }
        }

        double dist = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);

        if (dist < 9.0) {
            villager.getNavigation().stop();
            breakProgress++;

            if (isBridging) {
                if (breakProgress >= maxBreakTicks) {
                    performPlaceBlock();
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

    private void performPlaceBlock() {
        ItemStack block = getPlaceableBlock();
        if (!block.isEmpty() && targetBlockPos != null) {
            net.minecraft.world.level.block.Block b = net.minecraft.world.level.block.Block.byItem(block.getItem());
            BlockState placeState = b.defaultBlockState();

            villager.level().setBlockAndUpdate(targetBlockPos, placeState);
            block.shrink(1);

            villager.level().playSound(null, targetBlockPos, net.minecraft.sounds.SoundEvents.STONE_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        this.targetBlockPos = null;
        this.isBridging = false;
    }

    private void performBreak() {
        if (villager.level() instanceof ServerLevel sl) {
            damageTool();

            sl.destroyBlock(targetBlockPos, true, villager);
            sl.destroyBlockProgress(villager.getId(), targetBlockPos, -1);

            this.activeVeinBlockPos = findAdjacentOre(targetBlockPos);

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
                    BlockPos startPos = att.getPersonalChestPos().offset(0, 0, 3);
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
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.level().isNight()) return false;

        if (villager.getLastHurtByMob() != null && villager.level().getGameTime() - villager.getLastHurtByMobTimestamp() < 100) {
            return false;
        }
        return targetBlockPos != null && (isMinable(villager.level().getBlockState(targetBlockPos)) || villager.level().getBlockState(targetBlockPos).isAir());
    }
}