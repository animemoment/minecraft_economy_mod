package com.economymod.network;

import com.economymod.entity.EconomyTraderEntity;
import com.economymod.gui.menu.EconomyTradeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundCustomOfferPacket(double offeredPrice) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("economymod", "custom_offer");
    public static final Type<ServerboundCustomOfferPacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, ServerboundCustomOfferPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeDouble(packet.offeredPrice()),
            buf -> new ServerboundCustomOfferPacket(buf.readDouble())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundCustomOfferPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (player.containerMenu instanceof EconomyTradeMenu menu) {
                    if (menu.getOwnerActor() instanceof EconomyTraderEntity trader) {
                        ItemStack stack = menu.buyContainer.getItem(0);
                        if (!stack.isEmpty()) {
                            // Обычная логика торга без ручного ввода
                            if (trader.evaluateOffer(stack, packet.offeredPrice(), true)) {
                                trader.processCustomTransaction(player, stack, packet.offeredPrice(), true);
                            }
                        }
                    }
                }
            }
        });
    }
}