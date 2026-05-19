package com.economymod.network;

import com.economymod.economy.PriceCalculator;
import com.economymod.economy.TransactionService;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.world.VillageNetworkData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundProcessTransactionPacket() implements CustomPacketPayload {
    public static final Type<ServerboundProcessTransactionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "process_transaction"));
    public static final StreamCodec<ByteBuf, ServerboundProcessTransactionPacket> STREAM_CODEC = StreamCodec.unit(new ServerboundProcessTransactionPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundProcessTransactionPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !(sp.containerMenu instanceof EconomyTradeMenu menu)) return;
            var owner = menu.getOwnerActor();
            var player = menu.getPlayerActor();
            if (owner == null || player == null) return;

            VillageNetworkData.VillageInfo info = VillageNetworkData.get(sp.serverLevel()).getVillageInfo(owner.getPosition());
            boolean tradeHappened = false;

            for (int i = 0; i < 9; i++) {
                ItemStack s = menu.buyContainer.getItem(i);
                if (s.isEmpty()) continue;
                // ИСПРАВЛЕНО: double
                double cost = PriceCalculator.getBuyPrice(s, info) * s.getCount();
                if (player.getBalance() >= cost && TransactionService.addItemsSafe(player, s.copy(), s.getCount())) {
                    player.setBalance(player.getBalance() - cost);
                    owner.setBalance(owner.getBalance() + cost);
                    if (info != null) info.recordTrade(s.getItem(), s.getCount());
                    menu.buyContainer.setItem(i, ItemStack.EMPTY);
                    tradeHappened = true;
                }
            }

            for (int i = 0; i < 9; i++) {
                ItemStack s = menu.sellContainer.getItem(i);
                if (s.isEmpty()) continue;
                // ИСПРАВЛЕНО: double
                double val = PriceCalculator.getSellPrice(s, info) * s.getCount();
                if (owner.getBalance() >= val && TransactionService.addItemsSafe(owner, s.copy(), s.getCount())) {
                    player.setBalance(player.getBalance() + val);
                    owner.setBalance(owner.getBalance() - val);
                    if (info != null) info.recordTrade(s.getItem(), s.getCount());
                    menu.sellContainer.setItem(i, ItemStack.EMPTY);
                    tradeHappened = true;
                }
            }

            if (tradeHappened) {
                PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(player.getBalance(), owner.getBalance()));
                PacketDistributor.sendToPlayer(sp, new ClientboundTransactionResultPacket(true, player.getBalance(), owner.getBalance()));
                menu.broadcastChanges();
            } else {
                PacketDistributor.sendToPlayer(sp, new ClientboundTransactionResultPacket(false, player.getBalance(), owner.getBalance()));
            }
        });
    }
}