package com.economymod.entity.ai;

import com.economymod.EconomyMod;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.economy.PriceCalculator;
import com.economymod.economy.TransactionService;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class VillagerP2PTradeGoal extends Goal {
    private final Villager villager;
    private Villager partner;
    private BlockPos bellPos;
    private int cooldown = 0;
    private int actionCooldown = 0; // Внутренний кулдаун сделок во избежание спама тиков

    public VillagerP2PTradeGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (villager.level().getGameTime() % 80 != 0) return false;

        var myAtt = villager.getData(ModAttachments.VILLAGER.get());
        if (myAtt == null) return false;

        // Торговать идем только если есть реальные предложения или нужды
        if (myAtt.getOffers().isEmpty() && myAtt.getDemands().isEmpty()) return false;

        // Ищем колокол (POI собрания) в радиусе 48 блоков
        if (villager.level() instanceof ServerLevel sl) {
            Optional<BlockPos> bell = sl.getPoiManager().findClosest(
                    poiType -> poiType.is(PoiTypes.MEETING),
                    villager.blockPosition(),
                    48,
                    PoiManager.Occupancy.ANY
            );
            if (bell.isPresent()) {
                this.bellPos = bell.get().immutable();
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        this.cooldown = 400; // Глобальный кулдаун на поиск базара (20 секунд)
        this.actionCooldown = 0;
    }

    @Override
    public void tick() {
        if (bellPos == null) return;

        double distToBell = villager.distanceToSqr(bellPos.getX() + 0.5, bellPos.getY() + 1.0, bellPos.getZ() + 0.5);

        if (distToBell > 144.0) {
            // Идем к колоколу
            villager.getNavigation().moveTo(bellPos.getX() + 0.5, bellPos.getY() + 1.0, bellPos.getZ() + 0.5, 0.55D);
        } else {
            // Мы у колокола! Останавливаемся
            villager.getNavigation().stop();

            // Если мы на кулдауне после прошлой попытки — отдыхаем
            if (actionCooldown > 0) {
                actionCooldown--;
                return;
            }

            // 1. ВЫКРИКИ НА БАЗАРЕ: Раз в 6 секунд (120 тиков) житель делает объявление
            if (villager.tickCount % 120 == 0) {
                shoutTradeAnnouncement();
            }

            // 2. ПОИСК ПАРТНЕРА: Ищем соседа у колокола
            List<Villager> neighbors = villager.level().getEntitiesOfClass(
                    Villager.class, new AABB(villager.blockPosition()).inflate(12.0),
                    v -> v != villager && v.isAlive()
            );

            if (!neighbors.isEmpty()) {
                this.partner = neighbors.get(villager.getRandom().nextInt(neighbors.size()));
                if (partner != null) {
                    villager.getLookControl().setLookAt(partner, 30.0F, 30.0F);
                    performLiveTrade();
                }
            }
        }
    }

    private void shoutTradeAnnouncement() {
        var myAtt = villager.getData(ModAttachments.VILLAGER.get());
        if (myAtt == null) return;

        String shoutMessage = null;
        String professionName = villager.getVillagerData().getProfession().toString();
        professionName = professionName.substring(0, 1).toUpperCase() + professionName.substring(1);

        if (!myAtt.getDemands().isEmpty()) {
            ItemStack demandStack = myAtt.getDemands().get(0).stack;
            shoutMessage = "Мне очень нужен " + demandStack.getHoverName().getString() + " x" + demandStack.getCount() + "!";
        } else if (!myAtt.getOffers().isEmpty()) {
            ItemStack offerStack = myAtt.getOffers().get(0).stack;
            shoutMessage = "Продаю " + offerStack.getHoverName().getString() + " x" + offerStack.getCount() + " по отличной цене!";
        }

        if (shoutMessage != null) {
            String finalMsg = "§6[" + villager.getName().getString() + " (" + professionName + ")]§r: " + shoutMessage;
            for (Player player : villager.level().players()) {
                if (player.distanceToSqr(villager) < 256.0) {
                    player.sendSystemMessage(Component.literal(finalMsg));
                }
            }
            if (villager.level() instanceof ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                        villager.getX(), villager.getY() + 2.2, villager.getZ(), 3, 0.1, 0.1, 0.1, 0.05);
            }
        }
    }

    private void performLiveTrade() {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var myAtt = villager.getData(ModAttachments.VILLAGER.get());
        var partnerAtt = partner.getData(ModAttachments.VILLAGER.get());
        if (myAtt == null || partnerAtt == null) return;

        VillageNetworkData.VillageInfo info = VillageNetworkData.get(level).getVillageInfo(villager.blockPosition());

        for (VillagerAttachment.Demand myDemand : myAtt.getDemands()) {
            for (VillagerAttachment.Offer partnerOffer : partnerAtt.getOffers()) {
                if (ItemStack.isSameItemSameComponents(myDemand.stack, partnerOffer.stack)) {
                    double price = PriceCalculator.getBuyPrice(myDemand.stack, info);
                    int amount = Math.min(myDemand.stack.getCount(), partnerOffer.stack.getCount());

                    // ИСПРАВЛЕНО: Проводим транзакцию только если объемы сделки полностью валидны
                    if (amount > 0 && TransactionService.processTransaction(partnerAtt, myAtt, myDemand.stack, price, amount)) {
                        if (info != null) info.recordTrade(myDemand.stack.getItem(), amount);

                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                villager.getX(), villager.getY() + 2, villager.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                partner.getX(), partner.getY() + 2, partner.getZ(), 10, 0.2, 0.2, 0.2, 0.05);

                        // ИСПРАВЛЕНО: После успешной сделки мы мгновенно прерываем текущую цель и накладываем жесткий кулдаун!
                        this.cooldown = 600;        // Глобальный кулдаун ИИ на 30 секунд
                        this.actionCooldown = 400;  // Кулдаун внутренней активности на 20 секунд
                        this.partner = null;
                        this.bellPos = null;        // Сброс цели для выхода из тика планировщика
                        return;
                    }
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return bellPos != null && villager.level().getGameTime() % 400 != 0;
    }
}