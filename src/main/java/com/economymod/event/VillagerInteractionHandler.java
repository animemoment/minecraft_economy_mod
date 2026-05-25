package com.economymod.event;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.entity.VillageGuardEntity;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public class VillagerInteractionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity targetEntity = event.getTarget();

        if (targetEntity instanceof Villager villager) {
            Player interactingPlayer = event.getEntity();

            // Проверяем, держит ли игрок щит в правой или левой руке
            boolean hasShield = interactingPlayer.getMainHandItem().getItem() instanceof ShieldItem ||
                    interactingPlayer.getOffhandItem().getItem() instanceof ShieldItem;

            if (hasShield) {
                // ИСПРАВЛЕНО: Полностью убрали проверку на Shift!
                // Теперь щит ВСЕГДА имеет наивысший приоритет над торговлей в любом состоянии (и стоя, и на Shift).
                // Клик по жителю блокируется с результатом PASS, заставляя щит мгновенно подняться и отразить удар.
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.PASS);
                return;
            }

            // Проверка: если житель в бою – не открываем GUI
            if (villager instanceof VillageGuardEntity guard) {
                if (guard.getTarget() != null && guard.getTarget().isAlive()) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.CONSUME);
                    return;
                }
            } else {
                LivingEntity lastHurt = villager.getLastHurtByMob();
                if (lastHurt != null && villager.tickCount - villager.getLastHurtByMobTimestamp() < 100) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.CONSUME);
                    return;
                }
            }

            // Отменяем стандартное взаимодействие и открываем кастомный GUI торговли
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
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