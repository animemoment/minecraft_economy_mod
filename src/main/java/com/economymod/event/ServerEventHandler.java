package com.economymod.event;

import com.economymod.EconomyMod;
import com.economymod.registry.ModAttachments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ServerEventHandler {
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            var p = event.getEntity();
            var eco = p.getData(ModAttachments.PLAYER_ECONOMY.get());
            EconomyMod.LOGGER.info("Player {} joined. Balance: {}",
                    p.getName().getString(), eco.getBalance());
        }
    }
}