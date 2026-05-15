package com.economymod;

import com.economymod.client.ClientEventHandler;
import com.economymod.command.CommandRegistry;
import com.economymod.command.GiveBudgetCommand;
import com.economymod.command.VillagerTradeCommand;
import com.economymod.event.ServerEvents;
import com.economymod.event.VillagerInteractionHandler;
import com.economymod.registry.ModAttachments;
import com.economymod.registry.ModEntities;
import com.economymod.registry.ModMenus;
import com.economymod.economy.EconomyManager;
import com.economymod.network.ServerboundCustomOfferPacket;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(EconomyMod.MODID)
public class EconomyMod {
    public static final String MODID = "economymod";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile EconomyManager economyManager;

    public EconomyMod(IEventBus modEventBus) {
        LOGGER.info("=== EconomyMod: loading ===");

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);

        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ClientEventHandler::registerScreens);
        modEventBus.addListener(ClientEventHandler::registerEntityRenderers);

        // РЕГИСТРАЦИЯ ПАКЕТОВ (Исправляет вылет UnsupportedOperationException)
        modEventBus.addListener(this::registerPackets);

        NeoForge.EVENT_BUS.register(new CommandRegistry());
        NeoForge.EVENT_BUS.register(VillagerTradeCommand.class);
        NeoForge.EVENT_BUS.register(GiveBudgetCommand.class);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        NeoForge.EVENT_BUS.register(VillagerInteractionHandler.class);
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("=== EconomyMod: loaded ===");
    }

    private void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Регистрируем твой пакет для торга
        registrar.playToServer(
                ServerboundCustomOfferPacket.TYPE,
                ServerboundCustomOfferPacket.STREAM_CODEC,
                ServerboundCustomOfferPacket::handle
        );
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        economyManager = new EconomyManager(event.getServer().overworld());
        LOGGER.info("EconomyMod: economy manager created");
    }

    public static EconomyManager getEconomyManager() {
        return economyManager;
    }
}