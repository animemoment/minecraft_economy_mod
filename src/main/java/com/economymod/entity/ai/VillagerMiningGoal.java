package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.EnumSet;

public class VillagerMiningGoal extends Goal {
    private final Villager villager;
    private BlockPos orePos;
    private int breakProgress = 0;

    public VillagerMiningGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (prof != VillagerProfession.TOOLSMITH && prof != VillagerProfession.ARMORER && prof != VillagerProfession.WEAPONSMITH) return false;

        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null || !att.hasPickaxe()) return false;

        if (villager.level().getGameTime() % 60 != 0) return false;

        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-6, -2, -6), current.offset(6, 2, 6))) {
            BlockState state = villager.level().getBlockState(pos);
            if (state.is(Tags.Blocks.ORES)) {
                this.orePos = pos.immutable();
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        if (orePos == null) return;
        villager.getLookControl().setLookAt(orePos.getX() + 0.5, orePos.getY() + 0.5, orePos.getZ() + 0.5);
        double dist = villager.distanceToSqr(orePos.getX() + 0.5, orePos.getY(), orePos.getZ() + 0.5);

        if (dist < 3.5) {
            villager.getNavigation().stop();
            breakProgress++;
            if (breakProgress % 5 == 0) villager.swing(InteractionHand.MAIN_HAND);
            if (villager.level() instanceof ServerLevel sl) {
                int stage = (int) ((breakProgress / 100.0) * 10);
                sl.destroyBlockProgress(villager.getId(), orePos, stage);
            }
            if (breakProgress % 10 == 0) {
                // ИСПРАВЛЕНО: Правильное название звука
                villager.level().playSound(null, orePos, net.minecraft.sounds.SoundEvents.STONE_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
            }
            if (breakProgress >= 100) {
                performBreak();
                breakProgress = 0;
            }
        } else {
            villager.getNavigation().moveTo(orePos.getX(), orePos.getY(), orePos.getZ(), 0.5D);
        }
    }

    private void performBreak() {
        if (villager.level() instanceof ServerLevel sl) {
            sl.destroyBlock(orePos, true, villager);
            sl.destroyBlockProgress(villager.getId(), orePos, -1);
            this.orePos = null;
        }
    }

    @Override
    public void stop() {
        if (orePos != null && villager.level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(villager.getId(), orePos, -1);
        }
        this.orePos = null;
        this.breakProgress = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return orePos != null && villager.level().getBlockState(orePos).is(Tags.Blocks.ORES);
    }
}