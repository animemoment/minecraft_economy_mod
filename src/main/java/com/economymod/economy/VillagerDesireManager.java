package com.economymod.economy; // Исправлено с com.animemoment

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleContainer;

public class VillagerDesireManager {

    public static double getDesireScore(Villager villager, Item item) {
        int count = getItemCount(villager, item);
        VillagerProfession prof = villager.getVillagerData().getProfession();

        // Базовая потребность в еде
        if (isFood(item)) {
            return count < 12 ? 2.0 : 0.5;
        }

        // Профессиональные потребности
        if (prof == VillagerProfession.ARMORER && item == Items.IRON_INGOT) return 3.0 / (count + 1);
        if (prof == VillagerProfession.FARMER && item == Items.WHEAT_SEEDS) return 1.5 / (count + 1);
        if (prof == VillagerProfession.TOOLSMITH && item == Items.COAL) return 2.0 / (count + 1);

        return 0.1; // Минимальный интерес к прочим вещам
    }

    private static int getItemCount(Villager villager, Item item) {
        SimpleContainer inv = villager.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) total += inv.getItem(i).getCount();
        }
        return total;
    }

    private static boolean isFood(Item item) {
        return item == Items.BREAD || item == Items.POTATO || item == Items.CARROT;
    }
}