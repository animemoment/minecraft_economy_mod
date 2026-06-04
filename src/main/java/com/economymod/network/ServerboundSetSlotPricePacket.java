package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ServerboundSetSlotPricePacket(boolean isSell, int slotIndex, double customPrice) implements CustomPacketPayload {

    public static final Type<ServerboundSetSlotPricePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "set_slot_price"));

    public static final StreamCodec<ByteBuf, ServerboundSetSlotPricePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ServerboundSetSlotPricePacket::isSell,
            ByteBufCodecs.VAR_INT, ServerboundSetSlotPricePacket::slotIndex,
            ByteBufCodecs.DOUBLE, ServerboundSetSlotPricePacket::customPrice,
            ServerboundSetSlotPricePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final ServerboundSetSlotPricePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp && sp.containerMenu instanceof EconomyTradeMenu menu) {
                // Записываем предложенную цену в корзину меню на сервере
                if (packet.isSell()) {
                    menu.setSellCustomPrice(packet.slotIndex(), packet.customPrice());
                } else {
                    menu.setBuyCustomPrice(packet.slotIndex(), packet.customPrice());
                }
            }
        });
    }
}