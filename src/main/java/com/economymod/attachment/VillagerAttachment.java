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
        transferVanillaToCustom(); // На всякий случай проверяем инвентарь при поиске кирки
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).getItem() instanceof PickaxeItem) return true;
        }
        return false;
    }

    // НОВЫЙ МЕТОД: Забирает предметы из скрытого ванильного инвентаря жителя и кладет в кастомный
    public void transferVanillaToCustom() {
        if (villager == null) return;
        SimpleContainer vanillaInv = villager.getInventory();
        for (int i = 0; i < vanillaInv.getContainerSize(); i++) {
            ItemStack stack = vanillaInv.getItem(i);
            if (!stack.isEmpty()) {
                // Пытаемся добавить предмет в наш 36-слотовый инвентарь
                ItemStack remaining = inventory.addItem(stack.copy());
                // Возвращаем остаток (если наш инвентарь забит) обратно в ванильный
                vanillaInv.setItem(i, remaining);
            }
        }
    }

    public BlockPos getPersonalChestPos() { return personalChestPos; }
    public void setPersonalChestPos(BlockPos pos) { this.personalChestPos = pos; }

    public void fillInitialLoot() {
        transferVanillaToCustom(); // Перед проверкой лута забираем ванильные вещи
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
            addRandom(Items.WHEAT, 10, 24); addRandom(Items.WHEAT_SEEDS, 8, 16); addRandom(Items.BONE_MEAL, 2, 5);
        } else if (prof == VillagerProfession.TOOLSMITH) {
            addRandom(Items.IRON_INGOT, 4, 8); addRandom(Items.COAL, 10, 20); addRandom(Items.IRON_PICKAXE, 1, 1);
        } else if (prof == VillagerProfession.BUTCHER) {
            addRandom(Items.BEEF, 8, 16); addRandom(Items.COAL, 5, 10);
        } else if (prof == VillagerProfession.CLERIC) {
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
        transferVanillaToCustom(); // Всегда сливаем инвентарь при запросе контейнера
        return inventory;
    }

    @Override public double getBalance() { return budget; }
    @Override public void setBalance(double balance) { this.budget = balance; }

    @Override public String getActorDisplayName() { return villager != null ? villager.getDisplayName().getString() : "Villager"; }

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
        tag.putBoolean("WasLootGenerated", wasLootGenerated);
        if (personalChestPos != null) tag.putLong("ChestPos", personalChestPos.asLong());
        ListTag invList = new ListTag();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) {
                // ИСПРАВЛЕНО: Получаем тег напрямую из метода save() и записываем в него слот
                CompoundTag slotTag = (CompoundTag) s.save(provider);
                slotTag.putInt("Slot", i);
                invList.add(slotTag);
            }
        }
        tag.put("Inventory", invList);

        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА СОХРАНЕНИЕ: Данные жителя [{}] успешно записаны на диск! Бюджет: {}⛀, Слотов заполнено: {}",
                getActorDisplayName(), budget, invList.size());

        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        budget = tag.getDouble("Budget");
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

        // ЛОГ ДЛЯ ДЕБАГА: Мы увидим, когда игра считывает жителя из файла сохранения мира
        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА ЗАГРУЗКА: Данные жителя [{}] успешно считаны с диска! Бюджет: {}⛀, Предметов восстановлено: {}",
                getActorDisplayName(), budget, invList.size());
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