package com.economymod.attachment;

import com.economymod.economy.IEconomicActor;
import com.economymod.economy.desire.Desire;
import com.economymod.economy.desire.DesireProcessor;
import com.economymod.economy.PriceCalculator;
import com.economymod.entity.ai.mining.BridgeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class VillagerAttachment implements IEconomicActor {
    public static final int INVENTORY_SIZE = 36;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final Villager villager;
    private double budget;
    private boolean wasLootGenerated = false;
    private BlockPos personalChestPos = null;
    private double hunger = 20.0; // Сытость жителя (максимум 20.0, сытый по умолчанию)

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

    public void transferVanillaToCustom() {
        if (villager == null) return;
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

    public double getHunger() {
        return this.hunger;
    }

    public void setHunger(double hunger) {
        this.hunger = Math.max(0.0, Math.min(20.0, hunger));
    }

    public void decreaseHunger(double amount) {
        this.setHunger(this.hunger - amount);
        if (this.hunger < 10.0) {
            eatFoodIfNeeded();
        }
    }

    public void tick() {
        if (villager == null || villager.level().isClientSide()) return;

        // КРИТИЧНЫЙ БЛОК: Сон жителя полностью священен! Во сне ИИ полностью выключен
        if (villager.isSleeping()) return;

        long gameTime = villager.level().getGameTime();

        // 1. Снижаем сытость раз в секунду
        if (gameTime % 20 == 0) {
            this.decreaseHunger(0.02);
        }

        // 2. Универсальный спасатель из ям любой формы (активен раз в секунду)
        if (gameTime % 20 == 0 && villager.onGround()) {
            BlockPos feetPos = villager.blockPosition();

            // Если житель признан полностью запертым в яме или стоит ногами в жидкости (воде/лаве)
            if (isTrappedInPit(villager.level(), feetPos)) {
                ItemStack blockStack = BridgeBuilder.getPlaceableBlock(this);
                if (!blockStack.isEmpty()) {
                    BlockPos ceilingPos = feetPos.above(2); // Проверяем свободное место над головой
                    if (villager.level().getBlockState(ceilingPos).isAir()) {
                        villager.getJumpControl().jump();

                        net.minecraft.world.level.block.Block b = net.minecraft.world.level.block.Block.byItem(blockStack.getItem());
                        villager.level().setBlockAndUpdate(feetPos, b.defaultBlockState());
                        blockStack.shrink(1);

                        villager.teleportTo(villager.getX(), feetPos.getY() + 1.0D, villager.getZ());

                        villager.level().playSound(null, feetPos, net.minecraft.sounds.SoundEvents.STONE_PLACE,
                                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

                        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА САМОСПАСЕНИЕ: Житель {} успешно выбрался из ловушки на {}, построив под собой столб!",
                                getActorDisplayName(), feetPos.toShortString());
                    }
                }
            }
        }
    }

    // ИСПРАВЛЕНО: Безупречный математический детектор ловушек. Исключает башни-потолки в шахтах и обрывах!
    private boolean isTrappedInPit(Level level, BlockPos feetPos) {
        // Если житель стоит ногами в воде или лаве — спасаемся немедленно!
        if (!level.getFluidState(feetPos).isEmpty()) {
            return true;
        }

        // ИСПРАВЛЕНО: Житель признается зажатым ТОЛЬКО если все 4 горизонтальные стороны вокруг ног И головы завалены сплошными стенами (колодец 1х1)
        BlockPos headPos = feetPos.above();

        boolean feetTrapped = isBlockSolidForWalking(level, feetPos.north()) &&
                isBlockSolidForWalking(level, feetPos.south()) &&
                isBlockSolidForWalking(level, feetPos.east()) &&
                isBlockSolidForWalking(level, feetPos.west());

        boolean headTrapped = isBlockSolidForWalking(level, headPos.north()) &&
                isBlockSolidForWalking(level, headPos.south()) &&
                isBlockSolidForWalking(level, headPos.east()) &&
                isBlockSolidForWalking(level, headPos.west());

        return feetTrapped && headTrapped;
    }

    private boolean isBlockSolidForWalking(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) ||
                state.is(Blocks.LADDER) || state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            return false;
        }
        return state.isSolid();
    }

    private boolean isBlockSolidForStanding(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolid() && !(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock);
    }

    private void eatFoodIfNeeded() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isFood(stack.getItem())) {
                stack.shrink(1);
                this.setHunger(this.hunger + 6.0);
                if (villager != null) {
                    villager.level().playSound(null, villager.blockPosition(),
                            net.minecraft.sounds.SoundEvents.GENERIC_EAT,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
                com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА: Житель {} поел и восстановил сытость до {}!",
                        getActorDisplayName(), this.hunger);
                break;
            }
        }
    }

    private boolean isFood(Item item) {
        return item == Items.BREAD || item == Items.POTATO || item == Items.CARROT || item == Items.COOKED_BEEF || item == Items.COOKED_CHICKEN;
    }

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

        int blockCountToKeep = 64;
        int torchCountToKeep = 16;

        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            if (item == Items.TORCH) {
                if (torchCountToKeep > 0) {
                    torchCountToKeep -= stack.getCount();
                    continue;
                }
            }

            net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item);
            if (block != Blocks.AIR && block.defaultBlockState().isSolid() &&
                    block != Blocks.CHEST && block != Blocks.CRAFTING_TABLE && block != Blocks.FURNACE) {
                if (blockCountToKeep > 0) {
                    blockCountToKeep -= stack.getCount();
                    continue;
                }
            }

            if (!isUseful(item, prof)) {
                trash.add(i);
            }
        }
        return trash;
    }

    private boolean isUseful(Item item, VillagerProfession prof) {
        if (item.getFoodProperties(item.getDefaultInstance(), villager) != null) return true;
        if (item == Items.IRON_INGOT || item == Items.COAL || item == Items.STICK ||
                item == Items.GOLD_NUGGET || item instanceof PickaxeItem || item == Items.TORCH) return true;

        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item);
        if (block != Blocks.AIR && block.defaultBlockState().isSolid() &&
                block != Blocks.CHEST && block != Blocks.CRAFTING_TABLE && block != Blocks.FURNACE) {
            return true;
        }

        if (prof == VillagerProfession.FARMER) return item == Items.WHEAT_SEEDS || item == Items.WHEAT || item == Items.BONE_MEAL;
        if (prof == VillagerProfession.TOOLSMITH || prof == VillagerProfession.ARMORER || prof == VillagerProfession.WEAPONSMITH) {
            return item == Items.RAW_IRON || item == Items.IRON_PICKAXE || item == Items.RAW_GOLD || item == Items.RAW_COPPER;
        }
        return false;
    }

    @Override public SimpleContainer getInventory() {
        transferVanillaToCustom();
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
        tag.putDouble("Hunger", hunger);
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

        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА СОХРАНЕНИЕ: Данные жителя [{}] успешно записаны на диск! Бюджет: {}⛀, Слотов заполнено: {}",
                getActorDisplayName(), budget, invList.size());

        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        budget = tag.getDouble("Budget");
        wasLootGenerated = tag.getBoolean("WasLootGenerated");
        hunger = tag.contains("Hunger") ? tag.getDouble("Hunger") : 20.0;
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

        com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА ЗАГРУЗКА: Данные жителя [{}] успешно считаны с диска! Бюджет: {}⛀, Предметов восстановлено: {}",
                getActorDisplayName(), budget, invList.size());
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