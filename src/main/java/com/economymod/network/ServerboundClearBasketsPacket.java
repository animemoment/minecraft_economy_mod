// ServerboundClearBasketsPacket.java
package com.economymod.network;

import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundClearBasketsPacket() implements CustomPacketPayload {
    public static final Type<ServerboundClearBasketsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "clear_baskets"));
    public static final StreamCodec<ByteBuf, ServerboundClearBasketsPacket> STREAM_CODEC = StreamCodec.unit(new ServerboundClearBasketsPacket());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundClearBasketsPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof EconomyTradeMenu menu) {
                menu.clearBaskets(sp);
            }
        });
    }
}