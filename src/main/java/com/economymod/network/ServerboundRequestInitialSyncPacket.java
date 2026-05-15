package com.economymod.network;

import com.economymod.economy.IEconomicActor;
import com.economymod.economy.PriceCalculator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ServerboundRequestInitialSyncPacket() implements CustomPacketPayload {

    public static final Type<ServerboundRequestInitialSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "request_initial_sync"));

    public static final StreamCodec<ByteBuf, ServerboundRequestInitialSyncPacket> STREAM_CODEC =
            StreamCodec.unit(new ServerboundRequestInitialSyncPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final ServerboundRequestInitialSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof EconomyTradeMenu menu)) return;

            IEconomicActor owner = menu.getOwnerActor();
            if (owner == null) return;

            // Балансы
            long balance = sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance();
            long budget = owner.getBalance();
            PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(balance, budget));

            // Инвентарь владельца
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                items.add(owner.getInventory().getItem(i).copy());
            }
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(items, budget));

            // === НОВОЕ: расчёт и отправка динамических цен ===
            Map<Integer, Long> prices = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = owner.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    long price = PriceCalculator.getBuyPrice(stack, null);
                    prices.put(i, price);
                }
            }
            PacketDistributor.sendToPlayer(sp, new ClientboundPriceUpdatePacket(prices));
        });
    }
}