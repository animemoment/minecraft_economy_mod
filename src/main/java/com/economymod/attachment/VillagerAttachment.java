package com.economymod.attachment;

import com.economymod.economy.IEconomicActor;
import com.economymod.economy.desire.Desire;
import com.economymod.economy.desire.DesireProcessor;
import com.economymod.economy.PriceCalculator;
import com.economymod.entity.ai.mining.BridgeBuilder;
import com.economymod.creatures.VillagerBrainWrapper;
import com.economymod.entity.ai.AsyncBrainStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

        // КРИТИЧЕСКИЙ БЛОК: Сон жителя полностью священен! Во сне ИИ полностью выключен
        if (villager.isSleeping()) return;

        long gameTime = villager.level().getGameTime();

        // 1. Снижаем сытость раз в секунду с учетом индивидуального метаболизма биохимии ИИ!
        if (gameTime % 20 == 0) {
            double metabolismRate = 1.0;
            VillagerBrainWrapper brainWrapper = VillagerBrainWrapper.get(villager);
            if (brainWrapper != null) {
                metabolismRate = brainWrapper.getGenome().metabolismRate;
            }
            this.decreaseHunger(0.02 * metabolismRate);
        }

        // Последствия критического голодания (физические дебаффы, слабость и урон)
        if (this.hunger <= 0.0) {
            // Накладываем Слабость и Замедление раз в 3 секунды (60 тиков)
            if (gameTime % 60 == 0) {
                villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                villager.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            }

            // Наносим постепенный урон от голода раз в 2 секунды (40 тиков)
            if (gameTime % 40 == 0) {
                villager.hurt(villager.level().damageSources().starve(), 1.0F);

                // Выбрасываем химическую панику (стресс) в синапсы ИИ жителя
                VillagerBrainWrapper brainWrapper = VillagerBrainWrapper.get(villager);
                if (brainWrapper != null) {
                    brainWrapper.modifyChemical("Cortisol", 0.15f);  // Сильный скачок стресса
                    brainWrapper.modifyChemical("Dopamine", -0.10f); // Падение настроения/апатия
                }
            }
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

    private boolean isTrappedInPit(Level level, BlockPos feetPos) {
        if (!level.getFluidState(feetPos).isEmpty()) {
            return true;
        }

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

                // Биохимическая подпитка при еде!
                VillagerBrainWrapper brainWrapper = VillagerBrainWrapper.get(villager);
                if (brainWrapper != null) {
                    brainWrapper.modifyChemical("Glucose", 0.35f);
                    brainWrapper.modifyChemical("Dopamine", 0.15f);
                }

                // ИСПРАВЛЕНО: Восстанавливаем жителю 4.0 HP (2 сердца) при приеме пищи!
                if (villager != null) {
                    villager.heal(4.0F);
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

    @Override public net.minecraft.world.entity.LivingEntity getEntity() { return villager; }

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
        // ИСПРАВЛЕНО: Убран 100-тиковый кулдаун кэша для идеальной живой синхронизации с текущим инвентарем!
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

            // ИСПРАВЛЕНО: Житель сохраняет неприкосновенный личный запас еды (keep = 6)
            int keep = 0;
            if (isFood(s.getItem())) {
                keep = 6; // Еду никогда не продаем ниже лимита сытости
            } else if (isProfessionalItem(s.getItem(), prof)) {
                keep = 4; // Резерв профессиональных материалов
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
        tag.putBoolean("WasLootGenerated", wasLootGenerated);
        tag.putDouble("Hunger", hunger);
        tag.putInt("MiningStartY", miningStartY);
        if (personalChestPos != null) tag.putLong("ChestPos", personalChestPos.asLong());

        // АСИНХРОННОЕ СОХРАНЕНИЕ: Создаем снимок и отправляем тяжелые данные ИИ в фоновый поток!
        VillagerBrainWrapper brainWrapper = VillagerBrainWrapper.get(villager);
        if (brainWrapper != null) {
            CompoundTag brainTag = new CompoundTag();
            brainTag.put("BiochemData", brainWrapper.getBiochemistry().save());
            brainTag.putFloat("Fatigue", brainWrapper.getFatigue());
            brainTag.put("LearningSystem", brainWrapper.getLearningSystem().save());

            AsyncBrainStorage.queueSave(villager.level(), villager.getUUID(), brainTag);
        }

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

        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        budget = tag.getDouble("Budget");
        wasLootGenerated = tag.getBoolean("WasLootGenerated");
        hunger = tag.contains("Hunger") ? tag.getDouble("Hunger") : 20.0;
        if (tag.contains("ChestPos")) personalChestPos = BlockPos.of(tag.getLong("ChestPos"));
        inventory.clearContent();
        miningStartY = tag.getInt("MiningStartY");

        // АСИНХРОННАЯ ЗАГРУЗКА: Считываем данные из файлового буфера AsyncBrainStorage
        CompoundTag brainTag = AsyncBrainStorage.load(villager.level(), villager.getUUID());
        if (brainTag != null) {
            VillagerBrainWrapper brainWrapper = VillagerBrainWrapper.getOrCreate(villager);
            if (brainTag.contains("BiochemData")) {
                brainWrapper.getBiochemistry().load(brainTag.getCompound("BiochemData"));
            }
            if (brainTag.contains("Fatigue")) {
                brainWrapper.setFatigue(brainTag.getFloat("Fatigue"));
            }
            if (brainTag.contains("LearningSystem")) {
                brainWrapper.getLearningSystem().load(brainTag.getCompound("LearningSystem"));
            }
        }

        ListTag invList = tag.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < invList.size(); i++) {
            CompoundTag slotTag = invList.getCompound(i);
            int slot = slotTag.getInt("Slot");
            if (slot >= 0 && slot < INVENTORY_SIZE) {
                inventory.setItem(slot, ItemStack.parse(provider, slotTag).orElse(ItemStack.EMPTY));
            }
        }
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
    @Override
    public boolean wantsToBuy(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // 1. Все жители всегда купят еду, если они голодны или её мало в рюкзаке
        if (isFood(stack.getItem())) {
            int currentFood = countItemsInInventory(stack.getItem());
            return currentFood < 12; // Покупаем еду, если в запасе меньше 12 единиц
        }

        // 2. Житель купит вещь, если она прямо сейчас находится в списке его желаний (getDemands())
        for (Demand demand : getDemands()) {
            if (ItemStack.isSameItemSameComponents(demand.stack, stack)) {
                return true;
            }
        }

        // 3. Профессиональный интерес (базовые инструменты и сырье для его профессии)
        VillagerProfession prof = getProfession();
        if (prof == VillagerProfession.FARMER) {
            return stack.is(Items.WHEAT_SEEDS) || stack.getItem() instanceof net.minecraft.world.item.HoeItem;
        } else if (prof == VillagerProfession.TOOLSMITH || prof == VillagerProfession.WEAPONSMITH || prof == VillagerProfession.ARMORER) {
            return stack.is(Items.IRON_INGOT) || stack.is(Items.COAL) || stack.is(Items.RAW_IRON);
        } else if (prof == VillagerProfession.FLETCHER) {
            return stack.is(Items.OAK_LOG) || stack.is(Items.ACACIA_LOG) || stack.is(Items.FLINT) || stack.is(Items.FEATHER);
        }

        return false; // Любой другой хлам житель покупать отказывается
    }

    // Вспомогательный метод подсчета предметов
    private int countItemsInInventory(Item item) {
        int count = 0;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).is(item)) {
                count += inventory.getItem(i).getCount();
            }
        }
        return count;
    }
    @Override public BlockPos getPosition() { return villager != null ? villager.blockPosition() : null; }
    public static class Demand { public final ItemStack stack; public final int maxPricePerItem; public Demand(ItemStack stack, int maxPricePerItem) { this.stack = stack; this.maxPricePerItem = maxPricePerItem; } }
    public static class Offer { public final ItemStack stack; public final int minPricePerItem; public Offer(ItemStack stack, int minPricePerItem) { this.stack = stack; this.minPricePerItem = minPricePerItem; } }

    private int miningStartY = -1;

    public int getMiningStartY() { return miningStartY; }
    public void setMiningStartY(int y) { this.miningStartY = y; }

    public void forceLootGeneration() {
        VillagerProfession prof = getProfession();
        if (prof != VillagerProfession.NONE && prof != VillagerProfession.NITWIT) {
            inventory.clearContent();
            generateLootTable(prof);
            wasLootGenerated = true;
        }
    }
}