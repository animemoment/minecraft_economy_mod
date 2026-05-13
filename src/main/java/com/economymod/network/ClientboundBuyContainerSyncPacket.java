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

public record ClientboundBuyContainerSyncPacket(List<ItemStack> items) implements CustomPacketPayload {
    public static final Type<ClientboundBuyContainerSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "buy_container_sync"));

    public static final StreamCodec<ByteBuf, ClientboundBuyContainerSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ItemStack.OPTIONAL_CODEC.listOf()),
                    ClientboundBuyContainerSyncPacket::items,
                    ClientboundBuyContainerSyncPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(ClientboundBuyContainerSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                for (int i = 0; i < Math.min(packet.items().size(), 9); i++) {
                    // Обновляем слот напрямую, чтобы GUI сразу перерисовался
                    menu.slots.get(EconomyTradeMenu.BUY_START + i).set(packet.items().get(i));
                }
            }
        });
    }
}