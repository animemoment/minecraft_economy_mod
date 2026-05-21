package com.economymod.attachment;

import com.economymod.economy.IEconomicActor;
import com.economymod.economy.desire.Desire;
import com.economymod.economy.desire.DesireProcessor;
import com.economymod.economy.PriceCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;

import java.util.*;

public class VillagerAttachment implements IEconomicActor {
    public static final int INVENTORY_SIZE = 36;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final Villager villager;
    private double budget;
    private double hunger = 20.0; // ДОБАВЛЕНО: Показатель сытости от 0 до 20.0
    private boolean wasLootGenerated = false;
    private BlockPos personalChestPos = null;

    private final DesireProcessor desireProcessor = new DesireProcessor(this);
    private final List<Demand> cachedDemands = new ArrayList<>();
    private final List<Offer> cachedOffers = new ArrayList<>();
    private int recalcCooldown = 0;

    public VillagerAttachment(Villager villager) {
        this.villager = villager;
        this.budget = villager != null ? 50.0 + villager.getRandom().nextInt(150) : 100.0;
    }

    public VillagerProfession getProfession() {
        return villager != null ? villager.getVillagerData().getProfession() : VillagerProfession.NONE;
    }

    public boolean hasPickaxe() {
        transferVanillaToCustom();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).getItem() instanceof PickaxeItem) return true;
        }
        return false;
    }

    public ItemStack getActivePickaxe() {
        transferVanillaToCustom();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void transferVanillaToCustom() {
        if (villager == null) return;

        if (villager.level() instanceof ServerLevel sl) {
            List<net.minecraft.world.entity.item.ItemEntity> groundItems = sl.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    villager.getBoundingBox().inflate(4.0)
            );

            for (net.minecraft.world.entity.item.ItemEntity itemEntity : groundItems) {
                ItemStack stack = itemEntity.getItem();
                if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem ||
                        stack.is(Items.COAL) || stack.is(Items.CHARCOAL) ||
                        stack.is(Items.RAW_IRON) || stack.is(Items.RAW_GOLD) || stack.is(Items.RAW_COPPER) ||
                        stack.is(Items.DIAMOND) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.EMERALD)) {

                    ItemStack remaining = inventory.addItem(stack.copy());
                    if (remaining.getCount() < stack.getCount()) {
                        itemEntity.setItem(remaining);
                        sl.playSound(null, villager.blockPosition(),
                                net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                    }
                }
            }
        }

        SimpleContainer vanillaInv = villager.getInventory();
        for (int i = 0; i < vanillaInv.getContainerSize(); i++) {
            ItemStack stack = vanillaInv.getItem(i);
            if (!stack.isEmpty()) {
                ItemStack remaining = inventory.addItem(stack.copy());
                vanillaInv.setItem(i, remaining);
            }
        }
    }

    public BlockPos getPersonalChestPos() { return personalChestPos; }
    public void setPersonalChestPos(BlockPos pos) { this.personalChestPos = pos; }

    public void fillInitialLoot() {
        transferVanillaToCustom();
        if (wasLootGenerated) return;
        VillagerProfession prof = getProfession();
        if (prof != VillagerProfession.NONE && prof != VillagerProfession.NITWIT) {
            generateLootTable(prof);
            wasLootGenerated = true;
        }
    }

    private void generateLootTable(VillagerProfession prof) {
        if (villager == null) return;
        if (prof == VillagerProfession.FARMER) {
            addRandom(Items.BREAD, 8, 16); // Еда на старт
            addRandom(Items.WHEAT_SEEDS, 8, 16); addRandom(Items.BONE_MEAL, 2, 5);
        } else if (prof == VillagerProfession.TOOLSMITH) {
            addRandom(Items.BREAD, 6, 12);
            addRandom(Items.IRON_INGOT, 4, 8); addRandom(Items.COAL, 10, 20); addRandom(Items.IRON_PICKAXE, 1, 1);
        } else if (prof == VillagerProfession.BUTCHER) {
            addRandom(Items.COOKED_BEEF, 8, 16); // Мясо!
            addRandom(Items.COAL, 5, 10);
        } else if (prof == VillagerProfession.CLERIC) {
            addRandom(Items.BREAD, 6, 12);
            addRandom(Items.REDSTONE, 10, 20); addRandom(Items.GOLD_INGOT, 2, 5);
        }
    }

    private void addRandom(Item item, int min, int max) {
        int count = min + new Random().nextInt(max - min + 1);
        inventory.addItem(new ItemStack(item, count));
    }

    public List<Integer> getTrashSlots() {
        transferVanillaToCustom();
        List<Integer> trash = new ArrayList<>();
        VillagerProfession prof = getProfession();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isUseful(stack.getItem(), prof)) trash.add(i);
        }
        return trash;
    }

    private boolean isUseful(Item item, VillagerProfession prof) {
        if (item.getFoodProperties(item.getDefaultInstance(), villager) != null) return true;
        if (item == Items.IRON_INGOT || item == Items.COAL || item == Items.STICK || item == Items.GOLD_NUGGET || item instanceof PickaxeItem) return true;
        if (prof == VillagerProfession.FARMER) return item == Items.WHEAT_SEEDS || item == Items.WHEAT || item == Items.BONE_MEAL;
        if (prof == VillagerProfession.TOOLSMITH) return item == Items.RAW_IRON || item == Items.IRON_PICKAXE;
        return false;
    }

    @Override public SimpleContainer getInventory() {
        transferVanillaToCustom();
        return inventory;
    }

    @Override public double getBalance() { return budget; }
    @Override public void setBalance(double balance) { this.budget = balance; }

    @Override public String getActorDisplayName() { return villager != null ? villager.getDisplayName().getString() : "Villager"; }

    // ДОБАВЛЕНО: Управление сытостью (Геттеры, расход сытости и урон от голодания)
    public double getHunger() { return hunger; }
    // ИСПРАВЛЕНО: Используем стандартные методы Java Math вместо Mth.clamp() для идеальной совместимости
    public void setHunger(double hunger) {
        this.hunger = Math.max(0.0, Math.min(20.0, hunger));
    }

    public void decreaseHunger(double amount) {
        if (villager == null || villager.level().isClientSide()) return;
        this.hunger = Math.max(0.0, this.hunger - amount);

        // Если сытость упала до нуля — наносим 1 единицу урона от голода каждые 2 секунды (40 тиков)
        if (this.hunger <= 0.0 && villager.level().getGameTime() % 40 == 0 && villager.isAlive()) {
            villager.hurt(villager.damageSources().starve(), 1.0F);
        }
    }

    // ИСПРАВЛЕНО: Безопасное поглощение еды с созданием копии стака для предотвращения краша "Empty stacks are not allowed"
    public void eatFoodIfHungry() {
        if (villager == null || villager.level().isClientSide() || !villager.isAlive()) return;

        if (this.hunger <= 14.0) {
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && stack.getItem().getFoodProperties(stack, villager) != null) {
                    var food = stack.getItem().getFoodProperties(stack, villager);

                    // 1. Создаем БЕЗОПАСНУЮ копию предмета для частичек, пока стак еще не пустой!
                    ItemStack particleStack = stack.copy();

                    // 2. Восстанавливаем сытость жителя
                    this.hunger = Math.min(20.0, this.hunger + food.nutrition());

                    // 3. Забираем 1 еду из рюкзака (стак может стать пустым, но это больше не вызовет краш)
                    stack.shrink(1);

                    // 4. Ванильный звук поедания еды
                    villager.level().playSound(null, villager.blockPosition(),
                            net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);

                    // 5. Отрисовываем частички, используя нашу сохраненную копию!
                    if (villager.level() instanceof ServerLevel sl) {
                        sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, particleStack),
                                villager.getX(), villager.getY() + 1.2, villager.getZ(), 12, 0.1, 0.1, 0.1, 0.05);
                    }

                    com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА: Житель {} поел {}! Сытость восстановлена до: {}",
                            getActorDisplayName(), particleStack.getItem().getName(particleStack).getString(), hunger);
                    break;
                }
            }
        }
    }

    // ДОБАВЛЕНО: Метод ежесекундного тика ИИ метаболизма
    public void tick() {
        if (villager == null || villager.level().isClientSide() || !villager.isAlive()) return;

        // Каждые 5 секунд (100 тиков) житель тратит 0.15 сытости (пассивный расход калорий)
        if (villager.level().getGameTime() % 100 == 0) {
            decreaseHunger(0.15);
            eatFoodIfHungry(); // Пытаемся пообедать, если голодны
        }
    }

    public List<Demand> getDemands() {
        transferVanillaToCustom();
        if (recalcCooldown > 0) { recalcCooldown--; return cachedDemands; }
        recalcCooldown = 100;
        cachedDemands.clear();
        List<Desire> smartDesires = desireProcessor.calculateDesires();
        for (Desire desire : smartDesires) {
            double rawPrice = PriceCalculator.getRawPrice(desire.stack.getItem());
            double maxPrice = rawPrice * 1.5;
            cachedDemands.add(new Demand(desire.stack, (int)maxPrice));
        }
        return cachedDemands;
    }

    public List<Offer> getOffers() {
        transferVanillaToCustom();
        cachedOffers.clear();
        VillagerProfession prof = getProfession();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            int keep = isProfessionalItem(s.getItem(), prof) ? 2 : 0;
            // Еду оставляем себе, чтобы не умереть с голоду! (держим минимум 4 штуки)
            if (s.getItem().getFoodProperties(s, villager) != null) {
                keep = Math.max(keep, 4);
            }
            if (s.getCount() > keep) {
                double price = PriceCalculator.getRawPrice(s.getItem());
                cachedOffers.add(new Offer(new ItemStack(s.getItem(), s.getCount() - keep), (int)(price * 0.8)));
            }
        }
        return cachedOffers;
    }

    private boolean isProfessionalItem(Item item, VillagerProfession prof) {
        if (prof == VillagerProfession.FARMER) return item == Items.WHEAT || item == Items.BREAD;
        if (prof == VillagerProfession.TOOLSMITH) return item == Items.IRON_INGOT || item == Items.COAL;
        return false;
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Budget", budget);
        tag.putDouble("Hunger", hunger); // ДОБАВЛЕНО: Сохраняем голод на диск
        tag.putBoolean("WasLootGenerated", wasLootGenerated);
        if (personalChestPos != null) tag.putLong("ChestPos", personalChestPos.asLong());
        ListTag invList = new ListTag();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) {
                CompoundTag slotTag = (CompoundTag) s.save(provider);
                slotTag.putInt("Slot", i);
                invList.add(slotTag);
            }
        }
        tag.put("Inventory", invList);

        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА СОХРАНЕНИЕ: Данные жителя [{}] успешно записаны на диск! Бюджет: {}⛀, Голод: {}, Слотов заполнено: {}",
                getActorDisplayName(), budget, hunger, invList.size());

        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        budget = tag.getDouble("Budget");
        hunger = tag.contains("Hunger") ? tag.getDouble("Hunger") : 20.0; // ДОБАВЛЕНО: Загружаем голод с диска
        wasLootGenerated = tag.getBoolean("WasLootGenerated");
        if (tag.contains("ChestPos")) personalChestPos = BlockPos.of(tag.getLong("ChestPos"));
        inventory.clearContent();
        ListTag invList = tag.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < invList.size(); i++) {
            CompoundTag slotTag = invList.getCompound(i);
            int slot = slotTag.getInt("Slot");
            if (slot >= 0 && slot < INVENTORY_SIZE) {
                inventory.setItem(slot, ItemStack.parse(provider, slotTag).orElse(ItemStack.EMPTY));
            }
        }

        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА ЗАГРУЗКА: Данные жителя [{}] успешно считаны с диска! Бюджет: {}⛀, Голод: {}, Предметов восстановлено: {}",
                getActorDisplayName(), budget, hunger, invList.size());
    }

    public boolean wasLootGenerated() { return wasLootGenerated; }
    public void setLootGenerated(boolean val) { this.wasLootGenerated = val; }
    @Override public boolean wantsToBuy(ItemStack stack) { return false; }
    @Override public BlockPos getPosition() { return villager != null ? villager.blockPosition() : null; }
    public static class Demand { public final ItemStack stack; public final int maxPricePerItem; public Demand(ItemStack stack, int maxPricePerItem) { this.stack = stack; this.maxPricePerItem = maxPricePerItem; } }
    public static class Offer { public final ItemStack stack; public final int minPricePerItem; public Offer(ItemStack stack, int minPricePerItem) { this.stack = stack; this.minPricePerItem = minPricePerItem; } }

    public void forceLootGeneration() {
        VillagerProfession prof = getProfession();
        if (prof != VillagerProfession.NONE && prof != VillagerProfession.NITWIT) {
            inventory.clearContent();
            generateLootTable(prof);
            wasLootGenerated = true;
        }
    }
}