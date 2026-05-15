package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.economy.PriceCalculator;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public record ClientboundFullPriceTablePacket(Map<String, Long> priceTable) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundFullPriceTablePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "full_price_table"));

    public static final StreamCodec<ByteBuf, ClientboundFullPriceTablePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG),
            ClientboundFullPriceTablePacket::priceTable,
            ClientboundFullPriceTablePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundFullPriceTablePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<Item, Long> table = new HashMap<>();
            for (Map.Entry<String, Long> entry : packet.priceTable().entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
                if (item != null) {
                    table.put(item, entry.getValue());
                }
            }
            PriceCalculator.setClientPriceTable(table);
            EconomyMod.LOGGER.info("Client received full price table with {} items", table.size());
        });
    }
}