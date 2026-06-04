package com.economymod.network;

import com.economymod.EconomyMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ClientboundVillagerStatsPacket(float health, float hunger, float fatigue, float distress, String emotion) implements CustomPacketPayload {

    public static final Type<ClientboundVillagerStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "villager_stats"));

    public static final StreamCodec<ByteBuf, ClientboundVillagerStatsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ClientboundVillagerStatsPacket::health,
            ByteBufCodecs.FLOAT, ClientboundVillagerStatsPacket::hunger,
            ByteBufCodecs.FLOAT, ClientboundVillagerStatsPacket::fatigue,
            ByteBufCodecs.FLOAT, ClientboundVillagerStatsPacket::distress,
            ByteBufCodecs.STRING_UTF8, ClientboundVillagerStatsPacket::emotion,
            ClientboundVillagerStatsPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundVillagerStatsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = Minecraft.getInstance().screen;
            // Передаем данные на экран торговли для отрисовки статус-баров
            if (screen instanceof com.economymod.gui.screen.EconomyTradeScreen tradeScreen) {
                tradeScreen.updateVillagerStats(
                        packet.health(),
                        packet.hunger(),
                        packet.fatigue(),
                        packet.distress(),
                        packet.emotion()
                );
            }
        });
    }
}