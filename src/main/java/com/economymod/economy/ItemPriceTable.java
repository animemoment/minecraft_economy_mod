package com.economymod.economy;

import com.economymod.EconomyMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import java.util.*;

public class ItemPriceTable {

    private static final long MINING_COST = 1L;
    private static final long SMELTING_COST = 2L;
    private static final long CRAFTING_COST = 1L;
    private static final long SMITHING_COST = 5L;
    private static final long FUEL_COST_PER_ITEM = 1L;

    private final Map<Item, Long> prices = new HashMap<>();

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
        } while (changed && iteration < 25); // Увеличено до 25

        EconomyMod.LOGGER.info("ItemPriceTable built with {} items in {} iterations", prices.size(), iteration);
    }

    public Map<Item, Long> getAllPrices() {
        return Collections.unmodifiableMap(prices);
    }

    public long getPrice(Item item) {
        return prices.getOrDefault(item, getFallback(item));
    }

    private long getFallback(Item item) {
        if (item.getDefaultMaxStackSize() <= 16) return 8L;
        return 1L;
    }

    private void initBaseResources() {
        setIfAbsent(Items.DIAMOND, 40L);
        setIfAbsent(Items.EMERALD, 50L);
        setIfAbsent(Items.LAPIS_LAZULI, 5L);
        setIfAbsent(Items.AMETHYST_SHARD, 8L);
        setIfAbsent(Items.QUARTZ, 4L);
        setIfAbsent(Items.IRON_ORE, 5L);
        setIfAbsent(Items.GOLD_ORE, 8L);
        setIfAbsent(Items.DIAMOND_ORE, 30L);
        setIfAbsent(Items.EMERALD_ORE, 40L);
        setIfAbsent(Items.COAL, 3L);
        setIfAbsent(Items.RAW_IRON, 5L);
        setIfAbsent(Items.RAW_GOLD, 8L);
        setIfAbsent(Items.RAW_COPPER, 3L);
        setIfAbsent(Items.STONE, 1L);
        setIfAbsent(Items.COBBLESTONE, 1L);
        setIfAbsent(Items.DIRT, 1L);
        setIfAbsent(Items.OAK_LOG, 2L);
        setIfAbsent(Items.OAK_PLANKS, 1L);
        setIfAbsent(Items.STICK, 1L);
        setIfAbsent(Items.REDSTONE, 3L);
        setIfAbsent(Items.LEATHER, 3L);
        setIfAbsent(Items.FEATHER, 2L);
        setIfAbsent(Items.STRING, 2L);
        setIfAbsent(Items.GUNPOWDER, 4L);
        setIfAbsent(Items.ENDER_PEARL, 10L);
        setIfAbsent(Items.BLAZE_ROD, 12L);
        setIfAbsent(Items.GHAST_TEAR, 20L);
        setIfAbsent(Items.SLIME_BALL, 5L);
        setIfAbsent(Items.BOOK, 5L);
        setIfAbsent(Items.PAPER, 1L);
        setIfAbsent(Items.BEEF, 2L);
        setIfAbsent(Items.CHICKEN, 2L);
        setIfAbsent(Items.WHEAT, 1L);
        setIfAbsent(Items.BONE_MEAL, 1L);
        setIfAbsent(Items.ELYTRA, 500L);
        setIfAbsent(Items.TOTEM_OF_UNDYING, 1000L);
        setIfAbsent(Items.NETHER_STAR, 500L);
        setIfAbsent(Items.TRIDENT, 200L);
        setIfAbsent(Items.HEART_OF_THE_SEA, 100L);
        setIfAbsent(Items.NAUTILUS_SHELL, 20L);
        setIfAbsent(Items.PHANTOM_MEMBRANE, 15L);
        setIfAbsent(Items.SHULKER_SHELL, 30L);
        setIfAbsent(Items.ECHO_SHARD, 40L);
        setIfAbsent(Items.GOAT_HORN, 20L);
        setIfAbsent(Items.SNIFFER_EGG, 30L);
        setIfAbsent(Items.DISC_FRAGMENT_5, 50L);
        setIfAbsent(Items.EXPERIENCE_BOTTLE, 25L);
        setIfAbsent(Items.SADDLE, 15L);
        setIfAbsent(Items.NAME_TAG, 20L);
        setIfAbsent(Items.LEAD, 8L);
        setIfAbsent(Items.CLOCK, 12L);
        setIfAbsent(Items.COMPASS, 12L);
        setIfAbsent(Items.RECOVERY_COMPASS, 30L);
        setIfAbsent(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 100L);
        // === Добавлены ресурсы незерита ===
        setIfAbsent(Items.NETHERITE_SCRAP, 100L);
        setIfAbsent(Items.NETHERITE_INGOT, 450L);
        setIfAbsent(Items.ANCIENT_DEBRIS, 300L);
    }

    private void setIfAbsent(Item item, long price) {
        prices.putIfAbsent(item, price);
    }

    private boolean processSmeltingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<SmeltingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMELTING)) {
            changed |= processSmelting(holder.value());
        }
        return changed;
    }

    private boolean processBlastingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<BlastingRecipe> holder : rm.getAllRecipesFor(RecipeType.BLASTING)) {
            changed |= processSmelting(holder.value());
        }
        return changed;
    }

    private boolean processSmokingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<SmokingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMOKING)) {
            changed |= processSmelting(holder.value());
        }
        return changed;
    }

    private boolean processCraftingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            changed |= processCrafting(holder.value());
        }
        return changed;
    }

    private boolean processSmithingRecipes(RecipeManager rm, ServerLevel level) {
        boolean changed = false;
        for (RecipeHolder<SmithingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMITHING)) {
            SmithingRecipe recipe = holder.value();

            // Пропускаем трим-рецепты (украшение брони)
            if (recipe instanceof SmithingTrimRecipe) continue;

            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty()) continue;

            Item resultItem = output.getItem();
            if (prices.containsKey(resultItem)) continue;

            // Используем стандартный список ингредиентов
            // Для SmithingTransform: 0 = template, 1 = base, 2 = addition
            List<Ingredient> ingredients = recipe.getIngredients();

            if (ingredients.size() < 3) continue;

            long templateValue = getIngredientValue(ingredients.get(0));
            long baseValue = getIngredientValue(ingredients.get(1));
            long additionValue = getIngredientValue(ingredients.get(2));

            // Если базовый предмет или добавка неизвестны, пропускаем
            if (baseValue <= 0 || additionValue <= 0) continue;

            // Если шаблон не оценён, даём ему цену по умолчанию (50), чтобы не блокировать рецепт
            if (templateValue <= 0) templateValue = 50L;

            long total = baseValue + additionValue + templateValue + SMITHING_COST;
            long price = Math.max(1L, total / output.getCount());
            prices.put(resultItem, price);
            EconomyMod.LOGGER.debug("Smithing: {} = {} (base={} addition={} template={})",
                    resultItem, price, baseValue, additionValue, templateValue);
            changed = true;
        }
        return changed;
    }

    private boolean processSmelting(AbstractCookingRecipe recipe) {
        ItemStack output = recipe.getResultItem(null);
        Item resultItem = output.getItem();
        if (prices.containsKey(resultItem)) return false;

        Ingredient ingredient = recipe.getIngredients().getFirst();
        long ingredientValue = getIngredientValue(ingredient);
        if (ingredientValue <= 0) return false;

        long total = ingredientValue + FUEL_COST_PER_ITEM + SMELTING_COST;
        long price = Math.max(1L, total / output.getCount());
        prices.put(resultItem, price);
        EconomyMod.LOGGER.debug("Smelting: {} = {} (ingredients={})", resultItem, price, ingredientValue);
        return true;
    }

    private boolean processCrafting(CraftingRecipe recipe) {
        if (recipe.isSpecial()) return false;
        ItemStack output = recipe.getResultItem(null);
        Item resultItem = output.getItem();
        if (prices.containsKey(resultItem)) return false;

        long ingredientsValue = 0;
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            long ingValue = getIngredientValue(ing);
            if (ingValue <= 0) return false;
            ingredientsValue += ingValue;
        }
        if (ingredientsValue == 0) return false;

        long total = ingredientsValue + CRAFTING_COST;
        long price = Math.max(1L, total / output.getCount());
        prices.put(resultItem, price);
        EconomyMod.LOGGER.debug("Crafting: {} = {} (ingredients={})", resultItem, price, ingredientsValue);
        return true;
    }

    private long getIngredientValue(Ingredient ingredient) {
        if (ingredient.isEmpty()) return 0;
        for (ItemStack stack : ingredient.getItems()) {
            long price = prices.getOrDefault(stack.getItem(), -1L);
            if (price > 0) return price;
        }
        return -1;
    }
}