package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.economy.IEconomicActor;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData; // Добавлено
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos; // Добавлено
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ServerboundRequestInitialSyncPacket() implements CustomPacketPayload {
    public static final Type<ServerboundRequestInitialSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "request_initial_sync"));
    public static final StreamCodec<ByteBuf, ServerboundRequestInitialSyncPacket> STREAM_CODEC = StreamCodec.unit(new ServerboundRequestInitialSyncPacket());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundRequestInitialSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof EconomyTradeMenu menu)) return;

            IEconomicActor owner = menu.getOwnerActor();
            if (owner == null) return;

            // 1. Балансы (double)
            PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(
                    sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance(),
                    owner.getBalance()
            ));

            // 2. Инвентарь
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 36; i++) items.add(owner.getInventory().getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(items, owner.getBalance()));

            // 3. Таблица цен (ИСПРАВЛЕНО: Рассчитываем именно ДИНАМИЧЕСКИЕ цены для этой деревни)
            var manager = EconomyMod.getEconomyManager();
            if (manager != null && manager.getPriceTable() != null) {
                Map<String, Double> priceTableStrings = new HashMap<>();

                // Находим деревню по координатам торговца на сервере
                BlockPos pos = owner.getPosition();
                VillageNetworkData.VillageInfo villageInfo = (pos != null) ?
                        VillageNetworkData.get(sp.serverLevel()).getVillageInfo(pos) : null;

                manager.getPriceTable().getAllPrices().forEach((item, price) -> {
                    // Рассчитываем динамическую базовую цену с учетом спроса, предложения и инфляции этой деревни
                    double dynamicBase = com.economymod.economy.PriceCalculator.calculateDynamicPrice(new ItemStack(item), villageInfo);
                    priceTableStrings.put(BuiltInRegistries.ITEM.getKey(item).toString(), dynamicBase);
                });
                PacketDistributor.sendToPlayer(sp, new ClientboundFullPriceTablePacket(priceTableStrings));
            }
        });
    }
}