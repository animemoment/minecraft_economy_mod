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

            ServerLevel level = sp.serverLevel();
            BlockPos pos = owner.getPosition();

            // Получаем данные деревни
            VillageNetworkData data = VillageNetworkData.get(level);
            VillageNetworkData.VillageInfo info = (pos != null) ? data.getVillageInfo(pos) : null;

            SimpleContainer buyContainer = menu.buyContainer;
            SimpleContainer sellContainer = menu.sellContainer;

            // === ПОКУПКА (Игрок покупает у жителя) ===
            for (int i = 0; i < 9; i++) {
                ItemStack stack = buyContainer.getItem(i);
                if (!stack.isEmpty()) {
                    long pricePerItem = PriceCalculator.getBuyPrice(stack, info);
                    int amount = stack.getCount();
                    long totalCost = pricePerItem * (long) amount;

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

                    // ЭКОНОМИКА: Записываем сделку
                    if (info != null) info.recordTrade(stack.getItem(), amount);
                }
            }

            // === ПРОДАЖА (Игрок продает жителю) ===
            for (int i = 0; i < 9; i++) {
                ItemStack stack = sellContainer.getItem(i);
                if (!stack.isEmpty()) {
                    long pricePerItem = PriceCalculator.getSellPrice(stack, info);
                    int amount = stack.getCount();
                    long totalPrice = pricePerItem * (long) amount;

                    if (owner.getBalance() < totalPrice) continue;
                    if (!TransactionService.canAddItems(owner, stack, amount)) continue;

                    sellContainer.setItem(i, ItemStack.EMPTY);
                    if (!TransactionService.addItemsSafe(owner, stack.copy(), amount)) {
                        sellContainer.setItem(i, stack);
                        continue;
                    }

                    owner.setBalance(owner.getBalance() - totalPrice);
                    player.setBalance(player.getBalance() + totalPrice);

                    // ЭКОНОМИКА: Записываем сделку
                    if (info != null) info.recordTrade(stack.getItem(), amount);
                }
            }

            // Помечаем данные как измененные для сохранения
            data.setDirty();

            // === СИНХРОНИЗАЦИЯ КОРЗИН ===
            List<ItemStack> buyItems = new ArrayList<>();
            for (int i = 0; i < 9; i++) buyItems.add(buyContainer.getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundBuyContainerSyncPacket(buyItems));

            List<ItemStack> sellItems = new ArrayList<>();
            for (int i = 0; i < 9; i++) sellItems.add(sellContainer.getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundSellContainerSyncPacket(sellItems));

            // === ПЕРЕСЧЁТ ДИНАМИЧЕСКИХ ЦЕН ===
            if (info != null) {
                info.recalcFactors(level);

                Map<Integer, Long> newPrices = new HashMap<>();
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = owner.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        long price = PriceCalculator.calculateDynamicPrice(stack, info);
                        newPrices.put(i, price);
                    }
                }
                PacketDistributor.sendToPlayer(sp, new ClientboundPriceUpdatePacket(newPrices));
                EconomyMod.LOGGER.info("Dynamic prices updated after transaction at {}", pos);
            }

            // === ФИНАЛЬНАЯ СИНХРОНИЗАЦИЯ ===
            PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(player.getBalance(), owner.getBalance()));

            List<ItemStack> items = new ArrayList<>();
            for (int j = 0; j < 36; j++) items.add(owner.getInventory().getItem(j).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(items, owner.getBalance()));
        });
    }
}