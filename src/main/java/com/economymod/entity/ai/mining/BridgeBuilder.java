package com.economymod.entity.ai.mining;

import com.economymod.attachment.VillagerAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class BridgeBuilder {

    public static ItemStack getPlaceableBlock(VillagerAttachment att) {
        if (att == null) return ItemStack.EMPTY;
        SimpleContainer inv = att.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            Block block = Block.byItem(stack.getItem());
            if (block != Blocks.AIR) {
                BlockState defaultState = block.defaultBlockState();

                // ИСПРАВЛЕНО: Защитный фильтр полностью исключает двери, кровати и высокие растения из строительных блоков жителей!
                boolean isDoubleBlock = block instanceof DoorBlock ||
                        block instanceof BedBlock ||
                        block instanceof DoublePlantBlock ||
                        defaultState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);

                if (defaultState.isSolid() && !isDoubleBlock && block != Blocks.CHEST && block != Blocks.CRAFTING_TABLE && block != Blocks.FURNACE) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void performPlaceBlock(Level level, BlockPos targetBlockPos, ItemStack block) {
        if (targetBlockPos != null && !block.isEmpty()) {
            Block b = Block.byItem(block.getItem());
            BlockState placeState = b.defaultBlockState();

            level.setBlockAndUpdate(targetBlockPos, placeState);
            block.shrink(1);

            level.playSound(null, targetBlockPos, net.minecraft.sounds.SoundEvents.STONE_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }
}