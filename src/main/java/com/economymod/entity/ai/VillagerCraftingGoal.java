package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

public class VillagerCraftingGoal extends Goal {
    private final Villager villager;
    private BlockPos tablePos;
    private int craftingTick = 0;

    public VillagerCraftingGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().getGameTime() % 20 != 0) return false;
        if (!hasResources()) return false;

        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -2, -10), current.offset(10, 2, 10))) {
            if (villager.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                this.tablePos = pos.immutable();
                return true;
            }
        }
        return false;
    }

    private boolean hasResources() {
        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;
        var inv = att.getInventory();
        int iron = 0, sticks = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.IRON_INGOT)) iron += stack.getCount();
            if (stack.is(Items.STICK)) sticks += stack.getCount();
        }
        return iron >= 3 && sticks >= 2;
    }

    @Override
    public void start() {
        craftingTick = 0;
        if (tablePos != null) {
            villager.getNavigation().moveTo(tablePos.getX(), tablePos.getY(), tablePos.getZ(), 0.6D);
        }
    }

    @Override
    public void tick() {
        if (tablePos == null) return;

        // ИСПРАВЛЕНО: Блокируем ванильный ИИ движения и взгляда
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);

        villager.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5);

        if (villager.distanceToSqr(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5) < 3.0) {
            villager.getNavigation().stop();
            craftingTick++;

            if (craftingTick % 10 == 0 && villager.level() instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                        tablePos.getX() + 0.5, tablePos.getY() + 1.1, tablePos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.02);
            }

            if (craftingTick >= 60) {
                performCraft();
                craftingTick = 0;
            }
        } else {
            villager.getNavigation().moveTo(tablePos.getX(), tablePos.getY(), tablePos.getZ(), 0.6D);
        }
    }

    private void performCraft() {
        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return;
        consumeItem(Items.IRON_INGOT, 3);
        consumeItem(Items.STICK, 2);
        att.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        villager.level().playSound(null, tablePos, net.minecraft.sounds.SoundEvents.VILLAGER_WORK_TOOLSMITH,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        this.tablePos = null;
    }

    private void consumeItem(net.minecraft.world.item.Item item, int count) {
        var inv = villager.getData(ModAttachments.VILLAGER.get()).getInventory();
        int remaining = count;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tablePos != null && hasResources() && villager.level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE);
    }
}