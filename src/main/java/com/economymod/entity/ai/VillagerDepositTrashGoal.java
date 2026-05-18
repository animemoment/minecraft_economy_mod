package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.EnumSet;
import java.util.List;

public class VillagerDepositTrashGoal extends Goal {
    private final Villager villager;
    private BlockPos chestPos;
    private int openTicks = 0;

    public VillagerDepositTrashGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null || att.getTrashSlots().isEmpty()) return false;

        if (att.getPersonalChestPos() != null) {
            this.chestPos = att.getPersonalChestPos();
            return true;
        }

        // Ищем ближайший сундук в радиусе 12 блоков
        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-8, -2, -8), current.offset(8, 2, 8))) {
            if (villager.level().getBlockState(pos).is(Blocks.CHEST)) {
                this.chestPos = pos.immutable();
                att.setPersonalChestPos(this.chestPos);
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        villager.getLookControl().setLookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);
        if (villager.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5) < 3.0) {
            villager.getNavigation().stop();
            openTicks++;

            BlockEntity be = villager.level().getBlockEntity(chestPos);
            if (be instanceof ChestBlockEntity chest) {
                if (openTicks == 5) {
                    // Анимация открытия
                    villager.level().blockEvent(chestPos, Blocks.CHEST, 1, 1);
                    villager.level().playSound(null, chestPos, net.minecraft.sounds.SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                }

                if (openTicks >= 30) {
                    depositItems(chest);
                    // Анимация закрытия
                    villager.level().blockEvent(chestPos, Blocks.CHEST, 1, 0);
                    villager.level().playSound(null, chestPos, net.minecraft.sounds.SoundEvents.CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                    openTicks = 0;
                    this.chestPos = null;
                }
            }
        } else {
            villager.getNavigation().moveTo(chestPos.getX(), chestPos.getY(), chestPos.getZ(), 0.5D);
        }
    }

    private void depositItems(Container chest) {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        List<Integer> trashSlots = att.getTrashSlots();
        for (int slotIdx : trashSlots) {
            ItemStack trash = att.getInventory().getItem(slotIdx);
            // Пытаемся положить в сундук
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).isEmpty()) {
                    chest.setItem(i, trash.copy());
                    att.getInventory().setItem(slotIdx, ItemStack.EMPTY);
                    break;
                } else if (ItemStack.isSameItemSameComponents(chest.getItem(i), trash)) {
                    int canAdd = Math.min(trash.getCount(), chest.getItem(i).getMaxStackSize() - chest.getItem(i).getCount());
                    chest.getItem(i).grow(canAdd);
                    trash.shrink(canAdd);
                    if (trash.isEmpty()) break;
                }
            }
        }
    }
}