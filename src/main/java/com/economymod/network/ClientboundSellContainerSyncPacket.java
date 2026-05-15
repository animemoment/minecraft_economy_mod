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

public record ClientboundSellContainerSyncPacket(List<ItemStack> items) implements CustomPacketPayload {
    public static final Type<ClientboundSellContainerSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "sell_container_sync"));

    public static final StreamCodec<ByteBuf, ClientboundSellContainerSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ItemStack.OPTIONAL_CODEC.listOf()),
                    ClientboundSellContainerSyncPacket::items,
                    ClientboundSellContainerSyncPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(ClientboundSellContainerSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                for (int i = 0; i < Math.min(packet.items().size(), 9); i++) {
                    menu.sellContainer.setItem(i, packet.items().get(i));
                }
            }
        });
    }
}