package com.economymod.client;

import com.economymod.gui.screen.EconomyTradeScreen;
import com.economymod.registry.ModMenus;
import com.economymod.registry.ModEntities;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientEventHandler {

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ECONOMY_TRADE_MENU.get(), EconomyTradeScreen::new);
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ECONOMY_TRADER.get(), EconomyTraderRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGE_GUARD.get(), VillageGuardRenderer::new);
        event.registerEntityRenderer(ModEntities.TEST_CREATURE.get(), TestCreatureRenderer::new);
    }
}