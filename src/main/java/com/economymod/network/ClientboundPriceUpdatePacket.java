package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public record ClientboundPriceUpdatePacket(Map<Integer, Long> slotPrices) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundPriceUpdatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "price_update"));

    public static final StreamCodec<ByteBuf, ClientboundPriceUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_LONG),
            ClientboundPriceUpdatePacket::slotPrices,
            ClientboundPriceUpdatePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundPriceUpdatePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof EconomyTradeMenu tradeMenu) {
                tradeMenu.updatePrices(packet.slotPrices());
            }
        });
    }
}