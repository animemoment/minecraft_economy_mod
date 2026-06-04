package com.economymod.entity;

import com.economymod.EconomyMod;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.economy.IEconomicActor;
import com.economymod.economy.PriceCalculator;
import com.economymod.entity.ai.TravelToVillageGoal;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.ClientboundOwnerInventorySyncPacket;
import com.economymod.world.VillageNetworkData;
import com.economymod.config.EconomyConfig;
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
    private double budget = 500.0; // Скрыто в private для гарантированной потокобезопасности
    private BlockPos currentBazaar;
    public long lastTravelTime = 0;
    private long lastTradeTick = 0;

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
        }
    }

    public void arriveAtVillage(BlockPos bazaarPos) {
        if (bazaarPos == null) return;
        this.currentBazaar = bazaarPos;
        if (PriceCalculator.isPriceTableReady()) tradeWithVillagers();
    }

    public synchronized boolean evaluateOffer(ItemStack stack, double playerPrice, boolean isPlayerBuying) {
        if (stack.isEmpty() || playerPrice <= 0) return false;
        VillageNetworkData.VillageInfo info = null;
        if (level() instanceof ServerLevel serverLevel && currentBazaar != null) {
            info = VillageNetworkData.get(serverLevel).getVillageInfo(currentBazaar);
        }
        double fairPrice = PriceCalculator.getBuyPrice(stack, info);
        if (isPlayerBuying) return playerPrice >= (fairPrice * 0.95);
        else return playerPrice <= (fairPrice * 1.05);
    }

    public synchronized void processCustomTransaction(ServerPlayer player, ItemStack stack, double confirmedPrice, boolean isPlayerBuying) {
        if (isPlayerBuying) {
            if (player.containerMenu instanceof EconomyTradeMenu menu) {
                var playerActor = menu.getPlayerActor();
                if (playerActor.getBalance() >= confirmedPrice) {
                    playerActor.setBalance(playerActor.getBalance() - confirmedPrice);
                    this.budget += confirmedPrice;
                    if (!player.getInventory().add(stack.copy())) player.drop(stack.copy(), false);
                    stack.setCount(0);
                }
            }
        } else {
            if (this.budget >= confirmedPrice) {
                if (player.containerMenu instanceof EconomyTradeMenu menu) {
                    var playerActor = menu.getPlayerActor();
                    this.budget -= confirmedPrice;
                    playerActor.setBalance(playerActor.getBalance() + confirmedPrice);
                    this.inventory.addItem(stack.copy());
                    stack.setCount(0);
                }
            }
        }
        syncInventoryToClients();
    }

    @Override public boolean wantsToBuy(ItemStack stack) { return stack.is(Items.BREAD) || stack.is(Items.IRON_INGOT); }
    @Override public Set<Item> getWantedItems() { return Set.of(Items.BREAD, Items.IRON_INGOT); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, 0.35D).add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.5D));
        this.goalSelector.addGoal(2, new TravelToVillageGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.35D));
    }

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
            p.openMenu(this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new EconomyTradeMenu(id, inv, this); }
    @Override public SimpleContainer getInventory() { return inventory; }

    // Синхронизированный потокобезопасный доступ к деньгам торговца
    @Override public synchronized double getBalance() { return budget; }
    @Override public synchronized void setBalance(double balance) { this.budget = balance; }

    @Override public Component getDisplayName() { return this.getCustomName() != null ? this.getCustomName() : Component.literal("Trader"); }
    @Override public String getActorDisplayName() { return getDisplayName().getString(); }
    @Override public BlockPos getPosition() { return currentBazaar; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", inventory.createTag(this.registryAccess()));
        tag.putDouble("Budget", budget);
        if (currentBazaar != null) tag.putLong("CurrentBazaar", currentBazaar.asLong());
        tag.putLong("LastTravelTime", lastTravelTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Inventory")) inventory.fromTag(tag.getList("Inventory", 10), this.registryAccess());
        if (tag.contains("Budget")) budget = tag.getDouble("Budget");
        if (tag.contains("CurrentBazaar")) currentBazaar = BlockPos.of(tag.getLong("CurrentBazaar"));
        if (tag.contains("LastTravelTime")) lastTravelTime = tag.getLong("LastTravelTime");
    }

    private void updateCurrentBazaar() {
        if (this.level() instanceof ServerLevel serverLevel) {
            currentBazaar = serverLevel.getPoiManager()
                    .findClosest(poiType -> poiType.is(PoiTypes.MEETING), this.blockPosition(), 48, PoiManager.Occupancy.ANY)
                    .orElse(null);
        }
    }

    public synchronized void tradeWithVillagers() {
        if (!(level() instanceof ServerLevel serverLevel) || currentBazaar == null) return;
        VillageNetworkData data = VillageNetworkData.get(serverLevel);
        VillageNetworkData.VillageInfo info = data.getVillageInfo(currentBazaar);
        if (info == null) return;

        List<Villager> villagers = level().getEntitiesOfClass(Villager.class, new AABB(currentBazaar).inflate(48), LivingEntity::isAlive);
        if (villagers.isEmpty()) return;

        boolean tradeHappened = false;
        int tradesCount = 0;
        int maxTrades = EconomyConfig.MAX_CONCURRENT_TRADES.get(); // Считываем лимит из валидируемого конфига

        for (Villager villager : villagers) {
            if (tradesCount >= maxTrades) break; // Защитный лимит на количество сделок за один тик

            var att = villager.getData(com.economymod.registry.ModAttachments.VILLAGER.get());
            if (att == null || att.getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE) continue;

            for (com.economymod.attachment.VillagerAttachment.Demand d : att.getDemands()) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack sellerStack = inventory.getItem(i);
                    if (!sellerStack.isEmpty() && ItemStack.isSameItemSameComponents(sellerStack, d.stack)) {
                        int amount = Math.min(sellerStack.getCount(), d.stack.getCount());
                        double pricePerItem = PriceCalculator.getBuyPrice(new ItemStack(d.stack.getItem()), info);

                        // Применение налога из конфига при расчете сделки (TAX_RATE)
                        double taxMultiplier = 1.0 + EconomyConfig.TAX_RATE.get();
                        double priceWithTax = pricePerItem * taxMultiplier;

                        if (priceWithTax <= d.maxPricePerItem) {
                            double total = priceWithTax * amount;
                            if (att.getBalance() >= total) {
                                sellerStack.shrink(amount);
                                att.setBalance(att.getBalance() - total);
                                this.budget += total;
                                att.getInventory().addItem(new ItemStack(d.stack.getItem(), amount));
                                info.recordTrade(d.stack.getItem(), amount);
                                tradeHappened = true;
                                tradesCount++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (tradeHappened) { data.setDirty(); syncInventoryToClients(); }
    }

    @Override public net.minecraft.world.entity.LivingEntity getEntity() { return this; }

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