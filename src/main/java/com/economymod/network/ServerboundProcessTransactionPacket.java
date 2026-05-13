package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.economy.PriceCalculator;
import com.economymod.economy.TransactionService;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.world.VillageNetworkData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record ServerboundProcessTransactionPacket() implements CustomPacketPayload {
    public static final Type<ServerboundProcessTransactionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "process_transaction"));

    public static final StreamCodec<ByteBuf, ServerboundProcessTransactionPacket> STREAM_CODEC =
            StreamCodec.unit(new ServerboundProcessTransactionPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundProcessTransactionPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof EconomyTradeMenu menu)) return;

            var owner = menu.getOwnerActor();
            var player = menu.getPlayerActor();
            if (owner == null || player == null) return;

            SimpleContainer buyContainer = menu.buyContainer;
            SimpleContainer sellContainer = menu.sellContainer;

            // === ПОКУПКА ===
            for (int i = 0; i < 9; i++) {
                ItemStack stack = buyContainer.getItem(i);
                if (!stack.isEmpty()) {
                    long pricePerItem = PriceCalculator.getBuyPrice(stack, null); // info пока null, обновим позже
                    int amount = stack.getCount();
                    long totalCost = pricePerItem * amount;

                    if (player.getBalance() < totalCost) continue;
                    if (!TransactionService.canRemoveItems(owner, stack, amount)) continue;
                    TransactionService.removeItems(owner, stack.copy(), amount);
                    if (!TransactionService.canAddItems(player, stack, amount)) {
                        TransactionService.addItems(owner, stack.copy(), amount);
                        continue;
                    }
                    buyContainer.setItem(i, ItemStack.EMPTY);
                    if (!TransactionService.addItemsSafe(player, stack.copy(), amount)) {
                        TransactionService.addItems(owner, stack.copy(), amount);
                        buyContainer.setItem(i, stack);
                        continue;
                    }
                    player.setBalance(player.getBalance() - totalCost);
                    owner.setBalance(owner.getBalance() + totalCost);
                }
            }

            // === ПРОДАЖА ===
            for (int i = 0; i < 9; i++) {
                ItemStack stack = sellContainer.getItem(i);
                if (!stack.isEmpty()) {
                    long pricePerItem = PriceCalculator.getSellPrice(stack, null);
                    int amount = stack.getCount();
                    long totalPrice = pricePerItem * amount;

                    if (owner.getBalance() < totalPrice) continue;
                    if (!TransactionService.canAddItems(owner, stack, amount)) continue;
                    sellContainer.setItem(i, ItemStack.EMPTY);
                    if (!TransactionService.addItemsSafe(owner, stack.copy(), amount)) {
                        sellContainer.setItem(i, stack);
                        continue;
                    }
                    owner.setBalance(owner.getBalance() - totalPrice);
                    player.setBalance(player.getBalance() + totalPrice);
                }
            }

            // === ОБНОВЛЕНИЕ ДИНАМИЧЕСКИХ ЦЕН ===
            BlockPos pos = owner.getPosition();
            if (pos != null && sp.serverLevel() != null) {
                VillageNetworkData data = VillageNetworkData.get(sp.serverLevel());
                VillageNetworkData.VillageInfo info = data.getVillageInfo(pos);
                if (info != null) {
                    // В будущем: info.recalcFactors(level) – пока пропускаем, используем существующие факторы
                    Map<Integer, Long> newPrices = new HashMap<>();
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = owner.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            long price = PriceCalculator.calculateDynamicPrice(stack, info);
                            newPrices.put(i, price);
                        }
                    }
                    ClientboundPriceUpdatePacket pricePacket = new ClientboundPriceUpdatePacket(newPrices);
                    PacketDistributor.sendToPlayer(sp, pricePacket);
                }
            }

            // === СИНХРОНИЗАЦИЯ ===
            long playerBal = player.getBalance();
            long ownerBal = owner.getBalance();
            PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(playerBal, ownerBal));

            List<ItemStack> items = new ArrayList<>();
            for (int j = 0; j < 36; j++) items.add(owner.getInventory().getItem(j).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(items, ownerBal));
        });
    }
}