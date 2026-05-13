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

public record ClientboundVillagerSyncPacket(List<ItemStack> inventory, long budget) implements CustomPacketPayload {
    public static final Type<ClientboundVillagerSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "villager_sync"));

    public static final StreamCodec<ByteBuf, ClientboundVillagerSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ItemStack.OPTIONAL_CODEC.listOf()),
                    ClientboundVillagerSyncPacket::inventory,
                    ByteBufCodecs.VAR_LONG,
                    ClientboundVillagerSyncPacket::budget,
                    ClientboundVillagerSyncPacket::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(ClientboundVillagerSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                menu.updateFromServer(packet.inventory(), packet.budget());
            }
        });
    }
}