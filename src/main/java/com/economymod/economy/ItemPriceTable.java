package com.economymod.economy;

import com.economymod.EconomyMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import java.util.*;
import java.lang.reflect.Field;

public class ItemPriceTable {

    // --- КОНСТАНТЫ (Теперь в double для точности до сотых) ---
    private static final double SMELTING_COST = 2.50;
    private static final double CRAFTING_COST = 0.50;    // Наценка за каждый предмет в сетке крафта
    private static final double SMITHING_COST = 50.00;
    private static final double FUEL_COST_PER_ITEM = 1.25;

    private final Map<Item, Double> prices = new HashMap<>();

    public ItemPriceTable(RecipeManager rm, ServerLevel level) {
        initBaseResources();

        boolean changed;
        int iteration = 0;
        do {
            changed = false;
            changed |= processSmeltingRecipes(rm);
            changed |= processBlastingRecipes(rm);
            changed |= processSmokingRecipes(rm);
            changed |= processCraftingRecipes(rm);
            changed |= processSmithingRecipes(rm, level);
            iteration++;
        } while (changed && iteration < 50);

        EconomyMod.LOGGER.info("ItemPriceTable built with {} items in {} iterations", prices.size(), iteration);
    }

    public Map<Item, Double> getAllPrices() {
        return Collections.unmodifiableMap(prices);
    }

    public double getPrice(Item item) {
        return prices.getOrDefault(item, getFallback(item));
    }

    private double getFallback(Item item) {
        if (item.getDefaultMaxStackSize() <= 16) return 20.0;
        return 0.10; // Базовая цена для неизвестных блоков (грязь и т.д.)
    }

