package com.economymod.network;

import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.gui.screen.EconomyTradeScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundBalanceSyncPacket(long balance, long traderBudget) implements CustomPacketPayload {

    public static final Type<ClientboundBalanceSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "balance_sync"));

    public static final StreamCodec<ByteBuf, ClientboundBalanceSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ClientboundBalanceSyncPacket::balance,
                    ByteBufCodecs.VAR_LONG, ClientboundBalanceSyncPacket::traderBudget,
                    ClientboundBalanceSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundBalanceSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                menu.setClientBalance(packet.balance());
                menu.setClientBudget(packet.traderBudget());
            }
        });
    }
}