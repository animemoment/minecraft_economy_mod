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
    private static final double SMELTING_COST = 2.50; // Топливо + амортизация печи
    private static final double CRAFTING_COST = 0.75; // Плата за работу мастера
    private final Map<Item, Double> prices = new HashMap<>();
    private final RegistryAccess registryAccess;

    48x
    public ItemPriceTable(RecipeManager rm, ServerLevel level) {
        this.registryAccess = level.registryAccess();

        // 1. Установка цен на ресурсы, которые нельзя скрафтить
        initBaseResources();

        // 2. Рекурсивный обсчет (25 итераций для самых глубоких цепочек крафта)
        for (int i = 0; i < 25; i++) {
            boolean changed = false;
            changed |= processCraftingRecipes(rm);
            changed |= processCookingRecipes(rm, RecipeType.SMELTING);
            changed |= processCookingRecipes(rm, RecipeType.BLASTING);
            changed |= processCookingRecipes(rm, RecipeType.SMOKING);
            changed |= processNetheriteUpgrades();
            if (!changed) break;
        }

        EconomyMod.LOGGER.info("ЭКОНОМИКА: Таблица цен сформирована. Рассчитано предметов: {}", prices.size());
    }

    private void initBaseResources() {
        // --- СЕКЦИЯ 1: ЗЕМЛЯ И ГОРНЫЕ ПОРОДЫ ---
        setPrice(Items.DIRT, 0.01);
        setPrice(Items.GRASS_BLOCK, 0.05);
        setPrice(Items.COARSE_DIRT, 0.04);
        setPrice(Items.ROOTED_DIRT, 0.06);
        setPrice(Items.PODZOL, 0.10);
        setPrice(Items.MYCELIUM, 0.25);
        setPrice(Items.MUD, 0.03);
        setPrice(Items.MUDDY_MANGROVE_ROOTS, 0.15);
        setPrice(Items.MOSS_BLOCK, 1.00);
        setPrice(Items.COBBLESTONE, 0.02);
        setPrice(Items.STONE, 0.06);
        setPrice(Items.SMOOTH_STONE, 0.12);
        setPrice(Items.DEEPSLATE, 0.04);
        setPrice(Items.COBBLED_DEEPSLATE, 0.03);
        setPrice(Items.TUFF, 0.04);
        setPrice(Items.CALCITE, 0.20);
        setPrice(Items.DRIPSTONE_BLOCK, 0.10);
        setPrice(Items.POINTED_DRIPSTONE, 0.15);
        setPrice(Items.ANDESITE, 0.05);
        setPrice(Items.DIORITE, 0.05);
        setPrice(Items.GRANITE, 0.05);
        setPrice(Items.GRAVEL, 0.03);
        setPrice(Items.SAND, 0.02);
        setPrice(Items.RED_SAND, 0.04);
        setPrice(Items.SANDSTONE, 0.10);
        setPrice(Items.RED_SANDSTONE, 0.12);
        setPrice(Items.CLAY_BALL, 0.15);
        setPrice(Items.CLAY, 0.60);
        setPrice(Items.OBSIDIAN, 15.00);
        setPrice(Items.CRYING_OBSIDIAN, 45.00);
        setPrice(Items.BEDROCK, 999999.00);
        setPrice(Items.ICE, 0.08);
        setPrice(Items.PACKED_ICE, 0.30);
        setPrice(Items.BLUE_ICE, 2.00);
        setPrice(Items.SNOW_BLOCK, 0.40);
        setPrice(Items.SNOW, 0.05);
        setPrice(Items.SUSPICIOUS_SAND, 0.35);
        setPrice(Items.SUSPICIOUS_GRAVEL, 0.45);

        // --- СЕКЦИЯ 2: РАСТИТЕЛЬНОСТЬ И ДЕРЕВО (только то, что нельзя скрафтить) ---
        // Брёвна (не крафтятся)
        setPrice(Items.OAK_LOG, 4.00);
        setPrice(Items.SPRUCE_LOG, 4.00);
        setPrice(Items.BIRCH_LOG, 4.00);
        setPrice(Items.JUNGLE_LOG, 5.00);
        setPrice(Items.ACACIA_LOG, 4.50);
        setPrice(Items.DARK_OAK_LOG, 4.50);
        setPrice(Items.MANGROVE_LOG, 5.00);
        setPrice(Items.CHERRY_LOG, 5.50);
        setPrice(Items.BAMBOO_BLOCK, 3.00);
        setPrice(Items.CRIMSON_STEM, 8.00);
        setPrice(Items.WARPED_STEM, 8.00);
        // Листва
        setPrice(Items.OAK_LEAVES, 0.05);
        setPrice(Items.SPRUCE_LEAVES, 0.05);
        setPrice(Items.BIRCH_LEAVES, 0.05);
        setPrice(Items.JUNGLE_LEAVES, 0.06);
        setPrice(Items.ACACIA_LEAVES, 0.05);
        setPrice(Items.DARK_OAK_LEAVES, 0.05);
        setPrice(Items.MANGROVE_LEAVES, 0.06);
        setPrice(Items.CHERRY_LEAVES, 0.07);
        setPrice(Items.AZALEA_LEAVES, 0.08);
        setPrice(Items.FLOWERING_AZALEA_LEAVES, 0.12);
        // Саженцы
        setPrice(Items.OAK_SAPLING, 2.00);
        setPrice(Items.SPRUCE_SAPLING, 2.00);
        setPrice(Items.BIRCH_SAPLING, 2.00);
        setPrice(Items.JUNGLE_SAPLING, 3.00);
        setPrice(Items.ACACIA_SAPLING, 2.50);
        setPrice(Items.DARK_OAK_SAPLING, 2.50);
        setPrice(Items.MANGROVE_PROPAGULE, 3.00);
        setPrice(Items.CHERRY_SAPLING, 4.00);
        setPrice(Items.AZALEA, 2.50);
        setPrice(Items.FLOWERING_AZALEA, 3.00);
        // Растения и цветы
        setPrice(Items.BAMBOO, 0.10);
        setPrice(Items.SUGAR_CANE, 0.80);
        setPrice(Items.CACTUS, 1.20);
        setPrice(Items.VINE, 0.50);
        setPrice(Items.LILY_PAD, 5.00);
        setPrice(Items.SEAGRASS, 0.30);
        setPrice(Items.KELP, 0.25);
        setPrice(Items.DRIED_KELP, 0.35);
        setPrice(Items.SEA_PICKLE, 5.00);
        setPrice(Items.DANDELION, 1.50);
        setPrice(Items.POPPY, 1.50);
        setPrice(Items.BLUE_ORCHID, 2.50);
        setPrice(Items.ALLIUM, 2.00);
        setPrice(Items.AZURE_BLUET, 2.00);
        setPrice(Items.RED_TULIP, 2.00);
        setPrice(Items.ORANGE_TULIP, 2.00);
        setPrice(Items.WHITE_TULIP, 2.00);
        setPrice(Items.PINK_TULIP, 2.00);
        setPrice(Items.OXEYE_DAISY, 2.00);
        setPrice(Items.CORNFLOWER, 2.00);
        setPrice(Items.LILY_OF_THE_VALLEY, 3.00);
        setPrice(Items.WITHER_ROSE, 25.00);
        setPrice(Items.SUNFLOWER, 2.50);
        setPrice(Items.LILAC, 2.50);
        setPrice(Items.ROSE_BUSH, 2.50);
        setPrice(Items.PEONY, 2.50);
        setPrice(Items.PITCHER_PLANT, 5.00);
        setPrice(Items.TORCHFLOWER, 4.00);
        setPrice(Items.PINK_PETALS, 1.00);
        setPrice(Items.SPORE_BLOSSOM, 4.50);
        setPrice(Items.HANGING_ROOTS, 0.60);
        setPrice(Items.BIG_DRIPLEAF, 1.80);
        setPrice(Items.SMALL_DRIPLEAF, 1.20);
        setPrice(Items.GLOW_LICHEN, 1.00);
        setPrice(Items.MOSS_CARPET, 0.80);
        setPrice(Items.SWEET_BERRIES, 0.30);
        setPrice(Items.GLOW_BERRIES, 0.50);
        setPrice(Items.COCOA_BEANS, 1.00);
        setPrice(Items.PUMPKIN, 1.50);
        setPrice(Items.MELON, 2.00);
        setPrice(Items.BROWN_MUSHROOM, 3.00);
        setPrice(Items.RED_MUSHROOM, 3.00);
        setPrice(Items.WARPED_FUNGUS, 5.00);
        setPrice(Items.CRIMSON_FUNGUS, 5.00);
        setPrice(Items.WARPED_ROOTS, 1.50);
        setPrice(Items.CRIMSON_ROOTS, 1.50);
        setPrice(Items.WEEPING_VINES, 0.80);
        setPrice(Items.TWISTING_VINES, 0.80);
        setPrice(Items.NETHER_SPROUTS, 1.00);
        setPrice(Items.NETHER_WART, 4.00);
        setPrice(Items.CHORUS_FLOWER, 6.00);
        setPrice(Items.CHORUS_PLANT, 2.00);

        // --- СЕКЦИЯ 3: РУДЫ И МИНЕРАЛЫ (БАЗА) ---
        setPrice(Items.COAL_ORE, 3.00);
        setPrice(Items.DEEPSLATE_COAL_ORE, 3.50);
        setPrice(Items.COPPER_ORE, 2.50);
        setPrice(Items.DEEPSLATE_COPPER_ORE, 3.00);
        setPrice(Items.IRON_ORE, 8.00);
        setPrice(Items.DEEPSLATE_IRON_ORE, 8.50);
        setPrice(Items.GOLD_ORE, 20.00);
        setPrice(Items.DEEPSLATE_GOLD_ORE, 21.00);
        setPrice(Items.REDSTONE_ORE, 2.50);
        setPrice(Items.DEEPSLATE_REDSTONE_ORE, 3.00);
        setPrice(Items.LAPIS_ORE, 5.00);
        setPrice(Items.DEEPSLATE_LAPIS_ORE, 5.50);
        setPrice(Items.DIAMOND_ORE, 400.00);
        setPrice(Items.DEEPSLATE_DIAMOND_ORE, 420.00);
        setPrice(Items.EMERALD_ORE, 250.00);
        setPrice(Items.DEEPSLATE_EMERALD_ORE, 260.00);
        setPrice(Items.NETHER_QUARTZ_ORE, 4.00);
        setPrice(Items.NETHER_GOLD_ORE, 12.00);
        setPrice(Items.ANCIENT_DEBRIS, 1100.00);
        // Сырьё (не крафтится)
        setPrice(Items.COAL, 2.00);
        setPrice(Items.CHARCOAL, 1.80);
        setPrice(Items.RAW_IRON, 7.00);
        setPrice(Items.RAW_GOLD, 18.00);
        setPrice(Items.RAW_COPPER, 1.50);
        setPrice(Items.REDSTONE, 1.80);
        setPrice(Items.LAPIS_LAZULI, 4.50);
        setPrice(Items.QUARTZ, 3.50);
        setPrice(Items.AMETHYST_SHARD, 12.00);
        setPrice(Items.DIAMOND, 350.00);
        setPrice(Items.EMERALD, 200.00);
        setPrice(Items.FLINT, 1.50);
        setPrice(Items.NETHERITE_SCRAP, 1000.00);
        setPrice(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3500.00);

        // --- СЕКЦИЯ 4: МОБОДРОП ---
        setPrice(Items.ROTTEN_FLESH, 0.40);
        setPrice(Items.BONE, 1.20);
        setPrice(Items.STRING, 1.00);
        setPrice(Items.SPIDER_EYE, 2.50);
        setPrice(Items.GUNPOWDER, 6.00);
        setPrice(Items.ENDER_PEARL, 35.00);
        setPrice(Items.BLAZE_ROD, 45.00);
        setPrice(Items.GHAST_TEAR, 180.00);
        setPrice(Items.MAGMA_CREAM, 25.00);
        setPrice(Items.SLIME_BALL, 20.00);
        setPrice(Items.FEATHER, 0.60);
        setPrice(Items.LEATHER, 3.00);
        setPrice(Items.RABBIT_HIDE, 1.80);
        setPrice(Items.RABBIT_FOOT, 50.00);
        setPrice(Items.PHANTOM_MEMBRANE, 120.00);
        setPrice(Items.SHULKER_SHELL, 500.00);
        setPrice(Items.INK_SAC, 2.00);
        setPrice(Items.GLOW_INK_SAC, 5.00);
        setPrice(Items.PRISMARINE_SHARD, 3.00);
        setPrice(Items.PRISMARINE_CRYSTALS, 5.00);
        setPrice(Items.NAUTILUS_SHELL, 150.00);
        setPrice(Items.TURTLE_SCUTE, 30.00);
        setPrice(Items.ARMADILLO_SCUTE, 25.00);
        setPrice(Items.GOAT_HORN, 80.00);
        setPrice(Items.BREEZE_ROD, 55.00);
        setPrice(Items.SPIDER_EYE, 2.50);
        setPrice(Items.ARROW, 1.00);
        setPrice(Items.BOW, 8.00);
        setPrice(Items.CROSSBOW, 15.00);
        setPrice(Items.GOLDEN_SWORD, 12.00);
        setPrice(Items.GOLDEN_AXE, 16.00);
        setPrice(Items.GOLDEN_HELMET, 25.00);
        setPrice(Items.GOLDEN_CHESTPLATE, 40.00);
        setPrice(Items.GOLDEN_LEGGINGS, 35.00);
        setPrice(Items.GOLDEN_BOOTS, 20.00);

        // --- СЕКЦИЯ 5: СОКРОВИЩА (НЕКРАФТОВЫЕ) ---
        setPrice(Items.TOTEM_OF_UNDYING, 7500.00);
        setPrice(Items.ELYTRA, 15000.00);
        setPrice(Items.NETHER_STAR, 30000.00);
        setPrice(Items.DRAGON_EGG, 250000.00);
        setPrice(Items.HEART_OF_THE_SEA, 8000.00);
        setPrice(Items.SADDLE, 400.00);
        setPrice(Items.NAME_TAG, 250.00);
        setPrice(Items.LEAD, 8.00);
        setPrice(Items.ENCHANTED_GOLDEN_APPLE, 12000.00);
        setPrice(Items.TRIDENT, 6000.00);
        setPrice(Items.ECHO_SHARD, 1500.00);
        setPrice(Items.SPONGE, 300.00);
        setPrice(Items.BELL, 500.00);
        setPrice(Items.SCULK_CATALYST, 200.00);
        setPrice(Items.SCULK_SHRIEKER, 350.00);
        setPrice(Items.SCULK_SENSOR, 180.00);
        setPrice(Items.SCULK_VEIN, 25.00);
        setPrice(Items.SCULK, 100.00);
        setPrice(Items.REINFORCED_DEEPSLATE, 80.00);
        setPrice(Items.BUDDING_AMETHYST, 150.00);
        setPrice(Items.TRIAL_SPAWNER, 500.00);
        setPrice(Items.TRIAL_SPAWNER, 500.00);
        setPrice(Items.VAULT, 800.00);
        setPrice(Items.HEAVY_CORE, 2500.00);
        setPrice(Items.MUSIC_DISC_13, 200.00);
        setPrice(Items.MUSIC_DISC_CAT, 200.00);
        setPrice(Items.MUSIC_DISC_BLOCKS, 200.00);
        setPrice(Items.MUSIC_DISC_CHIRP, 200.00);
        setPrice(Items.MUSIC_DISC_FAR, 200.00);
        setPrice(Items.MUSIC_DISC_MALL, 200.00);
        setPrice(Items.MUSIC_DISC_MELLOHI, 200.00);
        setPrice(Items.MUSIC_DISC_STAL, 200.00);
        setPrice(Items.MUSIC_DISC_STRAD, 200.00);
        setPrice(Items.MUSIC_DISC_WARD, 200.00);
        setPrice(Items.MUSIC_DISC_11, 350.00);
        setPrice(Items.MUSIC_DISC_WAIT, 250.00);
        setPrice(Items.MUSIC_DISC_OTHERSIDE, 7000.00);
        setPrice(Items.MUSIC_DISC_PIGSTEP, 10000.00);
        setPrice(Items.MUSIC_DISC_5, 5000.00);
        setPrice(Items.MUSIC_DISC_RELIC, 4500.00);
        setPrice(Items.MUSIC_DISC_CREATOR, 5500.00);
        setPrice(Items.MUSIC_DISC_CREATOR_MUSIC_BOX, 5500.00);
        setPrice(Items.MUSIC_DISC_PRECIPICE, 3000.00);
        // Головы
        setPrice(Items.SKELETON_SKULL, 1800.00);
        setPrice(Items.WITHER_SKELETON_SKULL, 4500.00);
        setPrice(Items.ZOMBIE_HEAD, 1500.00);
        setPrice(Items.CREEPER_HEAD, 2000.00);
        setPrice(Items.DRAGON_HEAD, 8000.00);
        setPrice(Items.PIGLIN_HEAD, 2500.00);
        // Шаблоны кузнеца
        setPrice(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 500.00);
        setPrice(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 500.00);
        setPrice(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 500.00);
        setPrice(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 500.00);
        setPrice(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 500.00);
        setPrice(Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 700.00);
        setPrice(Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 700.00);
        setPrice(Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 700.00);
        setPrice(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 700.00);
        setPrice(Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 600.00);
        setPrice(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 500.00);
        setPrice(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3500.00);

        // --- СЕКЦИЯ 6: ЕДА (СЫРАЯ) ---
        setPrice(Items.APPLE, 1.50);
        setPrice(Items.GOLDEN_APPLE, 250.00);
        setPrice(Items.WHEAT, 0.60);
        setPrice(Items.WHEAT_SEEDS, 0.30);
        setPrice(Items.POTATO, 0.50);
        setPrice(Items.POISONOUS_POTATO, 0.20);
        setPrice(Items.CARROT, 0.50);
        setPrice(Items.BEETROOT, 0.50);
        setPrice(Items.BEETROOT_SEEDS, 0.25);
        setPrice(Items.MELON_SLICE, 0.20);
        setPrice(Items.MELON_SEEDS, 0.30);
        setPrice(Items.PUMPKIN_SEEDS, 0.30);
        setPrice(Items.TORCHFLOWER_SEEDS, 0.50);
        setPrice(Items.PITCHER_POD, 0.60);
        setPrice(Items.CHICKEN, 3.00);
        setPrice(Items.BEEF, 4.50);
        setPrice(Items.PORKCHOP, 4.00);
        setPrice(Items.MUTTON, 3.50);
        setPrice(Items.RABBIT, 3.00);
        setPrice(Items.COD, 2.50);
        setPrice(Items.SALMON, 3.00);
        setPrice(Items.TROPICAL_FISH, 4.00);
        setPrice(Items.PUFFERFISH, 8.00);
        setPrice(Items.HONEYCOMB, 5.00);
        setPrice(Items.HONEY_BOTTLE, 6.00);
        setPrice(Items.CHORUS_FRUIT, 4.00);
        setPrice(Items.EGG, 1.50);
        setPrice(Items.TURTLE_EGG, 25.00);
        setPrice(Items.SNIFFER_EGG, 30.00);
        setPrice(Items.BROWN_MUSHROOM, 3.00);
        setPrice(Items.RED_MUSHROOM, 3.00);
        setPrice(Items.GLOW_BERRIES, 0.50);
        setPrice(Items.SWEET_BERRIES, 0.30);
        setPrice(Items.KELP, 0.25);
        setPrice(Items.DRIED_KELP, 0.35);

        // --- СЕКЦИЯ 7: НИЖНИЙ МИР И ЭНД ---
        setPrice(Items.NETHERRACK, 0.02);
        setPrice(Items.SOUL_SAND, 0.50);
        setPrice(Items.SOUL_SOIL, 0.60);
        setPrice(Items.GLOWSTONE_DUST, 4.00);
        setPrice(Items.GLOWSTONE, 16.00);
        setPrice(Items.END_STONE, 0.05);
        setPrice(Items.MAGMA_BLOCK, 5.00);
        setPrice(Items.BASALT, 0.10);
        setPrice(Items.BLACKSTONE, 0.08);
        setPrice(Items.GILDED_BLACKSTONE, 15.00);
        setPrice(Items.SHROOMLIGHT, 8.00);
        setPrice(Items.NETHER_WART_BLOCK, 36.00);
        setPrice(Items.WARPED_WART_BLOCK, 38.00);
        setPrice(Items.CRIMSON_NYLIUM, 0.15);
        setPrice(Items.WARPED_NYLIUM, 0.20);
        setPrice(Items.BONE_BLOCK, 12.00);
        setPrice(Items.PURPUR_BLOCK, 10.00);
        setPrice(Items.END_STONE_BRICKS, 5.00);
        setPrice(Items.DRAGON_HEAD, 8000.00);
        setPrice(Items.END_ROD, 20.00);
        setPrice(Items.POPPED_CHORUS_FRUIT, 5.00);
        setPrice(Items.ENDER_EYE, 60.00);
        setPrice(Items.CHORUS_FLOWER, 6.00);
        setPrice(Items.CHORUS_FRUIT, 4.00);

        // --- СЕКЦИЯ 8: СОКРОВИЩА И РЕДКИЕ ПРЕДМЕТЫ ---
        setPrice(Items.ENCHANTED_BOOK, 250.00);
        setPrice(Items.EXPERIENCE_BOTTLE, 25.00);
        setPrice(Items.OMINOUS_BOTTLE, 100.00);
        setPrice(Items.WIND_CHARGE, 40.00);
        setPrice(Items.FIRE_CHARGE, 35.00);
        setPrice(Items.BRUSH, 8.00);
        setPrice(Items.RECOVERY_COMPASS, 1500.00);
        setPrice(Items.COMPASS, 12.00);
        setPrice(Items.CLOCK, 12.00);
        setPrice(Items.SPYGLASS, 15.00);
        setPrice(Items.MAP, 5.00);
        setPrice(Items.FILLED_MAP, 10.00);
        setPrice(Items.WRITABLE_BOOK, 7.00);
        setPrice(Items.WRITTEN_BOOK, 10.00);
        setPrice(Items.ARMOR_STAND, 9.00);
        setPrice(Items.ITEM_FRAME, 12.00);
        setPrice(Items.GLOW_ITEM_FRAME, 16.00);
        setPrice(Items.PAINTING, 18.00);
        setPrice(Items.SCAFFOLDING, 2.00);
        setPrice(Items.TORCH, 0.50);
        setPrice(Items.SOUL_TORCH, 0.80);
        setPrice(Items.LANTERN, 3.00);
        setPrice(Items.SOUL_LANTERN, 4.00);
        setPrice(Items.CHAIN, 3.00);
        setPrice(Items.IRON_BARS, 5.00);
        setPrice(Items.LADDER, 0.80);
        setPrice(Items.FLOWER_POT, 2.00);
        setPrice(Items.DECORATED_POT, 5.00);
        setPrice(Items.CANDLE, 1.50);
        setPrice(Items.COBWEB, 4.00);
        setPrice(Items.LILY_PAD, 5.00);
        setPrice(Items.SEA_PICKLE, 5.00);
        setPrice(Items.TURTLE_EGG, 25.00);
        setPrice(Items.HONEYCOMB, 5.00);
        setPrice(Items.HONEY_BLOCK, 25.00);
        setPrice(Items.SLIME_BLOCK, 90.00);
        setPrice(Items.HONEYCOMB_BLOCK, 20.00);
        setPrice(Items.MAGMA_BLOCK, 5.00);
        setPrice(Items.SPONGE, 300.00);
        setPrice(Items.WET_SPONGE, 150.00);
        setPrice(Items.POINTED_DRIPSTONE, 0.15);
        setPrice(Items.DRIPSTONE_BLOCK, 0.10);
        setPrice(Items.MOSS_BLOCK, 1.00);
        setPrice(Items.MOSS_CARPET, 0.80);
        setPrice(Items.AZALEA, 2.50);
        setPrice(Items.FLOWERING_AZALEA, 3.00);
        setPrice(Items.BIG_DRIPLEAF, 1.80);
        setPrice(Items.SMALL_DRIPLEAF, 1.20);
        setPrice(Items.GLOW_LICHEN, 1.00);
        setPrice(Items.HANGING_ROOTS, 0.60);
        setPrice(Items.ROOTED_DIRT, 0.06);
        setPrice(Items.SNIFFER_EGG, 30.00);
        setPrice(Items.GOAT_HORN, 80.00);
        setPrice(Items.TADPOLE_SPAWN_EGG, 10.00);
        setPrice(Items.ALLAY_SPAWN_EGG, 25.00);
        setPrice(Items.BREEZE_ROD, 55.00);
        setPrice(Items.HEAVY_CORE, 2500.00);
        setPrice(Items.TRIAL_KEY, 800.00);
        setPrice(Items.OMINOUS_TRIAL_KEY, 1500.00);
        setPrice(Items.BREEZE_ROD, 55.00);
    }

    private boolean processCraftingRecipes(RecipeManager rm) {
        boolean changed = false;
        for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) continue;

            ItemStack output = recipe.getResultItem(registryAccess);
            if (output.isEmpty() || prices.containsKey(output.getItem())) continue;

            double ingredientsTotal = 0;
            boolean allKnown = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                double best = getBestIngredientPrice(ingredient);
                if (best > 0) ingredientsTotal += best;
                else { allKnown = false; break; }
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

    private boolean processNetheriteUpgrades() {
        boolean changed = false;
        double templateP = prices.getOrDefault(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, -1.0);
        double ingotP = prices.getOrDefault(Items.NETHERITE_INGOT, -1.0);

        if (templateP > 0 && ingotP > 0) {
            changed |= upgrade(Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, templateP, ingotP, 500);
            changed |= upgrade(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, templateP, ingotP, 500);
            changed |= upgrade(Items.DIAMOND_AXE, Items.NETHERITE_AXE, templateP, ingotP, 500);
            changed |= upgrade(Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL, templateP, ingotP, 300);
            changed |= upgrade(Items.DIAMOND_HOE, Items.NETHERITE_HOE, templateP, ingotP, 300);
            changed |= upgrade(Items.DIAMOND_HELMET, Items.NETHERITE_HELMET, templateP, ingotP, 800);
            changed |= upgrade(Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE, templateP, ingotP, 1200);
            changed |= upgrade(Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS, templateP, ingotP, 1000);
            changed |= upgrade(Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS, templateP, ingotP, 700);
        }
        return changed;
    }

    private boolean upgrade(Item base, net.minecraft.world.item.Item result, double temp, double ingot, int extra) {
        if (prices.containsKey(result)) return false;
        double baseP = prices.getOrDefault(base, -1.0);
        if (baseP > 0) {
            setPrice(result, baseP + temp + ingot + extra);
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

    public double getPrice(Item item) { return prices.getOrDefault(item, 1.0); }
    public Map<Item, Double> getAllPrices() { return Collections.unmodifiableMap(prices); }
}