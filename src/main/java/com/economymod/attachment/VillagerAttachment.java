package com.economymod.attachment;

import com.economymod.economy.IEconomicActor;
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

import java.util.*;

public class VillagerAttachment implements IEconomicActor {
    public static final int INVENTORY_SIZE = 36;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final Villager villager;
    private long budget;
    private float foodLevel = 20.0f;
    private boolean initialized = false;
    private VillagerProfession lastProfession = VillagerProfession.NONE;

    private List<Demand> cachedDemands = new ArrayList<>();
    private List<Offer> cachedOffers = new ArrayList<>();
    private int recalcCooldown = 0;

    public VillagerAttachment(Villager villager) {
        this.villager = villager;
        this.budget = villager != null ? 50 + villager.getRandom().nextInt(100) : 50;
    }

    private VillagerProfession getCurrentProfession() {
        return villager != null ? villager.getVillagerData().getProfession() : VillagerProfession.NONE;
    }

    private void ensureInitialized() {
        VillagerProfession currentProfession = getCurrentProfession();
        if (!initialized || currentProfession != lastProfession) {
            inventory.clearContent();
            if (currentProfession != VillagerProfession.NONE) {
                fillInventoryByProfession(currentProfession);
            }
            lastProfession = currentProfession;
            initialized = true;
        }
    }

