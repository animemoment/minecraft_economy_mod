package com.economymod.network;

import com.economymod.economy.PriceCalculator;
import com.economymod.economy.TransactionService;
import com.economymod.economy.PlayerActor;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

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

            // Покупка игроком
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.buyContainer.getItem(i);
                if (!stack.isEmpty()) {
                    long price = PriceCalculator.getBuyPrice(stack, null);
                    if (TransactionService.processTransaction(owner, player, stack.copy(), price, stack.getCount())) {
                        menu.buyContainer.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            // Продажа игроком
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.sellContainer.getItem(i);
                if (!stack.isEmpty()) {
                    long price = PriceCalculator.getSellPrice(stack, null);
                    if (TransactionService.processTransaction(player, owner, stack.copy(), price, stack.getCount())) {
                        menu.sellContainer.setItem(i, ItemStack.EMPTY);
                    }
                }
            }

            menu.refreshPrices();
            // Синхронизация
            long newBalance = player.getBalance();
            long ownerBudget = owner.getBalance();
            PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(newBalance, ownerBudget));

            List<ItemStack> ownerItems = new ArrayList<>();
            for (int i = 0; i < 36; i++) ownerItems.add(owner.getInventory().getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(ownerItems, ownerBudget));
        });
    }
}