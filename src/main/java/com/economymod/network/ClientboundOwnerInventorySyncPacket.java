package com.economymod.network;

import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.gui.screen.EconomyTradeScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ClientboundOwnerInventorySyncPacket(List<ItemStack> inventory, long budget) implements CustomPacketPayload {
    public static final Type<ClientboundOwnerInventorySyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "owner_inv_sync"));

    public static final StreamCodec<ByteBuf, ClientboundOwnerInventorySyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ItemStack.OPTIONAL_CODEC.listOf()),
                    ClientboundOwnerInventorySyncPacket::inventory,
                    ByteBufCodecs.VAR_LONG,
                    ClientboundOwnerInventorySyncPacket::budget,
                    ClientboundOwnerInventorySyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ClientboundOwnerInventorySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                menu.updateFromServer(packet.inventory(), packet.budget());
                if (Minecraft.getInstance().screen instanceof EconomyTradeScreen screen) {
                    screen.refreshData();
                }
            }
        });
    }
}