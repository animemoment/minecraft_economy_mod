package com.economymod.economy;

import com.economymod.EconomyMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import java.util.*;

public class ItemPriceTable {
    private static final double SMELTING_COST = 2.00;
    private static final double CRAFTING_COST = 0.50;
    private final Map<Item, Double> prices = new HashMap<>();
    private final RegistryAccess registryAccess; // Нужно для Minecraft 1.21.1

    public ItemPriceTable(RecipeManager rm, ServerLevel level) {
        this.registryAccess = level.registryAccess();
        initBaseResources();

        // 20 итераций для полной сборки (Руда -> Слиток -> Броня)
        for (int i = 0; i < 20; i++) {
            boolean changed = false;
            changed |= processCraftingRecipes(rm);
            changed |= processCookingRecipes(rm, RecipeType.SMELTING);
            changed |= processCookingRecipes(rm, RecipeType.BLASTING);
            changed |= processCookingRecipes(rm, RecipeType.SMOKING);
            changed |= processNetheriteUpgrades(); // Бронебойный расчет Незерита
            if (!changed) break;
        }

        EconomyMod.LOGGER.info("ItemPriceTable: Успешно рассчитано {} цен.", prices.size());
    }

    private void initBaseResources() {
        // --- ДЕРЕВО И БАЗА ---
        setPrice(Items.OAK_LOG, 4.00);
        setPrice(Items.SPRUCE_LOG, 4.00);
        setPrice(Items.BIRCH_LOG, 4.00);
        setPrice(Items.JUNGLE_LOG, 4.00);
        setPrice(Items.ACACIA_LOG, 4.00);
        setPrice(Items.DARK_OAK_LOG, 4.00);
        setPrice(Items.MANGROVE_LOG, 4.00);
        setPrice(Items.CHERRY_LOG, 4.00);
        setPrice(Items.OAK_PLANKS, 1.00);
        setPrice(Items.STICK, 0.50);

        // --- ДЕШЕВЫЕ БЛОКИ ---
        setPrice(Items.DIRT, 0.01);
        setPrice(Items.GRASS_BLOCK, 0.05);
        setPrice(Items.GRAVEL, 0.02);
        setPrice(Items.SAND, 0.02);
        setPrice(Items.COBBLESTONE, 0.02);
        setPrice(Items.STONE, 0.05);
        setPrice(Items.NETHERRACK, 0.01);

        // --- РУДЫ И СЛИТКИ ---
        setPrice(Items.COAL, 2.00);
        setPrice(Items.RAW_IRON, 6.00);
        setPrice(Items.IRON_INGOT, 8.00);
        setPrice(Items.RAW_GOLD, 15.00);
        setPrice(Items.GOLD_INGOT, 20.00);
        setPrice(Items.RAW_COPPER, 1.00);
        setPrice(Items.REDSTONE, 1.50);
        setPrice(Items.LAPIS_LAZULI, 4.00);
        setPrice(Items.QUARTZ, 3.00);
        setPrice(Items.DIAMOND, 250.00);
        setPrice(Items.EMERALD, 150.00);
        setPrice(Items.NETHERITE_SCRAP, 800.00);

        // --- РЕДКИЕ ВЕЩИ ---
        setPrice(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2000.00);
        setPrice(Items.NETHER_STAR, 10000.00);
        setPrice(Items.ENDER_PEARL, 25.00);
        setPrice(Items.BLAZE_ROD, 30.00);
        setPrice(Items.STRING, 1.00);
        setPrice(Items.FEATHER, 0.50);
    }

    private boolean processCraftingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) continue;

            // ИСПРАВЛЕНИЕ: Передаем registryAccess вместо null
            ItemStack output = recipe.getResultItem(registryAccess);
            if (output.isEmpty() || prices.containsKey(output.getItem())) continue;

            double ingredientsTotal = 0;
            boolean allKnown = true;

            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                double best = getBestIngredientPrice(ingredient);
                if (best > 0) {
                    ingredientsTotal += best;
                } else {
                    allKnown = false;
                    break;
                }
            }

            if (allKnown && ingredientsTotal > 0) {
                double finalPrice = (ingredientsTotal + CRAFTING_COST) / output.getCount();
                setPrice(output.getItem(), finalPrice);
                changed = true;
            }
        }
        return changed;
    }

    private boolean processCookingRecipes(RecipeManager rm, RecipeType<? extends AbstractCookingRecipe> type) {
        boolean changed = false;
        for (RecipeHolder<? extends AbstractCookingRecipe> holder : rm.getAllRecipesFor(type)) {
            AbstractCookingRecipe recipe = holder.value();

            // ИСПРАВЛЕНИЕ: Передаем registryAccess
            ItemStack output = recipe.getResultItem(registryAccess);
            if (output.isEmpty() || prices.containsKey(output.getItem())) continue;

            double inputPrice = getBestIngredientPrice(recipe.getIngredients().get(0));
            if (inputPrice > 0) {
                setPrice(output.getItem(), (inputPrice + SMELTING_COST) / output.getCount());
                changed = true;
            }
        }
        return changed;
    }

    // ИСПРАВЛЕНИЕ: Жестко кодируем незерит, чтобы избежать багов с API кузнечного стола
    private boolean processNetheriteUpgrades() {
        boolean changed = false;
        double templateP = prices.getOrDefault(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, -1.0);
        double ingotP = prices.getOrDefault(Items.NETHERITE_INGOT, -1.0);

        if (templateP > 0 && ingotP > 0) {
            changed |= upgradeToNetherite(Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_AXE, Items.NETHERITE_AXE, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_HOE, Items.NETHERITE_HOE, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_HELMET, Items.NETHERITE_HELMET, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS, templateP, ingotP);
            changed |= upgradeToNetherite(Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS, templateP, ingotP);
        }
        return changed;
    }

    private boolean upgradeToNetherite(Item base, Item result, double templateP, double ingotP) {
        if (prices.containsKey(result)) return false;
        double baseP = prices.getOrDefault(base, -1.0);
        if (baseP > 0) {
            setPrice(result, baseP + templateP + ingotP + 150.0); // +150 за элитную ковку
            return true;
        }
        return false;
    }

    private double getBestIngredientPrice(Ingredient ingredient) {
        double best = -1;
        for (ItemStack stack : ingredient.getItems()) {
            double p = prices.getOrDefault(stack.getItem(), -1.0);
            if (p > 0 && (best == -1 || p < best)) best = p;
        }
        return best;
    }

    private void setPrice(Item item, double price) {
        prices.put(item, Math.max(0.01, Math.round(price * 100.0) / 100.0));
    }

    public double getPrice(Item item) {
        return prices.getOrDefault(item, 1.0);
    }

    public Map<Item, Double> getAllPrices() { return Collections.unmodifiableMap(prices); }
}