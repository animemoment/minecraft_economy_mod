package com.economymod.economy;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleContainer;

public class VillagerDesireManager {
    public static int getDesiredAmount(Villager villager, Item item) {
        VillagerProfession prof = villager.getVillagerData().getProfession();
        int current = countInInventory(villager.getInventory(), item);

        // Исправлено: проверка на еду через FoodProperties
        if (item.getFoodProperties(item.getDefaultInstance(), villager) != null) {
            return Math.max(0, 20 - current);
        }

        if (prof == VillagerProfession.FARMER && item == Items.WHEAT_SEEDS) return Math.max(0, 64 - current);
        if (prof == VillagerProfession.ARMORER && item == Items.IRON_INGOT) return Math.max(0, 32 - current);

        return 0;
    }

    private static int countInInventory(SimpleContainer inv, Item item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) count += inv.getItem(i).getCount();
        }
        return count;
    }
}