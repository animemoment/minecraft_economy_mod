package com.economymod.gui.menu;

import com.economymod.EconomyMod;
import com.economymod.economy.IEconomicActor;
import com.economymod.economy.PlayerActor;
import com.economymod.economy.PriceCalculator;
import com.economymod.network.ClientboundBalanceSyncPacket;
import com.economymod.network.ClientboundFullPriceTablePacket;
import com.economymod.network.ClientboundOwnerInventorySyncPacket;
import com.economymod.network.ClientboundPriceUpdatePacket;
import com.economymod.network.ClientboundVillagerStatsPacket;
import com.economymod.registry.ModMenus;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class EconomyTradeMenu extends AbstractContainerMenu {

    public static final int OWNER_SLOTS = 36;
    public static final int PLAYER_INV_START = 36;
    public static final int HOTBAR_START = 63;
    public static final int HOTBAR_END = 71;
    public static final int BUY_START = 72;
    public static final int BUY_END = 80;
    public static final int SELL_START = 81;
    public static final int SELL_END = 89;

    private final IEconomicActor ownerActor;
    private final PlayerActor playerActor;
    private final SimpleContainer ownerInventory;
    private final double[] prices;
    private double clientBalance;
    private double clientBudget;
    private final VillageNetworkData.VillageInfo villageInfo;
    public final SimpleContainer buyContainer = new SimpleContainer(9);
    public final SimpleContainer sellContainer = new SimpleContainer(9);

    // Массивы для хранения предложенных игроком цен (0.0 по умолчанию означает расчет по рынку)
    private final double[] buyCustomPrices = new double[9];
    private final double[] sellCustomPrices = new double[9];

    public void setBuyCustomPrice(int slotIndex, double price) {
        if (slotIndex >= 0 && slotIndex < 9) {
            this.buyCustomPrices[slotIndex] = Math.max(0.0, price);
        }
    }

    public void setSellCustomPrice(int slotIndex, double price) {
        if (slotIndex >= 0 && slotIndex < 9) {
            this.sellCustomPrices[slotIndex] = Math.max(0.0, price);
        }
    }

    public double getBuyCustomPrice(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < 9) {
            return this.buyCustomPrices[slotIndex];
        }
        return 0.0;
    }

    public double getSellCustomPrice(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < 9) {
            return this.sellCustomPrices[slotIndex];
        }
        return 0.0;
    }

    public EconomyTradeMenu(int id, Inventory playerInv) {
        this(id, playerInv, (IEconomicActor) null);
    }

    public EconomyTradeMenu(int id, Inventory playerInv, IEconomicActor owner) {
        super(ModMenus.ECONOMY_TRADE_MENU.get(), id);
        if (owner instanceof com.economymod.attachment.VillagerAttachment va) {
            va.fillInitialLoot();
        }
        this.ownerActor = owner;
        this.playerActor = new PlayerActor(playerInv.player);
        this.ownerInventory = owner != null ? owner.getInventory() : new SimpleContainer(36);
        this.prices = new double[OWNER_SLOTS];

        if (playerInv.player.level() instanceof ServerLevel sl && owner != null && owner.getPosition() != null) {
            this.villageInfo = VillageNetworkData.get(sl).getVillageInfo(owner.getPosition());
        } else {
            this.villageInfo = null;
        }

        // Слоты владельца (0..35)
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 9; c++) {
                int s = c + r * 9;
                this.addSlot(new Slot(ownerInventory, s, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack st) { return false; }
                });
            }

        // Инвентарь игрока (36..62)
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18));

        // Хотбар (63..71)
        for (int c = 0; c < 9; c++)
            this.addSlot(new Slot(playerInv, c, 8 + c * 18, 161));

        // BuyContainer (72..80)
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                int slot = c + r * 3;
                this.addSlot(new Slot(buyContainer, slot, 196 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }

        // SellContainer (81..89)
        // SellContainer (81..89)
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++) {
                int slot = c + r * 3;
                this.addSlot(new Slot(sellContainer, slot, 196 + c * 18, 93 + r * 18) {
                    @Override public boolean mayPlace(ItemStack stack) {
                        return true;
                    }
                });
            }

        refreshPrices();

        if (owner != null && playerInv.player instanceof ServerPlayer sp) {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < OWNER_SLOTS; i++) items.add(ownerInventory.getItem(i).copy());
            double budget = owner.getBalance();
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(items, budget));
            double playerBalance = playerActor.getBalance();
            PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(playerBalance, budget));
            this.clientBalance = playerBalance;
            this.clientBudget = budget;

            Map<Integer, Double> pricesMap = new HashMap<>();
            for (int i = 0; i < OWNER_SLOTS; i++) {
                if (!ownerInventory.getItem(i).isEmpty()) {
                    pricesMap.put(i, prices[i]);
                }
            }
            PacketDistributor.sendToPlayer(sp, new ClientboundPriceUpdatePacket(pricesMap));

            if (PriceCalculator.getPriceTable() != null) {
                Map<String, Double> dynamicPriceTable = new HashMap<>();
                PriceCalculator.getPriceTable().getAllPrices().forEach((item, price) -> {
                    double dynamicBase = PriceCalculator.calculateDynamicPrice(new ItemStack(item), villageInfo);
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    dynamicPriceTable.put(key.toString(), dynamicBase);
                });
                PacketDistributor.sendToPlayer(sp, new ClientboundFullPriceTablePacket(dynamicPriceTable));
            }
        }
    }

    public void refreshPrices() {
        for (int i = 0; i < OWNER_SLOTS; i++) {
            ItemStack st = ownerInventory.getItem(i);
            if (!st.isEmpty()) {
                prices[i] = PriceCalculator.getBuyPrice(st, villageInfo);
            } else {
                prices[i] = 0;
            }
        }
    }

    public double getPrice(int slot) { return prices[slot]; }

    public double getTotalBuyCost() {
        double total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = buyContainer.getItem(i);
            if (!stack.isEmpty()) {
                double customPrice = getBuyCustomPrice(i);
                double finalPricePerItem = (customPrice == 0.0) ? PriceCalculator.getBuyPrice(stack, villageInfo) : customPrice;
                total += finalPricePerItem * stack.getCount();
            }
        }
        return total;
    }

    public double getTotalSellValue() {
        double total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = sellContainer.getItem(i);
            if (!stack.isEmpty()) {
                double customPrice = getSellCustomPrice(i);
                double finalPricePerItem = (customPrice == 0.0) ? PriceCalculator.getSellPrice(stack, villageInfo) : customPrice;
                total += finalPricePerItem * stack.getCount();
            }
        }
        return total;
    }

    public void updateFromServer(List<ItemStack> inventory, double budget) {
        for (int i = 0; i < Math.min(inventory.size(), OWNER_SLOTS); i++)
            ownerInventory.setItem(i, inventory.get(i));
        this.clientBudget = budget;
    }

    public void updatePrices(Map<Integer, Double> serverPrices) {
        serverPrices.forEach((slot, price) -> {
            if (slot >= 0 && slot < OWNER_SLOTS) {
                prices[slot] = price;
            }
        });
    }

    public double getClientBalance() { return clientBalance; }
    public double getClientBudget() { return clientBudget; }
    public void setClientBalance(double balance) { this.clientBalance = balance; }
    public void setClientBudget(double budget) { this.clientBudget = budget; }
    public IEconomicActor getOwnerActor() { return ownerActor; }
    public PlayerActor getPlayerActor() { return playerActor; }

    public void clearBaskets(Player player) {
        for (int i = 0; i < 9; i++) buyContainer.setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = sellContainer.getItem(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                sellContainer.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = sellContainer.getItem(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) player.drop(stack, false);
                sellContainer.setItem(i, ItemStack.EMPTY);
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = buyContainer.getItem(i);
            if (!stack.isEmpty() && ownerActor != null) {
                ItemStack remainder = ownerActor.getInventory().addItem(stack.copy());
                if (!remainder.isEmpty()) {
                    if (ownerActor.getPosition() != null && player.level() instanceof ServerLevel) {
                        player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                                player.level(),
                                ownerActor.getPosition().getX(),
                                ownerActor.getPosition().getY(),
                                ownerActor.getPosition().getZ(),
                                remainder
                        ));
                    }
                }
                buyContainer.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();
        if (index < OWNER_SLOTS) return ItemStack.EMPTY;
        if (index >= BUY_START && index <= BUY_END) {
            slot.set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (index >= SELL_START && index <= SELL_END) {
            if (!this.moveItemStackTo(stackInSlot, PLAYER_INV_START, HOTBAR_END + 1, false))
                return ItemStack.EMPTY;
            return original;
        }
        if ((index >= PLAYER_INV_START && index <= PLAYER_INV_START + 26) || (index >= HOTBAR_START && index <= HOTBAR_END)) {
            if (!this.moveItemStackTo(stackInSlot, SELL_START, SELL_END + 1, false))
                return ItemStack.EMPTY;
            return original;
        }
        return ItemStack.EMPTY;
    }

    private final java.util.concurrent.atomic.AtomicBoolean transactionLock = new java.util.concurrent.atomic.AtomicBoolean(false);

    public boolean tryLockTransaction() {
        return transactionLock.compareAndSet(false, true);
    }

    public void unlockTransaction() {
        transactionLock.set(false);
    }

    @Override public boolean stillValid(Player p) { return ownerActor != null; }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        int index = slot.index;
        if (index < OWNER_SLOTS || (index >= BUY_START && index <= BUY_END)) return false;
        return super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * ИСПРАВЛЕНО: broadcastChanges() на сервере считывает ИИ-параметры жителя и шлет пакет статистики на клиент
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (ownerActor != null && playerActor.getEntity() instanceof ServerPlayer sp) {
            float health = 1.0f;
            float hunger = 1.0f;
            float fatigue = 0.0f;
            float distress = 0.0f;
            String emotion = "Спокоен";

            LivingEntity entity = ownerActor.getEntity();
            if (entity != null) {
                health = entity.getHealth() / entity.getMaxHealth();
            }

            if (ownerActor instanceof com.economymod.attachment.VillagerAttachment va) {
                hunger = (float) va.getHunger() / 20.0f;
            }

            if (entity instanceof net.minecraft.world.entity.npc.Villager v) {
                com.economymod.creatures.VillagerBrainWrapper brain = com.economymod.creatures.VillagerBrainWrapper.get(v);
                if (brain != null) {
                    fatigue = brain.getSensorValue("Fatigue");
                    distress = brain.getLearningSystem().calculateDistress(health, 1.0f - hunger, fatigue);
                    emotion = brain.getStrongestDesire();
                    if (emotion == null) {
                        emotion = "Спокоен";
                    } else {
                        emotion = formatEmotionName(emotion);
                    }
                }
            }

            // Отсылаем пакет статистики
            PacketDistributor.sendToPlayer(sp,
                    new ClientboundVillagerStatsPacket(health, hunger, fatigue, distress, emotion));
        }
    }

    private String formatEmotionName(String raw) {
        switch (raw) {
            case "EAT": return "Проголодался";
            case "SLEEP": return "Хочет спать";
            case "TRADE": return "Готов торговать";
            case "RUN_AWAY": return "Напуган!";
            case "EXPLORE": return "Любопытен";
            case "SOCIALIZE": return "Общителен";
            default: return "Спокоен";
        }
    }
}