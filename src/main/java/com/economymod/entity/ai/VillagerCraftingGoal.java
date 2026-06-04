package com.economymod.entity.ai;

import com.economymod.EconomyMod;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class VillagerCraftingGoal extends Goal {
    private final Villager villager;
    private BlockPos tablePos;
    private int craftingTick = 0;
    private VillagerRecipe activeRecipe;

    // Расширенная книга рецептов выживания для жителей
    private static final List<VillagerRecipe> RECIPES = List.of(
            new VillagerRecipe(Items.BREAD, 1, Items.WHEAT, 3, null, 0),

            // Защитное снаряжение и оружие
            new VillagerRecipe(Items.IRON_SWORD, 1, Items.IRON_INGOT, 2, Items.STICK, 1),
            new VillagerRecipe(Items.SHIELD, 1, Items.OAK_PLANKS, 6, Items.IRON_INGOT, 1),
            new VillagerRecipe(Items.BOW, 1, Items.STICK, 3, Items.STRING, 3),
            new VillagerRecipe(Items.ARROW, 4, Items.FLINT, 1, Items.STICK, 1),
            new VillagerRecipe(Items.LEATHER_CHESTPLATE, 1, Items.LEATHER, 8, null, 0),
            new VillagerRecipe(Items.IRON_CHESTPLATE, 1, Items.IRON_INGOT, 8, null, 0),

            // Инструменты работы
            new VillagerRecipe(Items.IRON_PICKAXE, 1, Items.IRON_INGOT, 3, Items.STICK, 2),
            new VillagerRecipe(Items.STONE_PICKAXE, 1, Items.COBBLESTONE, 3, Items.STICK, 2),
            new VillagerRecipe(Items.STONE_HOE, 1, Items.COBBLESTONE, 2, Items.STICK, 2),
            new VillagerRecipe(Items.WOODEN_HOE, 1, Items.OAK_PLANKS, 2, Items.STICK, 2),

            // Бытовое жизнеобеспечение
            new VillagerRecipe(Items.TORCH, 4, Items.COAL, 1, Items.STICK, 1),
            new VillagerRecipe(Items.TORCH, 4, Items.CHARCOAL, 1, Items.STICK, 1),
            new VillagerRecipe(Items.CRAFTING_TABLE, 1, Items.OAK_PLANKS, 4, null, 0),
            new VillagerRecipe(Items.CHEST, 1, Items.OAK_PLANKS, 8, null, 0),

            // Промежуточные полуфабрикаты (подлежат умному контролю)
            new VillagerRecipe(Items.OAK_PLANKS, 4, Items.OAK_LOG, 1, null, 0),
            new VillagerRecipe(Items.STICK, 4, Items.OAK_PLANKS, 2, null, 0)
    );

    public VillagerCraftingGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().getGameTime() % 20 != 0) return false;

        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return false;

        // Поиск рецепта по жесткому алгоритму умного планирования нужд
        this.activeRecipe = evaluateAvailableRecipe(att);
        if (this.activeRecipe == null) return false;

        BlockPos current = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(current.offset(-10, -2, -10), current.offset(10, 2, 10))) {
            if (villager.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                this.tablePos = pos.immutable();
                return true;
            }
        }
        return false;
    }

    private VillagerRecipe evaluateAvailableRecipe(VillagerAttachment att) {
        SimpleContainer inv = att.getInventory();
        VillagerProfession prof = villager.getVillagerData().getProfession();

        for (VillagerRecipe recipe : RECIPES) {
            if (hasIngredients(inv, recipe)) {

                // Умный контроль полуфабрикатов: Житель НЕ крафтит доски/палки впустую!
                if (recipe.result == Items.OAK_PLANKS) {
                    if (hasDemandForSemiProduct(att, Items.OAK_PLANKS)) return recipe;
                    continue;
                }
                if (recipe.result == Items.STICK) {
                    if (hasDemandForSemiProduct(att, Items.STICK)) return recipe;
                    continue;
                }
                if (recipe.result == Items.CRAFTING_TABLE) {
                    // Верстак создается только если его нет поблизости в шахте
                    continue;
                }

                // Логическое планирование готовых изделий
                if (recipe.result == Items.BREAD) {
                    if (att.getHunger() < 14.0) return recipe; // Голоден -> крафтит хлеб
                } else if (recipe.result == Items.IRON_PICKAXE || recipe.result == Items.STONE_PICKAXE) {
                    if ((prof == VillagerProfession.TOOLSMITH || prof == VillagerProfession.ARMORER) && !att.hasPickaxe()) {
                        return recipe; // Нужна кирка для работы
                    }
                } else if (recipe.result == Items.STONE_HOE || recipe.result == Items.WOODEN_HOE) {
                    if (prof == VillagerProfession.FARMER) {
                        return recipe; // Мотыга фермеру
                    }
                } else if (recipe.result == Items.IRON_SWORD || recipe.result == Items.SHIELD || recipe.result == Items.BOW) {
                    // Мечи, щиты и луки крафтят стражники или жители для самообороны, если есть угроза
                    if (prof == VillagerProfession.WEAPONSMITH || prof == VillagerProfession.ARMORER || villager.getHealth() < villager.getMaxHealth() - 4.0f) {
                        return recipe;
                    }
                } else if (recipe.result == Items.ARROW || recipe.result == Items.TORCH) {
                    return recipe; // Стрелы и факелы всегда полезны в быту
                }
            }
        }
        return null;
    }

    /**
     * Потоковое планирование зависимых ингредиентов:
     * Проверяет, требуется ли палка или доска для крафта какого-то конечного нужного инструмента,
     * который житель ХОЧЕТ собрать, но ему не хватает этого сырья.
     */
    private boolean hasDemandForSemiProduct(VillagerAttachment att, Item semiProduct) {
        SimpleContainer inv = att.getInventory();
        VillagerProfession prof = villager.getVillagerData().getProfession();

        for (VillagerRecipe recipe : RECIPES) {
            // Пропускаем сами полуфабрикаты во избежание вечной рекурсии
            if (recipe.result == Items.OAK_PLANKS || recipe.result == Items.STICK || recipe.result == Items.CRAFTING_TABLE) {
                continue;
            }

            // Требуется ли этот полуфабрикат в конечном рецепте?
            boolean usesSemi = (recipe.ing1 == semiProduct) || (recipe.ing2 == semiProduct);
            if (usesSemi) {
                // Проверяем, есть ли у жителя основной материал (например, железо для меча/кирки)
                Item mainIng = (recipe.ing1 == semiProduct) ? recipe.ing2 : recipe.ing1;
                int mainCount = (recipe.ing1 == semiProduct) ? recipe.count2 : recipe.count1;

                boolean hasMain = mainIng == null || countItems(inv, mainIng) >= mainCount;
                if (hasMain) {
                    // Если основного материала хватает, но палок/досок дефицит — даем добро на крафт полуфабриката!
                    int currentSemiCount = countItems(inv, semiProduct);
                    int neededSemiCount = (recipe.ing1 == semiProduct) ? recipe.count1 : recipe.count2;
                    if (currentSemiCount < neededSemiCount) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int countItems(SimpleContainer inv, Item item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) count += s.getCount();
        }
        return count;
    }

    private boolean hasIngredients(SimpleContainer inv, VillagerRecipe recipe) {
        int count1 = countItems(inv, recipe.ing1);
        int count2 = recipe.ing2 != null ? countItems(inv, recipe.ing2) : 0;
        boolean hasSecond = recipe.ing2 == null || count2 >= recipe.count2;
        return count1 >= recipe.count1 && hasSecond;
    }

    @Override
    public void start() {
        craftingTick = 0;
        if (tablePos != null) {
            villager.getNavigation().moveTo(tablePos.getX(), tablePos.getY(), tablePos.getZ(), 0.6D);
        }
    }

    @Override
    public void tick() {
        if (tablePos == null || activeRecipe == null) return;

        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);

        villager.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 1.0, tablePos.getZ() + 0.5);

        if (villager.distanceToSqr(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5) < 3.0) {
            villager.getNavigation().stop();
            craftingTick++;

            if (craftingTick % 10 == 0 && villager.level() instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                        tablePos.getX() + 0.5, tablePos.getY() + 1.1, tablePos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.02);
            }

            if (craftingTick >= 60) {
                performCraft();
                craftingTick = 0;
            }
        } else {
            villager.getNavigation().moveTo(tablePos.getX(), tablePos.getY(), tablePos.getZ(), 0.6D);
        }
    }

    private void performCraft() {
        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null || activeRecipe == null) return;

        consumeItem(activeRecipe.ing1, activeRecipe.count1);
        if (activeRecipe.ing2 != null) {
            consumeItem(activeRecipe.ing2, activeRecipe.count2);
        }

        att.getInventory().addItem(new ItemStack(activeRecipe.result, activeRecipe.resultCount));

        villager.level().playSound(null, tablePos, net.minecraft.sounds.SoundEvents.VILLAGER_WORK_TOOLSMITH,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);

        EconomyMod.LOGGER.info("ЭКОНОМИКА КРАФТ: {} успешно скрафтил {} x{} на верстаке!",
                villager.getName().getString(), activeRecipe.result.getDescriptionId(), activeRecipe.resultCount);

        this.tablePos = null;
        this.activeRecipe = null;
    }

    private void consumeItem(Item item, int count) {
        var inv = villager.getData(ModAttachments.VILLAGER.get()).getInventory();
        int remaining = count;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return tablePos != null && activeRecipe != null && villager.level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE);
    }

    private static class VillagerRecipe {
        final Item result;
        final int resultCount;
        final Item ing1;
        final int count1;
        final Item ing2;
        final int count2;

        VillagerRecipe(Item result, int resultCount, Item ing1, int count1, Item ing2, int count2) {
            this.result = result;
            this.resultCount = resultCount;
            this.ing1 = ing1;
            this.count1 = count1;
            this.ing2 = ing2;
            this.count2 = count2;
        }
    }
}