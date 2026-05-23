package com.economymod.entity.ai.mining;

import com.economymod.attachment.VillagerAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

public class TorchPlacer {

    public static void tryPlaceTorch(Villager villager, VillagerAttachment att) {
        if (villager.level().getGameTime() % 40 != 0) return;

        ItemStack torch = getTorchFromInventory(att);
        if (!torch.isEmpty()) {
            BlockPos currentPos = villager.blockPosition();
            int light = villager.level().getBrightness(LightLayer.BLOCK, currentPos);

            // Если слишком темно и под ногами твердый блок — ставим факел
            if (light < 4 && villager.level().getBlockState(currentPos).isAir() && villager.level().getBlockState(currentPos.below()).isSolid()) {
                villager.level().setBlockAndUpdate(currentPos, Blocks.TORCH.defaultBlockState());
                torch.shrink(1);
                villager.level().playSound(null, currentPos, net.minecraft.sounds.SoundEvents.WOOD_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    private static ItemStack getTorchFromInventory(VillagerAttachment att) {
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
}