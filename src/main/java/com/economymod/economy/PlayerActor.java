package com.economymod.economy;

import com.economymod.registry.ModAttachments;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class PlayerActor implements IEconomicActor {
    private final Player player;

    public PlayerActor(Player player) {
        this.player = player;
    }

    public Inventory getPlayerInventory() {
        return player.getInventory();
    }

    @Override
    public SimpleContainer getInventory() {
        return null;
    }

    @Override
    public double getBalance() {
        var eco = player.getData(ModAttachments.PLAYER_ECONOMY.get());
        return eco.getBalance();
    }

    @Override
    public void setBalance(double balance) {
        var eco = player.getData(ModAttachments.PLAYER_ECONOMY.get());
        eco.setBalance(balance);
    }

    @Override
    public String getActorDisplayName() {
        return player.getDisplayName().getString();
    }
}