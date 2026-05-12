package com.economymod.network;

import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ClientboundTraderInventorySyncPacket(List<ItemStack> items, long budget) implements CustomPacketPayload {

    public static final Type<ClientboundTraderInventorySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "trader_inv_sync"));

    public static final StreamCodec<ByteBuf, ClientboundTraderInventorySyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ItemStack.OPTIONAL_CODEC.listOf()),
                    ClientboundTraderInventorySyncPacket::items,
                    ByteBufCodecs.VAR_LONG,
                    ClientboundTraderInventorySyncPacket::budget,
                    ClientboundTraderInventorySyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundTraderInventorySyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                menu.updateFromServer(packet.items(), packet.budget());
            }
        });
    }
}