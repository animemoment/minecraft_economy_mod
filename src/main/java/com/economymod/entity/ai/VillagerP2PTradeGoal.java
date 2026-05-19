package com.economymod.entity.ai;

import com.economymod.EconomyMod;
import com.economymod.attachment.VillagerAttachment;
import com.economymod.economy.PriceCalculator;
import com.economymod.economy.TransactionService;
import com.economymod.registry.ModAttachments;
import com.economymod.world.VillageNetworkData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class VillagerP2PTradeGoal extends Goal {
    private final Villager villager;
    private Villager partner;
    private int cooldown = 0;

    public VillagerP2PTradeGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (villager.level().getGameTime() % 40 != 0) return false;

        List<Villager> neighbors = villager.level().getEntitiesOfClass(
                Villager.class, new AABB(villager.blockPosition()).inflate(10.0),
                v -> v != villager && v.isAlive()
        );

        if (!neighbors.isEmpty()) {
            this.partner = neighbors.get(villager.getRandom().nextInt(neighbors.size()));
            return true;
        }
        return false;
    }

    @Override public void start() { this.cooldown = 100; }

    @Override
    public void tick() {
        if (partner == null || !partner.isAlive()) { partner = null; return; }
        villager.getLookControl().setLookAt(partner, 30.0F, 30.0F);
        villager.getNavigation().moveTo(partner, 0.6D);

        if (villager.distanceToSqr(partner) < 4.0) {
            performTrade();
            this.partner = null;
        }
    }

    private void performTrade() {
        if (!(villager.level() instanceof ServerLevel level)) return;
        var myAtt = villager.getData(ModAttachments.VILLAGER.get());
        var partnerAtt = partner.getData(ModAttachments.VILLAGER.get());
        if (myAtt == null || partnerAtt == null) return;

        VillageNetworkData.VillageInfo info = VillageNetworkData.get(level).getVillageInfo(villager.blockPosition());

        for (VillagerAttachment.Demand myDemand : myAtt.getDemands()) {
            for (VillagerAttachment.Offer partnerOffer : partnerAtt.getOffers()) {
                if (ItemStack.isSameItemSameComponents(myDemand.stack, partnerOffer.stack)) {
                    // ИСПРАВЛЕНО: double
                    double price = PriceCalculator.getBuyPrice(myDemand.stack, info);
                    int amount = Math.min(myDemand.stack.getCount(), partnerOffer.stack.getCount());

                    if (TransactionService.processTransaction(partnerAtt, myAtt, myDemand.stack, price, amount)) {
                        if (info != null) info.recordTrade(myDemand.stack.getItem(), amount);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                villager.getX(), villager.getY() + 2, villager.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                        return;
                    }
                }
            }
        }
    }

    @Override public boolean canContinueToUse() {
        return partner != null && partner.isAlive() && villager.distanceToSqr(partner) < 100.0;
    }
}