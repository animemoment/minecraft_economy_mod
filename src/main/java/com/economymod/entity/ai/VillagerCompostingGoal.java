package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class VillagerCompostingGoal extends Goal {
    private final Villager villager;
    private BlockPos composterPos;

    public VillagerCompostingGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.getVillagerData().getProfession() != VillagerProfession.FARMER) return false;
        if (villager.level().getGameTime() % 100 != 0) return false;

        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;
        SimpleContainer inv = att.getInventory();

        int seedCount = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WHEAT_SEEDS)) seedCount += inv.getItem(i).getCount();
        }
        if (seedCount < 5) return false;

        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-8, -2, -8), current.offset(8, 2, 8))) {
            if (villager.level().getBlockState(pos).is(Blocks.COMPOSTER)) {
                this.composterPos = pos.immutable();
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        if (composterPos == null) return;
        villager.getLookControl().setLookAt(composterPos.getX() + 0.5, composterPos.getY() + 1.0, composterPos.getZ() + 0.5);

        if (villager.distanceToSqr(composterPos.getX() + 0.5, composterPos.getY(), composterPos.getZ() + 0.5) < 3.0) {
            var att = villager.getData(ModAttachments.VILLAGER.get());
            if (att == null) return;
            SimpleContainer inv = att.getInventory();

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(Items.WHEAT_SEEDS)) {
                    BlockState state = villager.level().getBlockState(composterPos);

                    // ИСПРАВЛЕНО: Правильный порядок (Entity, State, Level, ItemStack, BlockPos)
                    ComposterBlock.insertItem(villager, state, villager.level(), new ItemStack(Items.WHEAT_SEEDS), composterPos);

                    s.shrink(1);
                    villager.level().playSound(null, composterPos, net.minecraft.sounds.SoundEvents.COMPOSTER_FILL, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    break;
                }
            }
            this.composterPos = null;
        } else {
            villager.getNavigation().moveTo(composterPos.getX(), composterPos.getY(), composterPos.getZ(), 0.5D);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return composterPos != null && villager.level().getBlockState(composterPos).is(Blocks.COMPOSTER);
    }
}