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

import java.util.*;

public class VillagerAttachment implements IEconomicActor {
    public static final int INVENTORY_SIZE = 36;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final Villager villager;
    private long budget;
    private boolean wasLootGenerated = false;
    private VillagerProfession lastProfession = VillagerProfession.NONE;

    private final DesireProcessor desireProcessor = new DesireProcessor(this);
    private List<Demand> cachedDemands = new ArrayList<>();
    private List<Offer> cachedOffers = new ArrayList<>();
    private int recalcCooldown = 0;

    public VillagerAttachment(Villager villager) {
        this.villager = villager;
        this.budget = villager != null ? 50 + villager.getRandom().nextInt(150) : 100;
    }

    public VillagerProfession getProfession() {
        return villager != null ? villager.getVillagerData().getProfession() : VillagerProfession.NONE;
    }

    public boolean wasLootGenerated() { return wasLootGenerated; }
    public void setLootGenerated(boolean val) { this.wasLootGenerated = val; }

    public void fillInitialLoot() {
        if (wasLootGenerated) return;
        VillagerProfession prof = getProfession();
        if (prof != VillagerProfession.NONE && prof != VillagerProfession.NITWIT) {
            generateLootTable(prof);
        }
        wasLootGenerated = true;
    }

    // МЕТОД ДЛЯ КОМАНДЫ (ПРИНУДИТЕЛЬНЫЙ)
    public void forceLootGeneration() {
        VillagerProfession prof = getProfession();
        if (prof != VillagerProfession.NONE && prof != VillagerProfession.NITWIT) {
            inventory.clearContent(); // Очищаем старое перед принудительным спавном
            generateLootTable(prof);
            wasLootGenerated = true;
        }
    }

    private void generateLootTable(VillagerProfession prof) {
        if (villager == null) return;

        if (prof == VillagerProfession.FARMER) {
            addRandom(Items.WHEAT, 10, 24);
            addRandom(Items.POTATO, 5, 12);
            addRandom(Items.WHEAT_SEEDS, 8, 16);
            addRandom(Items.BREAD, 2, 5);
        } else if (prof == VillagerProfession.TOOLSMITH) {
            addRandom(Items.IRON_INGOT, 4, 9);
            addRandom(Items.COAL, 12, 24);
            addRandom(Items.STONE_PICKAXE, 1, 1);
        } else if (prof == VillagerProfession.ARMORER) {
            addRandom(Items.IRON_INGOT, 6, 12);
            addRandom(Items.COAL, 10, 20);
            addRandom(Items.IRON_HELMET, 1, 1);
        } else if (prof == VillagerProfession.WEAPONSMITH) {
            addRandom(Items.IRON_INGOT, 5, 10);
            addRandom(Items.FLINT, 4, 8);
            addRandom(Items.IRON_SWORD, 1, 1);
        } else if (prof == VillagerProfession.LIBRARIAN) {
            addRandom(Items.PAPER, 20, 48);
            addRandom(Items.BOOK, 3, 7);
            addRandom(Items.FEATHER, 5, 10);
        } else if (prof == VillagerProfession.CLERIC) {
            addRandom(Items.REDSTONE, 10, 20);
            addRandom(Items.GOLD_INGOT, 2, 5);
            addRandom(Items.ROTTEN_FLESH, 16, 32);
        } else if (prof == VillagerProfession.BUTCHER) {
            addRandom(Items.BEEF, 8, 16);
            addRandom(Items.COAL, 5, 10);
        } else if (prof == VillagerProfession.MASON) {
            addRandom(Items.CLAY_BALL, 16, 32);
            addRandom(Items.STONE, 32, 64);
        } else if (prof == VillagerProfession.FLETCHER) {
            addRandom(Items.STICK, 16, 32);
            addRandom(Items.FLINT, 10, 20);
            addRandom(Items.FEATHER, 10, 20);
        } else if (prof == VillagerProfession.LEATHERWORKER) {
            addRandom(Items.LEATHER, 6, 15);
            addRandom(Items.RABBIT_HIDE, 4, 8);
        } else if (prof == VillagerProfession.SHEPHERD) {
            addRandom(Items.WHITE_WOOL, 8, 16);
            addRandom(Items.SHEARS, 1, 1);
        } else if (prof == VillagerProfession.FISHERMAN) {
            addRandom(Items.COD, 10, 20);
            addRandom(Items.STRING, 5, 12);
        }
    }

    private void addRandom(Item item, int min, int max) {
        int count = min + new Random().nextInt(max - min + 1);
        inventory.addItem(new ItemStack(item, count));
    }

    @Override public SimpleContainer getInventory() { return inventory; }
    @Override public long getBalance() { return budget; }
    @Override public void setBalance(long balance) { this.budget = balance; }
    @Override public String getActorDisplayName() { return villager != null ? villager.getDisplayName().getString() : "Villager"; }

    public List<Demand> getDemands() {
        if (recalcCooldown > 0) { recalcCooldown--; return cachedDemands; }
        recalcCooldown = 100;
        cachedDemands.clear();
        List<Desire> smartDesires = desireProcessor.calculateDesires();
        for (Desire desire : smartDesires) {
            double rawPrice = PriceCalculator.getRawPrice(desire.stack.getItem());
            int maxPrice = (int) (rawPrice * 1.5);
            cachedDemands.add(new Demand(desire.stack, Math.max(1, maxPrice)));
        }
        return cachedDemands;
    }

    public List<Offer> getOffers() {
        cachedOffers.clear();
        VillagerProfession prof = getProfession();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            int keep = isProfessionalItem(s.getItem(), prof) ? 2 : 0;
            if (s.getCount() > keep) {
                int price = (int) Math.max(1, PriceCalculator.getRawPrice(s.getItem()));
                cachedOffers.add(new Offer(new ItemStack(s.getItem(), s.getCount() - keep), (int)(price * 0.8)));
            }
        }
        return cachedOffers;
    }

    private boolean isProfessionalItem(Item item, VillagerProfession prof) {
        if (prof == VillagerProfession.FARMER) return item == Items.WHEAT || item == Items.BREAD || item == Items.WHEAT_SEEDS;
        if (prof == VillagerProfession.TOOLSMITH || prof == VillagerProfession.WEAPONSMITH) return item == Items.IRON_INGOT || item == Items.COAL;
        return false;
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Budget", budget);
        tag.putBoolean("WasLootGenerated", wasLootGenerated);
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
        wasLootGenerated = tag.getBoolean("WasLootGenerated");
        String profKey = tag.getString("LastProfession");
        lastProfession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(profKey));
        if (lastProfession == null) lastProfession = VillagerProfession.NONE;
        inventory.clearContent();
        ListTag invList = tag.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < invList.size(); i++) {
            CompoundTag slotTag = invList.getCompound(i);
            int slot = slotTag.getInt("Slot");
            if (slot >= 0 && slot < INVENTORY_SIZE) {
                inventory.setItem(slot, ItemStack.parse(provider, slotTag).orElse(ItemStack.EMPTY));
            }
        }
    }

    public static class Demand {
        public final ItemStack stack;
        public final int maxPricePerItem;
        public Demand(ItemStack stack, int maxPricePerItem) { this.stack = stack; this.maxPricePerItem = maxPricePerItem; }
    }

    public static class Offer {
        public final ItemStack stack;
        public final int minPricePerItem;
        public Offer(ItemStack stack, int minPricePerItem) { this.stack = stack; this.minPricePerItem = minPricePerItem; }
    }

    @Override public boolean wantsToBuy(ItemStack stack) { return false; }
    @Override public BlockPos getPosition() { return villager != null ? villager.blockPosition() : null; }
}