package com.economymod.registry;

import com.economymod.EconomyMod;
import com.economymod.network.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Исправлено: убраны устаревшие параметры bus
@EventBusSubscriber(modid = EconomyMod.MODID)
public class ModPackets {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Server-bound packets
        registrar.playToServer(
                ServerboundAddToBuySlotPacket.TYPE,
                ServerboundAddToBuySlotPacket.STREAM_CODEC,
                ServerboundAddToBuySlotPacket::handleServer
        );
        registrar.playToServer(
                ServerboundRemoveFromBuySlotPacket.TYPE,
                ServerboundRemoveFromBuySlotPacket.STREAM_CODEC,
                ServerboundRemoveFromBuySlotPacket::handleServer
        );
        registrar.playToServer(
                ServerboundProcessTransactionPacket.TYPE,
                ServerboundProcessTransactionPacket.STREAM_CODEC,
                ServerboundProcessTransactionPacket::handleServer
        );
        registrar.playToServer(
                ServerboundRequestInitialSyncPacket.TYPE,
                ServerboundRequestInitialSyncPacket.STREAM_CODEC,
                ServerboundRequestInitialSyncPacket::handleServer
        );
        registrar.playToServer(
                ServerboundClearBasketsPacket.TYPE,
                ServerboundClearBasketsPacket.STREAM_CODEC,
                ServerboundClearBasketsPacket::handleServer
        );
        registrar.playToServer(
                ServerboundCustomOfferPacket.TYPE,
                ServerboundCustomOfferPacket.STREAM_CODEC,
                ServerboundCustomOfferPacket::handle
        );

        // Client-bound packets
        registrar.playToClient(
                ClientboundTransactionResultPacket.TYPE,
                ClientboundTransactionResultPacket.STREAM_CODEC,
                ClientboundTransactionResultPacket::handleClient
        );
        registrar.playToClient(
                ClientboundBalanceSyncPacket.TYPE,
                ClientboundBalanceSyncPacket.STREAM_CODEC,
                ClientboundBalanceSyncPacket::handleClient
        );
        registrar.playToClient(
                ClientboundOwnerInventorySyncPacket.TYPE,
                ClientboundOwnerInventorySyncPacket.STREAM_CODEC,
                ClientboundOwnerInventorySyncPacket::handleClient
        );
        registrar.playToClient(
                ClientboundBuyContainerSyncPacket.TYPE,
                ClientboundBuyContainerSyncPacket.STREAM_CODEC,
                ClientboundBuyContainerSyncPacket::handleClient
        );
        registrar.playToClient(
                ClientboundFullPriceTablePacket.TYPE,
                ClientboundFullPriceTablePacket.STREAM_CODEC,
                ClientboundFullPriceTablePacket::handleClient
        );
        registrar.playToClient(
                ClientboundPriceUpdatePacket.TYPE,
                ClientboundPriceUpdatePacket.STREAM_CODEC,
                ClientboundPriceUpdatePacket::handleClient
        );
        registrar.playToClient(
                ClientboundSellContainerSyncPacket.TYPE,
                ClientboundSellContainerSyncPacket.STREAM_CODEC,
                ClientboundSellContainerSyncPacket::handleClient
        );
    }
}