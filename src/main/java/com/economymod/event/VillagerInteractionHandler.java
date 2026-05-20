package com.economymod.event;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public class VillagerInteractionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager villager) {
            // ИСПРАВЛЕНО: Отменяем событие на КЛИЕНТЕ и СЕРВЕРЕ, чтобы не моргал ванильный GUI
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME); // Анимация руки

            // Открываем кастомное меню только на сервере
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return villager.getDisplayName();
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                        VillagerAttachment att = villager.getData(ModAttachments.VILLAGER.get());
                        return new EconomyTradeMenu(id, inv, att);
                    }
                });
            }
        }
    }
}