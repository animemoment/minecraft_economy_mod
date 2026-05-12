package com.economymod.network;

import com.economymod.economy.IEconomicActor;
import com.economymod.entity.EconomyTraderEntity;
import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ServerboundAddToBuySlotPacket(int traderSlot, boolean shift) implements CustomPacketPayload {
    public static final Type<ServerboundAddToBuySlotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "add_to_buy"));

    public static final StreamCodec<ByteBuf, ServerboundAddToBuySlotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ServerboundAddToBuySlotPacket::traderSlot,
                    ByteBufCodecs.BOOL, ServerboundAddToBuySlotPacket::shift,
                    ServerboundAddToBuySlotPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundAddToBuySlotPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof EconomyTradeMenu menu)) return;
            IEconomicActor owner = menu.getOwnerActor();
            if (owner == null) return;

            int slot = packet.traderSlot();
            if (slot < 0 || slot >= 36) return;

            ItemStack source = owner.getInventory().getItem(slot);
            if (source.isEmpty()) return;

            ItemStack toAdd = source.copy();
            if (!packet.shift()) {
                toAdd.setCount(1);
            }

            for (int i = 0; i < 9; i++) {
                ItemStack existing = menu.buyContainer.getItem(i);
                if (existing.isEmpty()) {
                    menu.buyContainer.setItem(i, toAdd);
                    break;
                } else if (ItemStack.isSameItemSameComponents(existing, toAdd)) {
                    int canAdd = Math.min(64 - existing.getCount(), toAdd.getCount());
                    if (canAdd > 0) {
                        existing.grow(canAdd);
                        toAdd.shrink(canAdd);
                        if (toAdd.isEmpty()) break;
                    }
                }
            }

            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 9; i++) items.add(menu.buyContainer.getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundBuyContainerSyncPacket(items));
        });
    }
}