package com.economymod.entity.ai;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
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
        if (att == null) return false;

        List<Integer> trashSlots = att.getTrashSlots();
        if (trashSlots.isEmpty()) return false;

        int emptySlots = 0;
        int totalTrashCount = 0;

        for (int i = 0; i < att.getInventory().getContainerSize(); i++) {
            if (att.getInventory().getItem(i).isEmpty()) {
                emptySlots++;
            }
        }

        for (int slotIdx : trashSlots) {
            totalTrashCount += att.getInventory().getItem(slotIdx).getCount();
        }

        if (emptySlots > 2 && totalTrashCount < 128) {
            return false;
        }

        if (att.getPersonalChestPos() != null) {
            this.chestPos = att.getPersonalChestPos();
            return true;
        }

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
        // Блокируем ванильный ИИ движения и взгляда
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);

        BlockPos current = villager.blockPosition();
        BlockPos startPos = chestPos.offset(0, 0, 3); // Вход в шахту

        // ИСПРАВЛЕНО: Сегментирование пути. Если мы глубоко под землей в шахте (длина шахты по Z больше 12 блоков)
        if (current.getZ() - startPos.getZ() > 12) {
            // Рассчитываем вейпоинт на лестнице в 10 блоках выше текущего положения жителя
            int targetZ = Math.max(startPos.getZ(), current.getZ() - 10);
            int targetY = startPos.getY() - ((targetZ - startPos.getZ()) / 2);
            BlockPos waypoint = new BlockPos(startPos.getX(), targetY + 1, targetZ);

            // Идем и смотрим на вейпоинт перед собой (а не на сундук сквозь толщу земли!)
            villager.getLookControl().setLookAt(waypoint.getX() + 0.5, waypoint.getY() + 1.0, waypoint.getZ() + 0.5);
            villager.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, 0.5D);

            this.openTicks = 0; // Сбрасываем тики открытия, пока мы в пути
        } else {
            // Если мы уже у выхода из шахты — бежим напрямую к сундуку на поверхности
            villager.getLookControl().setLookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);

            if (villager.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5) < 3.0) {
                villager.getNavigation().stop();
                openTicks++;

                BlockEntity be = villager.level().getBlockEntity(chestPos);
                if (be instanceof ChestBlockEntity chest) {
                    if (openTicks == 5) {
                        villager.level().blockEvent(chestPos, Blocks.CHEST, 1, 1);
                        villager.level().playSound(null, chestPos, net.minecraft.sounds.SoundEvents.CHEST_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                    }

                    if (openTicks >= 30) {
                        depositItems(chest);
                        villager.level().blockEvent(chestPos, Blocks.CHEST, 1, 0);
                        villager.level().playSound(null, chestPos, net.minecraft.sounds.SoundEvents.CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
                        openTicks = 0;
                        this.chestPos = null;
                    }
                }
            } else {
                villager.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 0.5D);
            }
        }
    }

    private void depositItems(Container chest) {
        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
        List<Integer> trashSlots = att.getTrashSlots();
        for (int slotIdx : trashSlots) {
            ItemStack trash = att.getInventory().getItem(slotIdx);
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