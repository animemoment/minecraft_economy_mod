package com.economymod.economy.desire;

import com.economymod.attachment.VillagerAttachment;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Set; // ДОБАВЛЕНО
import java.util.function.Predicate;

public class DesireProcessor {

    private final VillagerAttachment attachment;

    public DesireProcessor(VillagerAttachment attachment) {
        this.attachment = attachment;
    }

    public List<Desire> calculateDesires() {
        List<Desire> desires = new ArrayList<>();
        VillagerProfession profession = attachment.getProfession();

        // 1. Базовые потребности (самый высокий приоритет)
        addFoodDesires(desires);

        // 2. Профессиональные потребности
        if (profession == VillagerProfession.FARMER) {
            addFarmerDesires(desires);
        } else if (profession == VillagerProfession.TOOLSMITH || profession == VillagerProfession.WEAPONSMITH) {
            addSmithDesires(desires);
        }

        // 3. Долгосрочные цели (низкий приоритет)
        addUpgradeDesires(desires);

        // Сортируем по приоритету (от большего к меньшему)
        desires.sort((d1, d2) -> Integer.compare(d2.priority, d1.priority));
        return desires;
    }

    private void addFoodDesires(List<Desire> desires) {
        int currentBread = countItems(Items.BREAD);
        if (currentBread < 5) {
            desires.add(new Desire(new ItemStack(Items.BREAD, 5 - currentBread), Desire.DesireType.CONSUMABLE, 100));
        }
    }

    private void addFarmerDesires(List<Desire> desires) {
        // Хочет семян для посадки
        int currentSeeds = countItems(Items.WHEAT_SEEDS);
        if (currentSeeds < 16) {
            desires.add(new Desire(new ItemStack(Items.WHEAT_SEEDS, 16 - currentSeeds), Desire.DesireType.MATERIAL, 50));
        }
        // Хочет мотыгу, если её нет вообще
        if (!hasAnyItem(item -> item instanceof net.minecraft.world.item.HoeItem)) {
            desires.add(new Desire(new ItemStack(Items.STONE_HOE), Desire.DesireType.TOOL, 80));
        }
    }

    private void addSmithDesires(List<Desire> desires) {
        // Хочет угля для плавки
        int currentCoal = countItems(Items.COAL);
        if (currentCoal < 10) {
            desires.add(new Desire(new ItemStack(Items.COAL, 10 - currentCoal), Desire.DesireType.MATERIAL, 60));
        }
        // Хочет железа для крафта
        int currentIron = countItems(Items.IRON_INGOT);
        if (currentIron < 5) {
            desires.add(new Desire(new ItemStack(Items.IRON_INGOT, 5 - currentIron), Desire.DesireType.MATERIAL, 70));
        }
    }

    private void addUpgradeDesires(List<Desire> desires) {
        // Если есть деньги и деревянная мотыга, захотеть каменную
        if (attachment.getBalance() > 200 && hasItem(Items.WOODEN_HOE) && !hasItem(Items.STONE_HOE)) {
            desires.add(new Desire(new ItemStack(Items.STONE_HOE), Desire.DesireType.UPGRADE, 10));
        }
    }

    // --- Утилиты для работы с инвентарем ---
    private int countItems(Item item) {
        int count = 0;
        for (int i = 0; i < attachment.getInventory().getContainerSize(); i++) {
            if (attachment.getInventory().getItem(i).is(item)) {
                count += attachment.getInventory().getItem(i).getCount();
            }
        }
        return count;
    }

    private boolean hasItem(Item item) {
        // Используем ванильный метод SimpleContainer
        return attachment.getInventory().hasAnyOf(Set.of(item));
    }

    private boolean hasAnyItem(Predicate<Item> predicate) {
        for (int i = 0; i < attachment.getInventory().getContainerSize(); i++) {
            if (predicate.test(attachment.getInventory().getItem(i).getItem())) {
                return true;
            }
        }
        return false;
    }
}