    private void initBaseResources() {
        // --- 1. ПРИРОДНЫЕ БЛОКИ (Микро-цены) ---
        setIfAbsent(Items.DIRT, 0.05);
        setIfAbsent(Items.GRAVEL, 0.10);
        setIfAbsent(Items.SAND, 0.10);
        setIfAbsent(Items.RED_SAND, 0.15);
        setIfAbsent(Items.CLAY_BALL, 0.50);
        setIfAbsent(Items.COBBLESTONE, 0.15);
        setIfAbsent(Items.STONE, 0.25);
        setIfAbsent(Items.DEEPSLATE, 0.30); // Глубинный сланец
        setIfAbsent(Items.CALCITE, 0.50);
        setIfAbsent(Items.TUFF, 0.30);
        setIfAbsent(Items.OBSIDIAN, 25.00);
        setIfAbsent(Items.CRYING_OBSIDIAN, 45.00);
        setIfAbsent(Items.ICE, 1.00);
        setIfAbsent(Items.SNOWBALL, 0.05);

        // --- 2. ДЕРЕВО (Основа экономики) ---
        // Мы задаем только ЛОГИ, остальное (доски, палки) посчитает система
        double logPrice = 4.00;
        setIfAbsent(Items.OAK_LOG, logPrice);
        setIfAbsent(Items.SPRUCE_LOG, logPrice);
        setIfAbsent(Items.BIRCH_LOG, logPrice);
        setIfAbsent(Items.JUNGLE_LOG, logPrice);
        setIfAbsent(Items.ACACIA_LOG, logPrice);
        setIfAbsent(Items.DARK_OAK_LOG, logPrice);
        setIfAbsent(Items.MANGROVE_LOG, logPrice);
        setIfAbsent(Items.CHERRY_LOG, logPrice);
        setIfAbsent(Items.BAMBOO, 0.50);

        // --- 3. РУДЫ И ДРАГОЦЕННОСТИ ---
        setIfAbsent(Items.COAL, 3.00);
        setIfAbsent(Items.RAW_IRON, 8.00);
        setIfAbsent(Items.RAW_GOLD, 15.00);
        setIfAbsent(Items.RAW_COPPER, 2.00);
        setIfAbsent(Items.DIAMOND, 200.00);
        setIfAbsent(Items.EMERALD, 150.00);
        setIfAbsent(Items.LAPIS_LAZULI, 12.00);
        setIfAbsent(Items.REDSTONE, 4.00);
        setIfAbsent(Items.AMETHYST_SHARD, 20.00);
        setIfAbsent(Items.QUARTZ, 6.00);

        // --- 4. ОРГАНИКА И СЕМЕНА ---
        setIfAbsent(Items.SUGAR_CANE, 1.00);
        setIfAbsent(Items.WHEAT_SEEDS, 0.10);
        setIfAbsent(Items.PUMPKIN_SEEDS, 0.20);
        setIfAbsent(Items.MELON_SEEDS, 0.20);
        setIfAbsent(Items.BEETROOT_SEEDS, 0.15);
        setIfAbsent(Items.NETHER_WART, 5.00); // База зельеварения
        setIfAbsent(Items.SWEET_BERRIES, 0.50);
        setIfAbsent(Items.GLOW_BERRIES, 1.00);
        setIfAbsent(Items.CACTUS, 1.50);
        setIfAbsent(Items.BROWN_MUSHROOM, 2.00);
        setIfAbsent(Items.RED_MUSHROOM, 2.00);

        // --- 5. ЛУТ С МОБОВ (Критически важно для крафтов) ---
        setIfAbsent(Items.ROTTEN_FLESH, 0.50);
        setIfAbsent(Items.BONE, 2.00);
        setIfAbsent(Items.STRING, 1.50);
        setIfAbsent(Items.SPIDER_EYE, 3.50);
        setIfAbsent(Items.GUNPOWDER, 6.00);
        setIfAbsent(Items.ENDER_PEARL, 50.00);
        setIfAbsent(Items.BLAZE_ROD, 85.00);
        setIfAbsent(Items.GHAST_TEAR, 150.00);
        setIfAbsent(Items.SLIME_BALL, 15.00);
        setIfAbsent(Items.MAGMA_CREAM, 20.00);
        setIfAbsent(Items.LEATHER, 6.00);
        setIfAbsent(Items.FEATHER, 0.50);
        setIfAbsent(Items.PHANTOM_MEMBRANE, 100.00);
        setIfAbsent(Items.INK_SAC, 3.00);
        setIfAbsent(Items.GLOW_INK_SAC, 8.00);
        setIfAbsent(Items.SHULKER_SHELL, 400.00);

        // --- 6. ОКЕАН (То, что часто забывают) ---
        setIfAbsent(Items.PRISMARINE_SHARD, 10.00);
        setIfAbsent(Items.PRISMARINE_CRYSTALS, 15.00);
        setIfAbsent(Items.NAUTILUS_SHELL, 250.00);
        setIfAbsent(Items.HEART_OF_THE_SEA, 2000.00);
        setIfAbsent(Items.TURTLE_SCUTE, 120.00); // Щиток черепа
        setIfAbsent(Items.SPONGE, 150.00);
        setIfAbsent(Items.KELP, 0.30);
        setIfAbsent(Items.SEA_PICKLE, 4.00);

        // --- 7. НЕДЕР / ЭНД / ТЕХНОЛОГИИ ---
        setIfAbsent(Items.NETHERRACK, 0.10);
        setIfAbsent(Items.SOUL_SAND, 1.50);
        setIfAbsent(Items.SOUL_SOIL, 1.50);
        setIfAbsent(Items.BASALT, 0.50);
        setIfAbsent(Items.END_STONE, 0.20);
        setIfAbsent(Items.ANCIENT_DEBRIS, 800.00);
        setIfAbsent(Items.NETHERITE_SCRAP, 250.00);
        setIfAbsent(Items.NETHERITE_INGOT, 1600.00);
        setIfAbsent(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 800.00);
        setIfAbsent(Items.NETHER_STAR, 15000.00);
        setIfAbsent(Items.ELYTRA, 7000.00);
        setIfAbsent(Items.DRAGON_EGG, 100000.00); // Артефакт

        // --- 8. УНИКАЛЬНЫЕ ВЕЩИ (Рыбалка, сокровища) ---
        setIfAbsent(Items.SADDLE, 350.00);
        setIfAbsent(Items.NAME_TAG, 180.00);
        setIfAbsent(Items.EXPERIENCE_BOTTLE, 50.00);
        setIfAbsent(Items.TOTEM_OF_UNDYING, 3000.00);
        setIfAbsent(Items.ENCHANTED_GOLDEN_APPLE, 5000.00);
        setIfAbsent(Items.GOLDEN_APPLE, 250.00);
    }

