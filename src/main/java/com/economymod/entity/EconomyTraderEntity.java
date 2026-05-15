package com.economymod.entity;

import com.economymod.EconomyMod;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.economy.IEconomicActor;
import com.economymod.economy.PriceCalculator;
import com.economymod.entity.ai.TravelToVillageGoal;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.ClientboundOwnerInventorySyncPacket;
import com.economymod.network.ClientboundPriceUpdatePacket;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EconomyTraderEntity extends AbstractVillager implements MenuProvider, IEconomicActor {

    public final SimpleContainer inventory = new SimpleContainer(36);
    public long budget = 500L;
    private BlockPos currentBazaar;
    public long lastTravelTime = 0;
    private boolean pricesNeedRecalc = true;

    public EconomyTraderEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal("Trader"));
        this.setCustomNameVisible(true);
        if (!level.isClientSide) {
            this.inventory.setItem(0, new ItemStack(Items.BREAD, 5));
            this.inventory.setItem(1, new ItemStack(Items.COAL, 3));
            this.inventory.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
            this.inventory.setItem(3, new ItemStack(Items.CHICKEN, 4));
            updateCurrentBazaar();
            if (currentBazaar != null) tradeWithVillage();
        }
    }

    @Override
    public boolean wantsToBuy(ItemStack stack) {
        return stack.is(Items.BREAD) || stack.is(Items.IRON_INGOT);
    }

    @Override
    public Set<Item> getWantedItems() {
        return Set.of(Items.BREAD, Items.IRON_INGOT);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.5D));
        this.goalSelector.addGoal(2, new TravelToVillageGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.35D));
    }

    // Обязательные методы AbstractVillager
    @Override protected void updateTrades() {}
    @Override protected void rewardTradeXp(net.minecraft.world.item.trading.MerchantOffer o) {}
    @Nullable @Override public AgeableMob getBreedOffspring(ServerLevel l, AgeableMob o) { return null; }
    @Override public MerchantOffers getOffers() { return new MerchantOffers(); }
    @Override public void overrideOffers(MerchantOffers offers) {}
    @Override public void overrideXp(int xp) {}
    @Override public int getVillagerXp() { return 0; }

    @Override
    public InteractionResult mobInteract(Player p, InteractionHand h) {
        if (!this.level().isClientSide) {
            if (pricesNeedRecalc && PriceCalculator.isPriceTableReady()) {
                pricesNeedRecalc = false;
                syncInventoryToClients();
                EconomyMod.LOGGER.info("Prices recalculated for trader {} on GUI open", this.getId());
            }
            p.openMenu(this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Nullable @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new EconomyTradeMenu(id, inv, this);
    }

    @Override public SimpleContainer getInventory() { return inventory; }
    @Override public long getBalance() { return budget; }
    @Override public void setBalance(long balance) { this.budget = balance; }

    @Override
    public Component getDisplayName() {
        return this.getCustomName() != null ? this.getCustomName() : Component.literal("Trader");
    }

    @Override
    public String getActorDisplayName() {
        return getDisplayName().getString();
    }

    @Override
    public BlockPos getPosition() {
        return getCurrentBazaar();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", inventory.createTag(this.registryAccess()));
        tag.putLong("Budget", budget);
        if (currentBazaar != null) tag.putLong("CurrentBazaar", currentBazaar.asLong());
        tag.putLong("LastTravelTime", lastTravelTime);
        tag.putBoolean("PricesNeedRecalc", pricesNeedRecalc);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Inventory")) inventory.fromTag(tag.getList("Inventory", 10), this.registryAccess());
        if (tag.contains("Budget")) budget = tag.getLong("Budget");
        if (tag.contains("CurrentBazaar")) currentBazaar = BlockPos.of(tag.getLong("CurrentBazaar"));
        if (tag.contains("LastTravelTime")) lastTravelTime = tag.getLong("LastTravelTime");
        if (tag.contains("PricesNeedRecalc")) pricesNeedRecalc = tag.getBoolean("PricesNeedRecalc");
        else pricesNeedRecalc = true;
    }

    private void updateCurrentBazaar() {
        if (this.level() instanceof ServerLevel serverLevel) {
            currentBazaar = serverLevel.getPoiManager()
                    .findClosest(poiType -> poiType.is(PoiTypes.MEETING), this.blockPosition(), 48, PoiManager.Occupancy.ANY)
                    .orElse(null);
        }
    }

    public void arriveAtVillage(BlockPos bazaarPos) {
        this.currentBazaar = bazaarPos;
        updateCurrentBazaar();
        if (PriceCalculator.isPriceTableReady()) {
            tradeWithVillage();
        } else {
            EconomyMod.LOGGER.debug("Delaying trade – PriceTable not ready");
        }
    }

    public void tradeWithVillage() {
        if (!PriceCalculator.isPriceTableReady()) {
            EconomyMod.LOGGER.debug("Skipping trade with villagers: PriceTable not ready");
            return;
        }
        if (currentBazaar == null) return;
        tradeWithVillagers();
    }

    public void tradeWithVillagers() {
        if (!(level() instanceof ServerLevel serverLevel) || currentBazaar == null) return;
        VillageNetworkData data = VillageNetworkData.get(serverLevel);
        VillageNetworkData.VillageInfo info = data.getVillageInfo(currentBazaar);
        if (info == null) return;

        List<Villager> villagers = level().getEntitiesOfClass(Villager.class,
                new AABB(currentBazaar).inflate(64), villager -> villager.isAlive());
        EconomyMod.LOGGER.info("=== Trader {} trading with {} villagers at {} ===", this.getId(), villagers.size(), currentBazaar);
        if (villagers.isEmpty()) return;

        Map<Item, Integer> totalDemand = new HashMap<>();
        Map<Item, Integer> totalSupply = new HashMap<>();
        int population = villagers.size();

        for (Villager villager : villagers) {
            VillagerAttachment attachment = villager.getData(ModAttachments.VILLAGER.get());
            attachment.forceReinitialize();
            if (attachment.getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE) continue;

            EconomyMod.LOGGER.info("Processing villager {} ({}), budget: {}, inventory items: {}",
                    villager.getId(), attachment.getProfession(), attachment.getBalance(), countNonEmptySlots(attachment.getInventory()));

            List<VillagerAttachment.Demand> demands = attachment.getDemands();
            for (VillagerAttachment.Demand d : demands) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack sellerStack = inventory.getItem(i);
                    if (!sellerStack.isEmpty() && ItemStack.isSameItemSameComponents(sellerStack, d.stack)) {
                        int amount = Math.min(sellerStack.getCount(), d.stack.getCount());
                        long pricePerItem = PriceCalculator.getBuyPrice(new ItemStack(d.stack.getItem()), info);
                        if (pricePerItem <= d.maxPricePerItem) {
                            long totalPrice = pricePerItem * amount;
                            if (attachment.getBalance() >= totalPrice) {
                                sellerStack.shrink(amount);
                                attachment.setBalance(attachment.getBalance() - totalPrice);
                                this.budget += totalPrice;
                                ItemStack given = d.stack.copy();
                                given.setCount(amount);
                                for (int j = 0; j < VillagerAttachment.INVENTORY_SIZE; j++) {
                                    ItemStack vs = attachment.getInventory().getItem(j);
                                    if (vs.isEmpty()) { attachment.getInventory().setItem(j, given); break; }
                                    else if (ItemStack.isSameItemSameComponents(vs, given) && vs.getCount() < 64) {
                                        int add = Math.min(64 - vs.getCount(), given.getCount());
                                        vs.grow(add); given.shrink(add);
                                        if (given.isEmpty()) break;
                                    }
                                }
                                EconomyMod.LOGGER.info(" SOLD {} x{} to villager {} for {} coins", amount, d.stack.getItem(), villager.getId(), totalPrice);
                                totalDemand.merge(d.stack.getItem(), amount, Integer::sum);
                                break;
                            }
                        }
                    }
                }
            }

            List<VillagerAttachment.Offer> offers = attachment.getOffers();
            for (VillagerAttachment.Offer o : offers) totalSupply.merge(o.stack.getItem(), o.stack.getCount(), Integer::sum);
        }

        for (Villager villager : villagers) {
            VillagerAttachment attachment = villager.getData(ModAttachments.VILLAGER.get());
            if (attachment.getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE) continue;
            List<VillagerAttachment.Offer> offers = attachment.getOffers();
            for (VillagerAttachment.Offer o : offers) {
                long bestPrice = 0;
                for (VillageNetworkData.VillageInfo other : data.getAllVillages()) {
                    if (other == info) continue;
                    long priceThere = PriceCalculator.getBuyPrice(o.stack, other);
                    if (priceThere > bestPrice) bestPrice = priceThere;
                }
                long profit = bestPrice - o.minPricePerItem;
                if (profit > o.minPricePerItem * 0.2 && this.budget >= o.minPricePerItem) {
                    int maxAfford = (int) (this.budget / o.minPricePerItem);
                    int amountToBuy = Math.min(o.stack.getCount(), maxAfford);
                    int freeSlots = 0;
                    for (int i = 0; i < 36; i++) {
                        ItemStack s = inventory.getItem(i);
                        if (s.isEmpty()) freeSlots += 64;
                        else if (ItemStack.isSameItemSameComponents(s, o.stack)) freeSlots += (64 - s.getCount());
                    }
                    amountToBuy = Math.min(amountToBuy, freeSlots);
                    if (amountToBuy > 0) {
                        int remaining = amountToBuy;
                        for (int i = 0; i < VillagerAttachment.INVENTORY_SIZE && remaining > 0; i++) {
                            ItemStack vs = attachment.getInventory().getItem(i);
                            if (ItemStack.isSameItemSameComponents(vs, o.stack)) {
                                int take = Math.min(remaining, vs.getCount());
                                vs.shrink(take); remaining -= take;
                            }
                        }
                        ItemStack bought = o.stack.copy();
                        bought.setCount(amountToBuy);
                        for (int i = 0; i < 36; i++) {
                            ItemStack s = inventory.getItem(i);
                            if (s.isEmpty()) { inventory.setItem(i, bought); break; }
                            else if (ItemStack.isSameItemSameComponents(s, bought)) {
                                int add = Math.min(64 - s.getCount(), bought.getCount());
                                s.grow(add); bought.shrink(add);
                                if (bought.isEmpty()) break;
                            }
                        }
                        long totalCost = (long) amountToBuy * o.minPricePerItem;
                        attachment.setBalance(attachment.getBalance() + totalCost);
                        this.budget -= totalCost;
                        EconomyMod.LOGGER.info(" BOUGHT {} x{} from villager {} for {} coins (profit {})", amountToBuy, o.stack.getItem(), villager.getId(), totalCost, profit);
                    }
                }
            }
        }

        info.recalcFactors(totalDemand, totalSupply, population);
        info.setInflationRate(info.getInflationRate() + 0.001);
        data.setDirty();
        syncInventoryToClients();

        if (currentBazaar != null) {
            Map<Integer, Long> newPrices = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    long price = PriceCalculator.calculateDynamicPrice(stack, info);
                    newPrices.put(i, price);
                }
            }
            ClientboundPriceUpdatePacket pricePacket = new ClientboundPriceUpdatePacket(newPrices);
            for (Player player : level().players()) {
                if (player instanceof ServerPlayer sp && sp.containerMenu instanceof EconomyTradeMenu menu && menu.getOwnerActor() == this) {
                    PacketDistributor.sendToPlayer(sp, pricePacket);
                }
            }
            EconomyMod.LOGGER.info("Sent dynamic price update to viewers: {}", newPrices);
        }

        EconomyMod.LOGGER.info("=== Trade finished. Trader budget now: {} ===", this.budget);
    }

    private int countNonEmptySlots(SimpleContainer inv) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) count++;
        return count;
    }

    public void syncInventoryToClients() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) items.add(inventory.getItem(i).copy());
        for (Player player : level().players()) {
            if (player instanceof ServerPlayer sp && sp.containerMenu instanceof EconomyTradeMenu menu && menu.getOwnerActor() == this) {
                PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(items, budget));
            }
        }
    }

    public BlockPos getCurrentBazaar() { return currentBazaar; }
}