    private void fillInventoryByProfession(VillagerProfession prof) {
        if (prof.equals(VillagerProfession.FARMER)) {
            inventory.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));
            inventory.setItem(1, new ItemStack(Items.BONE_MEAL, 1));
            inventory.setItem(2, new ItemStack(Items.WOODEN_HOE, 1));
            inventory.setItem(3, new ItemStack(Items.BREAD, 2));
        } else if (prof.equals(VillagerProfession.TOOLSMITH)) {
            inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
            inventory.setItem(1, new ItemStack(Items.COAL, 2));
            inventory.setItem(2, new ItemStack(Items.IRON_PICKAXE, 1));
        } else if (prof.equals(VillagerProfession.LIBRARIAN)) {
            inventory.setItem(0, new ItemStack(Items.BOOK, 2));
            inventory.setItem(1, new ItemStack(Items.PAPER, 4));
        } else if (prof.equals(VillagerProfession.BUTCHER)) {
            inventory.setItem(0, new ItemStack(Items.BEEF, 3));
            inventory.setItem(1, new ItemStack(Items.COOKED_BEEF, 1));
        } else if (prof.equals(VillagerProfession.CLERIC)) {
            inventory.setItem(0, new ItemStack(Items.GOLD_INGOT, 1));
            inventory.setItem(1, new ItemStack(Items.REDSTONE, 2));
        } else if (prof.equals(VillagerProfession.MASON)) {
            inventory.setItem(0, new ItemStack(Items.CLAY_BALL, 4));
            inventory.setItem(1, new ItemStack(Items.STONE, 8));
        } else if (prof.equals(VillagerProfession.WEAPONSMITH)) {
            inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            inventory.setItem(1, new ItemStack(Items.IRON_SWORD, 1));
            inventory.setItem(2, new ItemStack(Items.COAL, 1));
        } else if (prof.equals(VillagerProfession.NONE) || prof.equals(VillagerProfession.NITWIT)) {
            inventory.setItem(0, new ItemStack(Items.WHEAT, 2));
            inventory.setItem(1, new ItemStack(Items.COAL, 1));
        }
    }

    // ========= IEconomicActor =========
    @Override
    public SimpleContainer getInventory() {
        ensureInitialized();
        return inventory;
    }

    @Override
    public long getBalance() { return budget; }

    @Override
    public void setBalance(long balance) { this.budget = balance; }

    @Override
    public String getActorDisplayName() {
        return villager != null ? villager.getDisplayName().getString() : "Villager";
    }
    // ==================================

    public VillagerProfession getProfession() {
        return getCurrentProfession();
    }

    public void forceReinitialize() {
        this.initialized = false;
    }

    public static class Demand {
        public final ItemStack stack;
        public final int maxPricePerItem;
        public Demand(ItemStack stack, int maxPricePerItem) {
            this.stack = stack;
            this.maxPricePerItem = maxPricePerItem;
        }
    }

    public List<Demand> getDemands() {
        ensureInitialized();
        VillagerProfession currentProfession = getCurrentProfession();
        if (recalcCooldown > 0) {
            recalcCooldown--;
            return cachedDemands;
        }
        recalcCooldown = 200;
        cachedDemands.clear();

        if (foodLevel < 10) {
            int breadNeeded = Math.max(1, (int) ((20 - foodLevel) / 5));
            cachedDemands.add(new Demand(new ItemStack(Items.BREAD, breadNeeded), 3));
        }

        if (currentProfession.equals(VillagerProfession.FARMER)) {
            int seeds = countItem(Items.WHEAT_SEEDS);
            if (seeds < 8) cachedDemands.add(new Demand(new ItemStack(Items.WHEAT_SEEDS, 8 - seeds), 2));
            int bonemeal = countItem(Items.BONE_MEAL);
            if (bonemeal < 2) cachedDemands.add(new Demand(new ItemStack(Items.BONE_MEAL, 2 - bonemeal), 3));
            if (!hasItem(Items.WOODEN_HOE) && !hasItem(Items.STONE_HOE) && !hasItem(Items.IRON_HOE))
                cachedDemands.add(new Demand(new ItemStack(Items.WOODEN_HOE, 1), 5));
        } else if (currentProfession.equals(VillagerProfession.TOOLSMITH)) {
            if (!hasItem(Items.IRON_PICKAXE)) cachedDemands.add(new Demand(new ItemStack(Items.IRON_PICKAXE, 1), 20));
            int iron = countItem(Items.IRON_INGOT);
            if (iron < 4) cachedDemands.add(new Demand(new ItemStack(Items.IRON_INGOT, 4 - iron), 8));
        }

        long totalCost = 0;
        List<Demand> finalDemands = new ArrayList<>();
        for (Demand d : cachedDemands) {
            long cost = (long) d.stack.getCount() * d.maxPricePerItem;
            if (totalCost + cost <= budget * 0.3) {
                finalDemands.add(d);
                totalCost += cost;
            } else if (totalCost < budget * 0.3) {
                int remaining = (int) ((budget * 0.3 - totalCost) / d.maxPricePerItem);
                if (remaining > 0) {
                    ItemStack reduced = d.stack.copy();
                    reduced.setCount(remaining);
                    finalDemands.add(new Demand(reduced, d.maxPricePerItem));
                    totalCost += (long) remaining * d.maxPricePerItem;
                }
            }
        }
        cachedDemands = finalDemands;
        return cachedDemands;
    }

    public static class Offer {
        public final ItemStack stack;
        public final int minPricePerItem;
        public Offer(ItemStack stack, int minPricePerItem) {
            this.stack = stack;
            this.minPricePerItem = minPricePerItem;
        }
    }

    public List<Offer> getOffers() {
        ensureInitialized();
        if (recalcCooldown > 0) return cachedOffers;
        cachedOffers.clear();
        Map<Item, Integer> minStock = getMinimumStock();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            int min = minStock.getOrDefault(item, 0);
            if (stack.getCount() > min) {
                int amountToSell = stack.getCount() - min;
                double surplusFactor = (double) amountToSell / (min + 1);
                double sellFactor = Math.max(0.5, 1.0 - surplusFactor * 0.3);
                int basePrice = getBasePrice(item);
                int minPrice = (int) (basePrice * sellFactor);
                cachedOffers.add(new Offer(new ItemStack(item, amountToSell), minPrice));
            }
        }
        recalcCooldown = 200;
        return cachedOffers;
    }

    private int getBasePrice(Item item) {
        if (item == Items.BREAD) return 1;
        if (item == Items.WHEAT_SEEDS) return 1;
        if (item == Items.BONE_MEAL) return 1;
        if (item == Items.COAL) return 1;
        if (item == Items.IRON_INGOT) return 4;
        if (item == Items.IRON_PICKAXE) return 12;
        if (item == Items.BOOK) return 5;
        if (item == Items.PAPER) return 1;
        if (item == Items.BEEF) return 2;
        if (item == Items.GOLD_INGOT) return 8;
        if (item == Items.REDSTONE) return 3;
        if (item == Items.CLAY_BALL) return 1;
        if (item == Items.STONE) return 1;
        if (item == Items.IRON_SWORD) return 15;
        return 1;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < INVENTORY_SIZE; i++) if (inventory.getItem(i).is(item)) count += inventory.getItem(i).getCount();
        return count;
    }

    private boolean hasItem(Item item) {
        for (int i = 0; i < INVENTORY_SIZE; i++) if (inventory.getItem(i).is(item)) return true;
        return false;
    }

    private Map<Item, Integer> getMinimumStock() {
        Map<Item, Integer> map = new HashMap<>();
        VillagerProfession prof = getCurrentProfession();
        if (prof.equals(VillagerProfession.FARMER)) { map.put(Items.WHEAT_SEEDS, 4); map.put(Items.BREAD, 2); }
        else if (prof.equals(VillagerProfession.TOOLSMITH)) { map.put(Items.IRON_INGOT, 2); map.put(Items.IRON_PICKAXE, 1); }
        return map;
    }

    // ==================== СЕРИАЛИЗАЦИЯ ====================
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Budget", budget);
        tag.putFloat("FoodLevel", foodLevel);
        tag.putBoolean("Initialized", initialized);
        tag.putString("LastProfession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(lastProfession).toString());
        ListTag invList = new ListTag();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("Slot", i);
                s.save(provider, slotTag);
                invList.add(slotTag);
            }
        }
        tag.put("Inventory", invList);
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        budget = tag.getLong("Budget");
        foodLevel = tag.getFloat("FoodLevel");
        initialized = tag.getBoolean("Initialized");
        lastProfession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(tag.getString("LastProfession")));
        if (lastProfession == null) lastProfession = VillagerProfession.NONE;
        inventory.clearContent();
        ListTag invList = tag.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < invList.size(); i++) {
            CompoundTag slotTag = invList.getCompound(i);
            int slot = slotTag.getInt("Slot");
            if (slot >= 0 && slot < INVENTORY_SIZE) {
                ItemStack stack = ItemStack.parse(provider, slotTag).orElse(ItemStack.EMPTY);
                inventory.setItem(slot, stack);
            }
        }
    }
}