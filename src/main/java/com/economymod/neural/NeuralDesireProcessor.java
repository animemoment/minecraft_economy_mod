package com.economymod.neural;

import com.economymod.attachment.VillagerAttachment;
import com.economymod.economy.desire.Desire;
import com.economymod.economy.desire.Desire.DesireType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class NeuralDesireProcessor {
    private final VillagerAttachment attachment;
    private final NeuralNetwork network;

    public NeuralDesireProcessor(VillagerAttachment attachment, NeuralNetwork network) {
        this.attachment = attachment;
        this.network = network;
    }

    public List<Desire> calculateDesires() {
        List<Desire> desires = new ArrayList<>();
        if (!NeuralConfig.ENABLED) return desires;

        float eat = network.getOutputActivation(NeuralNetwork.OUTPUT_EAT);
        float sleep = network.getOutputActivation(NeuralNetwork.OUTPUT_SLEEP);
        float trade = network.getOutputActivation(NeuralNetwork.OUTPUT_TRADE);
        float run = network.getOutputActivation(NeuralNetwork.OUTPUT_RUN_AWAY);
        float explore = network.getOutputActivation(NeuralNetwork.OUTPUT_EXPLORE);
        float socialize = network.getOutputActivation(NeuralNetwork.OUTPUT_SOCIALIZE);

        if (eat > 0.6f) desires.add(new Desire(findBestFood(), DesireType.CONSUMABLE, (int)(eat * 100)));
        if (sleep > 0.6f) desires.add(new Desire(ItemStack.EMPTY, DesireType.LUXURY, (int)(sleep * 100)));
        if (trade > 0.6f) desires.add(createTradeDesire());
        if (run > 0.7f) desires.add(new Desire(ItemStack.EMPTY, DesireType.LUXURY, (int)(run * 100)));
        if (explore > 0.6f) desires.add(new Desire(ItemStack.EMPTY, DesireType.LUXURY, (int)(explore * 100)));
        if (socialize > 0.6f) desires.add(new Desire(ItemStack.EMPTY, DesireType.LUXURY, (int)(socialize * 100)));

        return desires;
    }

    private Desire createTradeDesire() {
        // Ищем что можно продать
        for (int i = 0; i < attachment.getInventory().getContainerSize(); i++) {
            ItemStack stack = attachment.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.WHEAT)) {
                return new Desire(stack.copy(), DesireType.MATERIAL, 70);
            }
        }
        return new Desire(new ItemStack(Items.WHEAT, 1), DesireType.MATERIAL, 70);
    }

    private ItemStack findBestFood() {
        for (int i = 0; i < attachment.getInventory().getContainerSize(); i++) {
            ItemStack stack = attachment.getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.is(Items.BREAD) || stack.is(Items.APPLE) || stack.is(Items.COOKED_BEEF))) {
                return stack.copy();
            }
        }
        return new ItemStack(Items.BREAD, 1);
    }
}