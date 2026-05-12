package com.economymod.network;

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
import com.economymod.economy.IEconomicActor;

import java.util.ArrayList;
import java.util.List;

public record ServerboundRemoveFromBuySlotPacket(int buySlot) implements CustomPacketPayload {
    public static final Type<ServerboundRemoveFromBuySlotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "remove_from_buy"));

    public static final StreamCodec<ByteBuf, ServerboundRemoveFromBuySlotPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ServerboundRemoveFromBuySlotPacket::buySlot, ServerboundRemoveFromBuySlotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundRemoveFromBuySlotPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof EconomyTradeMenu menu)) return;
            if (packet.buySlot() >= 0 && packet.buySlot() < 9) {
                menu.buyContainer.setItem(packet.buySlot(), ItemStack.EMPTY);
            }

            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 9; i++) items.add(menu.buyContainer.getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundBuyContainerSyncPacket(items));
        });
    }
}