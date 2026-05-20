package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

public class VillagerSmeltingGoal extends Goal {
    private final Villager villager;
    private BlockPos furnacePos;
    private BlockPos standPos;
    private int waitTicks = 0;
    private boolean isWaitingForSmelt = false;

    public VillagerSmeltingGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().getGameTime() % 40 != 0) return false;

        if (!hasEnoughResourcesForProfit()) return false;

        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -2, -10), current.offset(10, 2, 10))) {
            BlockEntity be = villager.level().getBlockEntity(pos);
            if (be instanceof AbstractFurnaceBlockEntity furnace) {
                if (furnace.getItem(0).isEmpty()) {
                    this.furnacePos = pos.immutable();
                    this.standPos = findStandPosition(furnacePos);
                    return standPos != null;
                }
            }
        }
        return false;
    }

    private boolean hasEnoughResourcesForProfit() {
        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;
        SimpleContainer inv = att.getInventory();

        int oreCount = 0;
        boolean hasFuel = inv.hasAnyOf(java.util.Set.of(Items.COAL, Items.CHARCOAL));

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isOre(inv.getItem(i))) oreCount += inv.getItem(i).getCount();
        }

        return oreCount >= 8 && hasFuel;
    }

    private BlockPos findStandPosition(BlockPos furnacePos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = furnacePos.relative(dir);
            if (villager.level().getBlockState(p).isAir() && villager.level().getBlockState(p.above()).isAir()) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void start() {
        isWaitingForSmelt = false;
        waitTicks = 0;
        if (standPos != null) {
            villager.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 0.5D);
        }
    }

    @Override
    public void tick() {
        if (furnacePos == null || standPos == null) return;

        // ИСПРАВЛЕНО: Блокируем ванильный ИИ движения и взгляда
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);

        villager.getLookControl().setLookAt(furnacePos.getX() + 0.5, furnacePos.getY() + 1.0, furnacePos.getZ() + 0.5);
        double dist = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);

        if (dist < 2.0) {
            villager.getNavigation().stop();
            BlockEntity be = villager.level().getBlockEntity(furnacePos);
            if (be instanceof AbstractFurnaceBlockEntity furnace) {

                if (!isWaitingForSmelt) {
                    interactWithFurnace(furnace);
                    isWaitingForSmelt = true;
                }
                else {
                    waitTicks++;
                    if (waitTicks % 20 == 0 && villager.level() instanceof ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                                villager.getX(), villager.getY() + 2.1, villager.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
                    }

                    if (!furnace.getItem(2).isEmpty() && furnace.getItem(0).isEmpty()) {
                        collectResult(furnace);
                        isWaitingForSmelt = false;
                        this.furnacePos = null;
                    }
                }
            }
        } else {
            villager.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 0.5D);
        }
    }

    private void interactWithFurnace(AbstractFurnaceBlockEntity furnace) {
        var att = villager.getData(ModAttachments.VILLAGER.get());
        SimpleContainer inv = att.getInventory();

        int oresToPut = 8;
        for (int i = 0; i < inv.getContainerSize() && oresToPut > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (isOre(stack)) {
                int take = Math.min(oresToPut, stack.getCount());
                ItemStack toInsert = stack.split(take);
                ItemStack current = furnace.getItem(0);
                if (current.isEmpty()) furnace.setItem(0, toInsert);
                else current.grow(take);
                oresToPut -= take;
            }
        }

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
                ItemStack fuel = stack.split(1);
                ItemStack currentFuel = furnace.getItem(1);
                if (currentFuel.isEmpty()) furnace.setItem(1, fuel);
                else currentFuel.grow(1);
                break;
            }
        }

        furnace.setChanged();

        villager.level().playSound(null, furnacePos, net.minecraft.sounds.SoundEvents.VILLAGER_WORK_ARMORER,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private void collectResult(AbstractFurnaceBlockEntity furnace) {
        var att = villager.getData(ModAttachments.VILLAGER.get());
        ItemStack result = furnace.getItem(2);
        if (!result.isEmpty()) {
            ItemStack collected = result.copy();
            att.getInventory().addItem(collected);
            furnace.setItem(2, ItemStack.EMPTY);

            furnace.setChanged();

            com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА: {} переплавил 8 руды с макс. выгодой!", villager.getName().getString());
            villager.level().playSound(null, furnacePos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    private boolean isOre(ItemStack stack) {
        return stack.is(Items.RAW_IRON) || stack.is(Items.RAW_GOLD) || stack.is(Items.RAW_COPPER);
    }

    @Override
    public boolean canContinueToUse() {
        if (furnacePos == null) return false;
        BlockEntity be = villager.level().getBlockEntity(furnacePos);
        return be instanceof AbstractFurnaceBlockEntity && isWaitingForSmelt;
    }
}