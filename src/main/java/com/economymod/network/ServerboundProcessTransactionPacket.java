package com.economymod.network;

import com.economymod.EconomyMod;
import com.economymod.economy.PriceCalculator;
import com.economymod.economy.TransactionService;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.world.VillageNetworkData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerboundProcessTransactionPacket() implements CustomPacketPayload {
    public static final Type<ServerboundProcessTransactionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "process_transaction"));
    public static final StreamCodec<ByteBuf, ServerboundProcessTransactionPacket> STREAM_CODEC = StreamCodec.unit(new ServerboundProcessTransactionPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final ServerboundProcessTransactionPacket packet, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !(sp.containerMenu instanceof EconomyTradeMenu menu)) return;

            // Предотвращение Race Condition при дюп-кликах через пакеты
            if (!menu.tryLockTransaction()) {
                EconomyMod.LOGGER.warn("ЭКОНОМИКА: Двойной клик/клиентская задержка. Транзакция для {} заблокирована.", sp.getName().getString());
                return;
            }

            try {
                var owner = menu.getOwnerActor();
                var player = menu.getPlayerActor();
                if (owner == null || player == null) return;

                ServerLevel level = sp.serverLevel();
                VillageNetworkData.VillageInfo info = VillageNetworkData.get(level).getVillageInfo(owner.getPosition());

                boolean tradeHappened = false;
                boolean villagerGotAngry = false;

                // 1. ОБРАБОТКА ПОКУПКИ ИГРОКОМ У ЖИТЕЛЯ (Buy Basket)
                for (int i = 0; i < 9; i++) {
                    ItemStack s = menu.buyContainer.getItem(i);
                    if (s.isEmpty()) continue;

                    double marketPricePerItem = PriceCalculator.getBuyPrice(s, info);
                    double customPricePerItem = menu.getBuyCustomPrice(i);

                    // Если игрок не ввел цену, покупаем по рыночной стоимости
                    double finalPricePerItem = (customPricePerItem == 0.0) ? marketPricePerItem : customPricePerItem;

                    // ОЦЕНКА ЖАДНОСТИ: Житель не продаст, если игрок предложил меньше 85% рыночной цены
                    if (customPricePerItem > 0.0 && finalPricePerItem < marketPricePerItem * 0.85) {
                        villagerGotAngry = true;
                        continue;
                    }

                    double totalCost = finalPricePerItem * s.getCount();
                    if (player.getBalance() >= totalCost && TransactionService.addItemsSafe(player, s.copy(), s.getCount())) {
                        player.setBalance(player.getBalance() - totalCost);
                        owner.setBalance(owner.getBalance() + totalCost);
                        if (info != null) info.recordTrade(s.getItem(), s.getCount());
                        menu.buyContainer.setItem(i, ItemStack.EMPTY);
                        tradeHappened = true;
                    }
                }

                // 2. ОБРАБОТКА ПРОДАЖИ ИГРОКОМ ЖИТЕЛЮ (Sell Basket)
                for (int i = 0; i < 9; i++) {
                    ItemStack s = menu.sellContainer.getItem(i);
                    if (s.isEmpty()) continue;

                    // Если жителю этот предмет вообще не нужен по его роли, он сердится и отказывается!
                    if (!owner.wantsToBuy(s)) {
                        villagerGotAngry = true;
                        continue;
                    }

                    double marketPricePerItem = PriceCalculator.getSellPrice(s, info);
                    double customPricePerItem = menu.getSellCustomPrice(i);

                    double finalPricePerItem = (customPricePerItem == 0.0) ? marketPricePerItem : customPricePerItem;

                    // ОЦЕНКА ЖАДНОСТИ: Житель не купит, если игрок требует больше 115% рыночной цены
                    if (customPricePerItem > 0.0 && finalPricePerItem > marketPricePerItem * 1.15) {
                        villagerGotAngry = true;
                        continue;
                    }

                    double totalValue = finalPricePerItem * s.getCount();

                    // БЮДЖЕТНЫЙ ЛИМИТ: Житель не купит, если у него нет столько денег в кошельке!
                    if (owner.getBalance() < totalValue) {
                        villagerGotAngry = true;
                        continue;
                    }

                    if (TransactionService.addItemsSafe(owner, s.copy(), s.getCount())) {
                        player.setBalance(player.getBalance() + totalValue);
                        owner.setBalance(owner.getBalance() - totalValue);
                        if (info != null) info.recordTrade(s.getItem(), s.getCount());
                        menu.sellContainer.setItem(i, ItemStack.EMPTY);
                        tradeHappened = true;
                    }
                }

                LivingEntity entity = owner.getEntity();

                // 3. РЕАКЦИЯ ЖИТЕЛЯ (Частицы и Звуки)
                if (villagerGotAngry) {
                    if (entity != null) {
                        level.playSound(null, entity.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                                entity.getX(), entity.getY() + 2.0D, entity.getZ(), 5, 0.2D, 0.2D, 0.2D, 0.02D);
                    }
                }

                if (tradeHappened) {
                    if (entity != null && !villagerGotAngry) {
                        level.playSound(null, entity.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                entity.getX(), entity.getY() + 2.0D, entity.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.05D);
                    }

                    PacketDistributor.sendToPlayer(sp, new ClientboundBalanceSyncPacket(player.getBalance(), owner.getBalance()));
                    PacketDistributor.sendToPlayer(sp, new ClientboundTransactionResultPacket(true, player.getBalance(), owner.getBalance()));
                    menu.broadcastChanges();
                } else {
                    PacketDistributor.sendToPlayer(sp, new ClientboundTransactionResultPacket(false, player.getBalance(), owner.getBalance()));
                }
            } finally {
                menu.unlockTransaction();
            }
        });
    }
}