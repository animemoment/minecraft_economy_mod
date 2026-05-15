package com.economymod.network;

import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundTransactionResultPacket(boolean success, long playerBalance, long ownerBudget) implements CustomPacketPayload {
    public static final Type<ClientboundTransactionResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "transaction_result"));
    public static final StreamCodec<ByteBuf, ClientboundTransactionResultPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ClientboundTransactionResultPacket::success,
            ByteBufCodecs.VAR_LONG, ClientboundTransactionResultPacket::playerBalance,
            ByteBufCodecs.VAR_LONG, ClientboundTransactionResultPacket::ownerBudget,
            ClientboundTransactionResultPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(final ClientboundTransactionResultPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu menu) {
                menu.setClientBalance(packet.playerBalance());
                menu.setClientBudget(packet.ownerBudget());
                var screen = Minecraft.getInstance().screen;
                if (screen instanceof com.economymod.gui.screen.EconomyTradeScreen es) {
                    if (packet.success()) es.onTransactionSuccess(); else es.onTransactionFailed();
                }
            }
        });
    }
}