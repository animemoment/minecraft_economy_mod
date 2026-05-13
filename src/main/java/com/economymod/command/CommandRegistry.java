package com.economymod.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class CommandRegistry {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BalanceCommand.register(event.getDispatcher());
        CoinsCommand.register(event.getDispatcher());
    }
}