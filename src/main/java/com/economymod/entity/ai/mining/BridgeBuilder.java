package com.economymod.entity.ai.mining;

import com.economymod.attachment.VillagerAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BridgeBuilder {

    public static ItemStack getPlaceableBlock(VillagerAttachment att) {
        if (att == null) return ItemStack.EMPTY;
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
            if (block != Blocks.AIR) {
                BlockState defaultState = block.defaultBlockState();
                // Фильтруем технические/рабочие блоки, чтобы не строить мосты из верстаков или сундуков
                if (defaultState.isSolid() && block != Blocks.CHEST && block != Blocks.CRAFTING_TABLE && block != Blocks.FURNACE) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void performPlaceBlock(Level level, BlockPos targetBlockPos, ItemStack block) {
        if (targetBlockPos != null && !block.isEmpty()) {
            net.minecraft.world.level.block.Block b = net.minecraft.world.level.block.Block.byItem(block.getItem());
            BlockState placeState = b.defaultBlockState();

            level.setBlockAndUpdate(targetBlockPos, placeState);
            block.shrink(1);

            level.playSound(null, targetBlockPos, net.minecraft.sounds.SoundEvents.STONE_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }
}