    private void setIfAbsent(Item item, double price) {
        prices.putIfAbsent(item, price);
    }

    // --- ЛОГИКА РАСЧЕТА (С поддержкой дробных чисел) ---

    private boolean processCrafting(CraftingRecipe recipe) {
        if (recipe.isSpecial()) return false;
        ItemStack output = recipe.getResultItem(null);
        Item resultItem = output.getItem();
        if (prices.containsKey(resultItem)) return false;

        double ingredientsValue = 0;
        int ingredientCount = 0;

        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            double ingValue = getIngredientValue(ing);
            if (ingValue <= 0) return false;

            ingredientsValue += ingValue;
            ingredientCount++;
        }
        if (ingredientsValue == 0) return false;

        // Итоговая цена = (Сумма ресурсов + наценка за работу) / количество на выходе
        double total = ingredientsValue + (CRAFTING_COST * ingredientCount);
        double price = Math.round((total / output.getCount()) * 100.0) / 100.0; // Округление до сотых

        prices.put(resultItem, price);
        return true;
    }

    private boolean processSmelting(AbstractCookingRecipe recipe) {
        ItemStack output = recipe.getResultItem(null);
        Item resultItem = output.getItem();
        if (prices.containsKey(resultItem)) return false;

        double ingredientValue = getIngredientValue(recipe.getIngredients().getFirst());
        if (ingredientValue <= 0) return false;

        double total = ingredientValue + FUEL_COST_PER_ITEM + SMELTING_COST;
        double price = Math.round((total / output.getCount()) * 100.0) / 100.0;

        prices.put(resultItem, price);
        return true;
    }

    private boolean processSmithingRecipes(RecipeManager rm, ServerLevel level) {
        boolean changed = false;
        for (RecipeHolder<SmithingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMITHING)) {
            SmithingRecipe recipe = holder.value();
            if (recipe instanceof SmithingTrimRecipe) continue;

            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || prices.containsKey(output.getItem())) continue;

            double templateV = 0, baseV = 0, addV = 0;

            if (recipe instanceof SmithingTransformRecipe transform) {
                templateV = getIngredientValue(getPrivateIngredient(transform, "template"));
                baseV = getIngredientValue(getPrivateIngredient(transform, "base"));
                addV = getIngredientValue(getPrivateIngredient(transform, "addition"));
            }

            if (baseV <= 0 || addV <= 0) continue;
            if (templateV <= 0) templateV = 100.0;

            double total = baseV + addV + templateV + SMITHING_COST;
            double price = Math.round((total / output.getCount()) * 100.0) / 100.0;

            prices.put(output.getItem(), price);
            changed = true;
        }
        return changed;
    }

    private double getIngredientValue(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return 0;
        for (ItemStack stack : ingredient.getItems()) {
            double price = prices.getOrDefault(stack.getItem(), -1.0);
            if (price > 0) return price;
        }
        return -1.0;
    }

    // Вспомогательные методы синхронизации (не сокращены)
    private boolean processSmeltingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<SmeltingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMELTING)) changed |= processSmelting(holder.value());
        return changed;
    }
    private boolean processBlastingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<BlastingRecipe> holder : rm.getAllRecipesFor(RecipeType.BLASTING)) changed |= processSmelting(holder.value());
        return changed;
    }
    private boolean processSmokingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<SmokingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMOKING)) changed |= processSmelting(holder.value());
        return changed;
    }
    private boolean processCraftingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) changed |= processCrafting(holder.value());
        return changed;
    }

    private Ingredient getPrivateIngredient(SmithingTransformRecipe recipe, String fieldName) {
        try {
            Field field = SmithingTransformRecipe.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Ingredient) field.get(recipe);
        } catch (Exception e) { return Ingredient.EMPTY; }
    }
}