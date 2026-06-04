package com.economymod.entity.ai;

import com.economymod.EconomyMod;
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
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillagerFarmingGoal extends Goal {
    private final Villager villager;
    private VillagerAttachment attachment;
    private BlockPos targetCropPos;
    private BlockPos targetSoilPos;
    private BlockPos targetWaterPos;
    private int workCooldown = 0;
    private int useBoneMealCooldown = 0;
    private boolean isPlacingWater = false;
    private static final int SEARCH_RADIUS = 20;

    // Системные переменные детектора застреваний и блеклиста грядок
    private final Map<BlockPos, Integer> failCount = new HashMap<>();
    private final Map<BlockPos, Long> blockedUntil = new HashMap<>();
    private static final int MAX_FAILS = 3;
    private static final long BLOCK_DURATION_TICKS = 600; // Черный список на 30 секунд
    private long targetStartTime = 0;
    private BlockPos lastTargetPos = null;

    public VillagerFarmingGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.getVillagerData().getProfession() != VillagerProfession.FARMER) return false;
        if (villager.level().isNight()) return false;
        if (villager.isSleeping()) return false;
        if (workCooldown > 0) {
            workCooldown--;
            return false;
        }
        attachment = villager.getData(ModAttachments.VILLAGER.get());
        if (attachment == null) return false;

        long now = villager.level().getGameTime();

        // 1. Очистка устаревшего черного списка грядок
        blockedUntil.entrySet().removeIf(entry -> entry.getValue() < now);

        // 2. Созревшие культуры
        BlockPos mature = findMatureCrop(now);
        if (mature != null) {
            targetCropPos = mature;
            targetSoilPos = null;
            targetWaterPos = null;
            return true;
        }

        ItemStack hoe = findHoe();
        if (hoe.isEmpty()) {
            workCooldown = 20;
            return false;
        }

        // 3. Удобрение костной мукой
        if (hasBoneMeal() && useBoneMealCooldown == 0) {
            BlockPos unripe = findUnripeCrop(now);
            if (unripe != null) {
                targetCropPos = unripe;
                return true;
            }
        }

        // 4. Сухие грядки (полив водой)
        BlockPos dryFarmland = findDryFarmland(now);
        if (dryFarmland != null && hasWaterBucket()) {
            targetWaterPos = dryFarmland;
            isPlacingWater = true;
            targetCropPos = null;
            targetSoilPos = null;
            return true;
        }

        // 5. Пустые грядки для посадки
        BlockPos emptyFarmland = findEmptyFarmland(now);
        ItemStack seeds = findSeeds();
        if (emptyFarmland != null && !seeds.isEmpty()) {
            targetSoilPos = emptyFarmland;
            targetCropPos = null;
            targetWaterPos = null;
            return true;
        }

        // 6. Пахота земли мотыгой
        BlockPos tillable = findTillableDirt(now);
        if (tillable != null) {
            targetSoilPos = tillable;
            targetCropPos = null;
            targetWaterPos = null;
            return true;
        }

        workCooldown = 20;
        return false;
    }

    @Override
    public void start() {
        this.lastTargetPos = null;
        this.targetStartTime = villager.level().getGameTime();

        if (targetCropPos != null) {
            villager.getNavigation().moveTo(targetCropPos.getX() + 0.5, targetCropPos.getY(), targetCropPos.getZ() + 0.5, 0.7);
        } else if (targetSoilPos != null) {
            villager.getNavigation().moveTo(targetSoilPos.getX() + 0.5, targetSoilPos.getY(), targetSoilPos.getZ() + 0.5, 0.7);
        } else if (targetWaterPos != null) {
            villager.getNavigation().moveTo(targetWaterPos.getX() + 0.5, targetWaterPos.getY(), targetWaterPos.getZ() + 0.5, 0.7);
        }
    }

    @Override
    public void tick() {
        long now = villager.level().getGameTime();
        BlockPos currentTarget = targetCropPos != null ? targetCropPos : (targetSoilPos != null ? targetSoilPos : targetWaterPos);

        // ИСПРАВЛЕНО: Таймаут пути увеличен до 30 секунд (600 тиков) во избежание ложных блокировок грядок
        if (currentTarget != null) {
            if (lastTargetPos == null || !lastTargetPos.equals(currentTarget)) {
                lastTargetPos = currentTarget.immutable();
                targetStartTime = now;
            } else {
                long elapsed = now - targetStartTime;
                if (elapsed > 600) { // Даем фермеру честные 30 секунд на сложный обход заборчиков
                    int fails = failCount.getOrDefault(currentTarget, 0) + 1;
                    failCount.put(currentTarget, fails);
                    if (fails >= MAX_FAILS) {
                        blockedUntil.put(currentTarget, now + BLOCK_DURATION_TICKS);
                        EconomyMod.LOGGER.debug("[FARMING] Грядка {} заблокирована в черном списке фермера на 30 секунд.", currentTarget.toShortString());
                    }
                    stop(); // Сброс цели
                    workCooldown = 40;
                    return;
                }
            }
        } else {
            lastTargetPos = null;
        }

        if (targetCropPos != null) handleCrop();
        else if (targetSoilPos != null) handleSoil();
        else if (targetWaterPos != null) handleWater();
        else stop();
    }

    private void handleCrop() {
        if (!villager.level().isLoaded(targetCropPos)) { stop(); return; }
        double dist = villager.distanceToSqr(targetCropPos.getX() + 0.5, targetCropPos.getY(), targetCropPos.getZ() + 0.5);
        if (dist < 3.0) {
            villager.getNavigation().stop();
            BlockState state = villager.level().getBlockState(targetCropPos);
            if (isMatureCrop(state)) {
                harvestCrop(targetCropPos);
                workCooldown = 30;
                stop();
            } else if (isCrop(state) && !isMatureCrop(state) && hasBoneMeal()) {
                applyBoneMeal(targetCropPos);
                useBoneMealCooldown = 40;
                workCooldown = 20;
                stop();
            } else {
                stop();
            }
        } else {
            villager.getNavigation().moveTo(targetCropPos.getX() + 0.5, targetCropPos.getY(), targetCropPos.getZ() + 0.5, 0.7);
        }
    }

    private void handleSoil() {
        if (!villager.level().isLoaded(targetSoilPos)) { stop(); return; }
        double dist = villager.distanceToSqr(targetSoilPos.getX() + 0.5, targetSoilPos.getY(), targetSoilPos.getZ() + 0.5);
        if (dist < 3.0) {
            villager.getNavigation().stop();
            BlockState state = villager.level().getBlockState(targetSoilPos);
            if (state.getBlock() == Blocks.FARMLAND) {
                plantSeeds(targetSoilPos);
                workCooldown = 20;
                stop();
            } else if (state.getBlock() == Blocks.DIRT || state.getBlock() == Blocks.GRASS_BLOCK || state.getBlock() == Blocks.COARSE_DIRT) {
                tillSoil(targetSoilPos);
                workCooldown = 25;
                stop();
            } else {
                stop();
            }
        } else {
            villager.getNavigation().moveTo(targetSoilPos.getX() + 0.5, targetSoilPos.getY(), targetSoilPos.getZ() + 0.5, 0.7);
        }
    }

    private void handleWater() {
        if (!villager.level().isLoaded(targetWaterPos)) { stop(); return; }
        double dist = villager.distanceToSqr(targetWaterPos.getX() + 0.5, targetWaterPos.getY(), targetWaterPos.getZ() + 0.5);
        if (dist < 3.0) {
            villager.getNavigation().stop();
            placeWater(targetWaterPos);
            workCooldown = 40;
            stop();
        } else {
            villager.getNavigation().moveTo(targetWaterPos.getX() + 0.5, targetWaterPos.getY(), targetWaterPos.getZ() + 0.5, 0.7);
        }
    }

    private BlockPos findMatureCrop(long now) {
        BlockPos center = villager.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;
                    if (isMatureCrop(villager.level().getBlockState(pos))) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findUnripeCrop(long now) {
        BlockPos center = villager.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;
                    BlockState state = villager.level().getBlockState(pos);
                    if (isCrop(state) && !isMatureCrop(state)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findDryFarmland(long now) {
        BlockPos center = villager.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;
                    BlockState state = villager.level().getBlockState(pos);
                    if (state.getBlock() == Blocks.FARMLAND) {
                        int moisture = state.getValue(FarmBlock.MOISTURE);
                        if (moisture == 0 && !isNearWater(pos)) {
                            return pos.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findEmptyFarmland(long now) {
        BlockPos center = villager.blockPosition();
        BlockPos bestPos = null;
        int bestScore = -1;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;
                    BlockState state = villager.level().getBlockState(pos);

                    if (state.getBlock() == Blocks.FARMLAND) {
                        BlockPos above = pos.above();
                        if (!villager.level().getBlockState(above).isAir()) continue;

                        int score = 0;
                        if (isNearWater(pos)) score += 100;

                        int nearbyCrops = countNearbyCrops(pos, 3);
                        score += Math.min(nearbyCrops * 15, 75);

                        int distance = (int) Math.sqrt(center.distSqr(pos));
                        score += (SEARCH_RADIUS - distance) * 2;

                        if (score > bestScore) {
                            bestScore = score;
                            bestPos = pos;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private BlockPos findTillableDirt(long now) {
        BlockPos center = villager.blockPosition();
        BlockPos anchor = attachment.getPersonalChestPos() != null ? attachment.getPersonalChestPos() : center;
        BlockPos bestPos = null;
        int bestScore = -1;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (blockedUntil.containsKey(pos) && blockedUntil.get(pos) > now) continue;
                    BlockState state = villager.level().getBlockState(pos);

                    if (state.getBlock() == Blocks.DIRT || state.getBlock() == Blocks.GRASS_BLOCK || state.getBlock() == Blocks.COARSE_DIRT) {
                        BlockPos above = pos.above();
                        BlockState aboveState = villager.level().getBlockState(above);

                        if (!aboveState.isAir()) continue;
                        if (!isNearWater(pos)) continue;
                        if (anchor != null && anchor.distSqr(pos) > 1024) continue;

                        int score = 0;
                        int nearbyFarmland = countNearbyBlocks(pos, Blocks.FARMLAND, 3);
                        score += Math.min(nearbyFarmland * 15, 75);

                        int distance = (int) Math.sqrt(center.distSqr(pos));
                        score += (SEARCH_RADIUS - distance) * 2;

                        if (score > bestScore) {
                            bestScore = score;
                            bestPos = pos;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private int countNearbyBlocks(BlockPos pos, Block target, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (villager.level().getBlockState(check).getBlock() == target) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countNearbyCrops(BlockPos pos, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (isCrop(villager.level().getBlockState(check))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isNearWater(BlockPos pos) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (villager.level().getBlockState(check).getBlock() == Blocks.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasWaterBucket() {
        SimpleContainer inv = attachment.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == Items.WATER_BUCKET) return true;
        }
        return false;
    }

    private void placeWater(BlockPos farmlandPos) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = attachment.getInventory();

        BlockState farmlandState = level.getBlockState(farmlandPos);
        if (farmlandState.getBlock() != Blocks.FARMLAND) return;
        if (farmlandState.getValue(FarmBlock.MOISTURE) > 0) return;

        boolean canPlaceWaterHere = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = farmlandPos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isSolid() || neighborState.isAir()) {
                if (neighborState.getBlock() != Blocks.WATER) {
                    canPlaceWaterHere = true;
                    break;
                }
            }
        }

        if (!canPlaceWaterHere) return;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                BlockPos waterPos = null;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos neighbor = farmlandPos.relative(dir);
                    BlockState neighborState = level.getBlockState(neighbor);
                    if (neighborState.isAir() || !neighborState.isSolid()) {
                        waterPos = neighbor;
                        break;
                    }
                }

                if (waterPos == null) return;

                level.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                stack.shrink(1);
                inv.addItem(new ItemStack(Items.BUCKET));
                villager.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, waterPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                break;
            }
        }
    }

    private void tillSoil(BlockPos pos) {
        ServerLevel level = (ServerLevel) villager.level();
        ItemStack hoe = findHoe();
        if (hoe.isEmpty()) return;

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (!aboveState.isAir()) {
            level.destroyBlock(above, true, villager);
        }

        if (level.getBlockState(pos).getBlock() == Blocks.FARMLAND) return;

        level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);

        if (hoe.isDamageableItem()) {
            hoe.setDamageValue(hoe.getDamageValue() + 1);
            if (hoe.getDamageValue() >= hoe.getMaxDamage()) {
                hoe.shrink(1);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }

        villager.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.HOE_TILL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

        // СБРОС ШТРАФОВ: Успешная вспашка доказывает, что фермер не застрял! Полностью очищаем черный список
        failCount.clear();
        blockedUntil.clear();

        attachment.decreaseHunger(0.15);
    }

    private void plantSeeds(BlockPos soilPos) {
        ItemStack seeds = findSeeds();
        if (seeds.isEmpty()) return;
        BlockPos above = soilPos.above();
        if (villager.level().getBlockState(above).isAir()) {
            Block cropBlock = getCropFromSeeds(seeds.getItem());
            if (cropBlock != null) {
                villager.level().setBlock(above, cropBlock.defaultBlockState(), 3);
                seeds.shrink(1);
                villager.swing(InteractionHand.MAIN_HAND);
                villager.level().playSound(null, above, net.minecraft.sounds.SoundEvents.CROP_PLANTED, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

                // СБРОС ШТРАФОВ: Успешная посадка семечка полностью очищает черный список
                failCount.clear();
                blockedUntil.clear();

                attachment.decreaseHunger(0.1);
            }
        }
    }

    private void harvestCrop(BlockPos pos) {
        ServerLevel level = (ServerLevel) villager.level();
        BlockState state = level.getBlockState(pos);
        if (!isMatureCrop(state)) return;
        float multiplier = getHoeMultiplier();
        List<ItemStack> drops = new ArrayList<>();
        if (state.getBlock() instanceof CropBlock crop) {
            int defaultCount = 1 + villager.getRandom().nextInt(2);
            int finalCount = (int) Math.min(defaultCount * multiplier, 64);
            drops.add(new ItemStack(getCropProduct(state.getBlock()), finalCount));
            if (crop == Blocks.WHEAT) {
                int seedsCount = 1 + villager.getRandom().nextInt(2);
                seedsCount = (int) Math.min(seedsCount * multiplier, 8);
                drops.add(new ItemStack(Items.WHEAT_SEEDS, seedsCount));
            } else if (crop == Blocks.BEETROOTS) {
                int seedsCount = 1 + villager.getRandom().nextInt(2);
                seedsCount = (int) Math.min(seedsCount * multiplier, 8);
                drops.add(new ItemStack(Items.BEETROOT_SEEDS, seedsCount));
            }
        } else if (state.getBlock() == Blocks.SWEET_BERRY_BUSH) {
            int count = 2 + villager.getRandom().nextInt(2);
            count = (int) Math.min(count * multiplier, 8);
            drops.add(new ItemStack(Items.SWEET_BERRIES, count));
        } else if (state.getBlock() == Blocks.NETHER_WART) {
            int count = 2 + villager.getRandom().nextInt(2);
            count = (int) Math.min(count * multiplier, 8);
            drops.add(new ItemStack(Items.NETHER_WART, count));
        } else {
            level.destroyBlock(pos, true, villager);
            villager.swing(InteractionHand.MAIN_HAND);
            attachment.decreaseHunger(0.2);
            return;
        }
        level.destroyBlock(pos, false, villager);
        for (ItemStack drop : drops) {
            attachment.getInventory().addItem(drop);
        }
        villager.swing(InteractionHand.MAIN_HAND);

        // СБРОС ШТРАФОВ: Успешный сбор созревшего урожая полностью очищает черный список грядок
        failCount.clear();
        blockedUntil.clear();

        attachment.decreaseHunger(0.2);
        if (villager.getRandom().nextFloat() < 0.3f) {
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, villager.getX(), villager.getY(), villager.getZ(), 1));
        }
    }

    private float getHoeMultiplier() {
        ItemStack hoe = findHoe();
        if (hoe.isEmpty()) return 1.0f;
        if (hoe.getItem() instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            if (tier == Tiers.WOOD) return 1.0f;
            if (tier == Tiers.STONE) return 1.3f;
            if (tier == Tiers.IRON) return 1.6f;
            if (tier == Tiers.DIAMOND) return 2.0f;
            if (tier == Tiers.NETHERITE) return 2.5f;
        }
        return 1.0f;
    }

    private Item getCropProduct(Block block) {
        if (block == Blocks.WHEAT) return Items.WHEAT;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        if (block == Blocks.BEETROOTS) return Items.BEETROOT;
        if (block == Blocks.MELON_STEM) return Items.MELON_SLICE;
        if (block == Blocks.PUMPKIN_STEM) return Items.PUMPKIN;
        return Items.AIR;
    }

    private boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock ||
                state.getBlock() instanceof StemBlock ||
                state.getBlock() == Blocks.SWEET_BERRY_BUSH ||
                state.getBlock() == Blocks.NETHER_WART;
    }

    private boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        if (state.getBlock() == Blocks.SWEET_BERRY_BUSH) return state.getValue(BlockStateProperties.AGE_3) == 3;
        if (state.getBlock() == Blocks.NETHER_WART) return state.getValue(BlockStateProperties.AGE_3) == 3;
        return false;
    }

    private boolean isSeed(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS ||
                item == Items.CARROT || item == Items.POTATO ||
                item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS;
    }

    private Block getCropFromSeeds(Item seed) {
        if (seed == Items.WHEAT_SEEDS) return Blocks.WHEAT;
        if (seed == Items.BEETROOT_SEEDS) return Blocks.BEETROOTS;
        if (seed == Items.CARROT) return Blocks.CARROTS;
        if (seed == Items.POTATO) return Blocks.POTATOES;
        if (seed == Items.MELON_SEEDS) return Blocks.MELON_STEM;
        if (seed == Items.PUMPKIN_SEEDS) return Blocks.PUMPKIN_STEM;
        return null;
    }

    private boolean hasBoneMeal() {
        SimpleContainer inv = attachment.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.BONE_MEAL)) return true;
        }
        return false;
    }

    private void applyBoneMeal(BlockPos cropPos) {
        ItemStack boneMeal = findBoneMeal();
        if (!boneMeal.isEmpty()) {
            if (BoneMealItem.applyBonemeal(boneMeal, villager.level(), cropPos, null)) {
                boneMeal.shrink(1);
                villager.level().levelEvent(1505, cropPos, 0);
                villager.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    private ItemStack findBoneMeal() {
        SimpleContainer inv = attachment.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.BONE_MEAL)) return inv.getItem(i);
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findHoe() {
        SimpleContainer inv = attachment.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof HoeItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack findSeeds() {
        SimpleContainer inv = attachment.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isSeed(stack.getItem())) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void stop() {
        targetCropPos = null;
        targetSoilPos = null;
        targetWaterPos = null;
        this.lastTargetPos = null;
        if (useBoneMealCooldown > 0) useBoneMealCooldown--;
    }

    @Override
    public boolean canContinueToUse() {
        return (targetCropPos != null || targetSoilPos != null || targetWaterPos != null) && workCooldown == 0;
    }
}