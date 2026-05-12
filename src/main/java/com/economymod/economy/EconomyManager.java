package com.economymod.economy;

import com.economymod.EconomyMod;
import com.economymod.attachment.PlayerEconomyAttachment;
import com.economymod.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;

public class EconomyManager {
    private final ServerLevel level;

    public EconomyManager(ServerLevel level) {
        this.level = level;
        EconomyMod.LOGGER.info("EconomyManager initialized");
    }

    public void addCoins(Player player, long amount) {
        if (player instanceof ServerPlayer sp) {
            sp.getData(ModAttachments.PLAYER_ECONOMY.get()).add(amount);
        }
    }

    public boolean removeCoins(Player player, long amount) {
        if (player instanceof ServerPlayer sp) {
            return sp.getData(ModAttachments.PLAYER_ECONOMY.get()).subtract(amount);
        }
        return false;
    }

    public long getBalance(Player player) {
        if (player instanceof ServerPlayer sp) {
            return sp.getData(ModAttachments.PLAYER_ECONOMY.get()).getBalance();
        }
        return 0L;
    }
}