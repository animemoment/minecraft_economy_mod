package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.economy.PriceCalculator;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public record ClientboundFullPriceTablePacket(Map<String, Double> priceTable) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundFullPriceTablePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "full_price_table"));

    public static final StreamCodec<ByteBuf, ClientboundFullPriceTablePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.DOUBLE),
            ClientboundFullPriceTablePacket::priceTable,
            ClientboundFullPriceTablePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundFullPriceTablePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<Item, Double> table = new HashMap<>();
            for (Map.Entry<String, Double> entry : packet.priceTable().entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.getKey()));
                if (item != Items.AIR) {
                    table.put(item, entry.getValue());
                }
            }
            PriceCalculator.setClientPriceTable(table);

            // ЛОГ ДЛЯ ДЕБАГА: Проверяем, видит ли клиент открытое меню при получении пакета
            var container = net.minecraft.client.Minecraft.getInstance().player.containerMenu;
            EconomyMod.LOGGER.info("ЭКОНОМИКА КЛИЕНТ: Получен пакет цен! Открытый контейнер у игрока: {}", container.getClass().getSimpleName());

            if (container instanceof com.economymod.gui.menu.EconomyTradeMenu menu) {
                menu.refreshPrices();
                EconomyMod.LOGGER.info("ЭКОНОМИКА КЛИЕНТ: Цены в GUI успешно обновлены на динамические!");
            } else {
                EconomyMod.LOGGER.warn("ЭКОНОМИКА КЛИЕНТ: Пакет проигнорирован, так как кастомное меню еще не открыто!");
            }
        });
    }
}