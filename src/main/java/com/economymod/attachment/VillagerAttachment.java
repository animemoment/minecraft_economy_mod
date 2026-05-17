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
    private boolean initialized = false;
    private VillagerProfession lastProfession = VillagerProfession.NONE;

    // Новая система желаний
    private final DesireProcessor desireProcessor = new DesireProcessor(this);

    private List<Demand> cachedDemands = new ArrayList<>();
    private List<Offer> cachedOffers = new ArrayList<>();
    private int recalcCooldown = 0;

    public VillagerAttachment(Villager villager) {
        this.villager = villager;
        // Начальный бюджет 100-300 монет
        this.budget = villager != null ? 100 + villager.getRandom().nextInt(200) : 100;
    }

    public VillagerProfession getProfession() {
        return villager != null ? villager.getVillagerData().getProfession() : VillagerProfession.NONE;
    }

    public void ensureInitialized() {
        VillagerProfession currentProfession = getProfession();
        if (!initialized || currentProfession != lastProfession) {
            inventory.clearContent();
            if (currentProfession != VillagerProfession.NONE) {
                fillInventoryRandomly(currentProfession);
            }
            lastProfession = currentProfession;
            initialized = true;
        }
    }

    private void fillInventoryRandomly(VillagerProfession prof) {
        if (villager == null) return;
        Random random = new Random();
        List<Item> pool = new ArrayList<>();

        if (prof.equals(VillagerProfession.FARMER)) {
            pool.addAll(List.of(Items.WHEAT, Items.POTATO, Items.CARROT, Items.BREAD));
        } else if (prof.equals(VillagerProfession.TOOLSMITH) || prof.equals(VillagerProfession.WEAPONSMITH)) {
            pool.addAll(List.of(Items.IRON_INGOT, Items.COAL, Items.IRON_AXE, Items.IRON_SWORD));
        } else if (prof.equals(VillagerProfession.LIBRARIAN)) {
            pool.addAll(List.of(Items.BOOK, Items.PAPER, Items.FEATHER));
        } else {
            pool.addAll(List.of(Items.STICK, Items.APPLE, Items.COBBLESTONE));
        }

        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            Item item = pool.get(random.nextInt(pool.size()));
            inventory.addItem(new ItemStack(item, 3 + random.nextInt(7)));
        }
    }

    @Override
    public SimpleContainer getInventory() {
        ensureInitialized();
        return inventory;
    }

    @Override
    public long getBalance() {
        return budget;
    }

    @Override
    public void setBalance(long balance) {
        this.budget = balance;
    }

    @Override
    public String getActorDisplayName() {
        return villager != null ? villager.getDisplayName().getString() : "Villager";
    }

    /**
     * Преобразует "Умные желания" из DesireProcessor в список Demand для торговли.
     */
    public List<Demand> getDemands() {
        ensureInitialized();
        if (recalcCooldown > 0) {
            recalcCooldown--;
            return cachedDemands;
        }
        recalcCooldown = 100; // Пересчет раз в 5 секунд

        cachedDemands.clear();
        List<Desire> smartDesires = desireProcessor.calculateDesires();

        for (Desire desire : smartDesires) {
            // Максимальная цена покупки = базовая цена * 1.5 (готов переплатить за нужду)
            double rawPrice = PriceCalculator.getRawPrice(desire.stack.getItem());
            int maxPrice = (int) (rawPrice * 1.5);
            cachedDemands.add(new Demand(desire.stack, Math.max(1, maxPrice)));
        }

        return cachedDemands;
    }

    public List<Offer> getOffers() {
        ensureInitialized();
        cachedOffers.clear();
        VillagerProfession prof = getProfession();

        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;

            boolean isProfessionalItem = isItemRelatedToProfession(s.getItem(), prof);

            // Если предмет НЕ по профессии - продаем всё.
            // Если предмет ПО профессии - продаем излишки (оставляем себе 2 шт).
            int keepAmount = isProfessionalItem ? 2 : 0;

            if (s.getCount() > keepAmount) {
                int price = (int) Math.max(1, PriceCalculator.getRawPrice(s.getItem()));
                // Цена продажи = 80% от рыночной
                cachedOffers.add(new Offer(new ItemStack(s.getItem(), s.getCount() - keepAmount), (int)(price * 0.8)));
            }
        }
        return cachedOffers;
    }

    private boolean isItemRelatedToProfession(Item item, VillagerProfession prof) {
        if (prof == VillagerProfession.FARMER)
            return item == Items.WHEAT || item == Items.BREAD || item == Items.CARROT || item == Items.POTATO || item == Items.WHEAT_SEEDS;
        if (prof == VillagerProfession.TOOLSMITH || prof == VillagerProfession.WEAPONSMITH)
            return item == Items.IRON_INGOT || item == Items.COAL || item == Items.IRON_PICKAXE || item == Items.IRON_SWORD;
        if (prof == VillagerProfession.LIBRARIAN)
            return item == Items.BOOK || item == Items.PAPER || item == Items.FEATHER;
        return false;
    }

    public static class Demand {
        public final ItemStack stack;
        public final int maxPricePerItem;
        public Demand(ItemStack stack, int maxPricePerItem) {
            this.stack = stack;
            this.maxPricePerItem = maxPricePerItem;
        }
    }

    public static class Offer {
        public final ItemStack stack;
        public final int minPricePerItem;
        public Offer(ItemStack stack, int minPricePerItem) {
            this.stack = stack;
            this.minPricePerItem = minPricePerItem;
        }
    }

    // ==================== СЕРИАЛИЗАЦИЯ ====================
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Budget", budget);
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
        initialized = tag.getBoolean("Initialized");
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

    public void forceReinitialize() {
        this.initialized = false;
    }

    @Override
    public boolean wantsToBuy(ItemStack stack) {
        for (Demand demand : getDemands()) {
            if (ItemStack.isSameItemSameComponents(demand.stack, stack)) return true;
        }
        return false;
    }

    @Override
    public BlockPos getPosition() {
        return villager != null ? villager.blockPosition() : null;
    }
}