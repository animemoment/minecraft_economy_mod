package com.economymod.gui.screen;

import com.economymod.economy.PriceCalculator;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class EconomyTradeScreen extends AbstractContainerScreen<EconomyTradeMenu> {

    private Button dealButton;
    private Button clearButton;

    private Slot hoveredSlot = null;
    private int hoverTicks = 0;
    private static final int HOVER_DELAY = 20;

    private boolean transactionFailed = false;
    private int failedAnimationTick = 0;
    private boolean transactionSuccess = false;
    private int successAnimationTick = 0;

    public EconomyTradeScreen(EconomyTradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 185;
        this.imageWidth = 290;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        this.dealButton = Button.builder(Component.literal("Сделка"), btn -> {
            PacketDistributor.sendToServer(new ServerboundProcessTransactionPacket());
        }).bounds(x + 196, y + 155, 50, 20).build();
        this.addRenderableWidget(dealButton);
        this.clearButton = Button.builder(Component.literal("✕"), btn -> {
            PacketDistributor.sendToServer(new ServerboundClearBasketsPacket());
        }).bounds(x + 250, y + 155, 20, 20).build();
        this.addRenderableWidget(clearButton);

        PacketDistributor.sendToServer(new ServerboundRequestInitialSyncPacket());
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);

        for (int i = 0; i < EconomyTradeMenu.OWNER_SLOTS; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF373737);
        }

        for (int i = EconomyTradeMenu.PLAYER_INV_START; i <= EconomyTradeMenu.HOTBAR_END; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF373737);
        }

        for (int i = EconomyTradeMenu.BUY_START; i <= EconomyTradeMenu.BUY_END; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFFA8D5A8);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF2E4A2E);
        }

        for (int i = EconomyTradeMenu.SELL_START; i <= EconomyTradeMenu.SELL_END; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFFD5A8A8);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF4A2E2E);
        }

        if (transactionFailed) drawButtonBorder(g, x, y, 0xFFFF0000);
        if (transactionSuccess) drawButtonBorder(g, x, y, 0xFF00FF00);
    }

    private void drawButtonBorder(GuiGraphics g, int x, int y, int color) {
        int bx = x + 196, by = y + 155;
        g.fill(bx - 2, by - 2, bx + 52, by, color);
        g.fill(bx - 2, by + 20, bx + 52, by + 22, color);
        g.fill(bx - 2, by, bx, by + 20, color);
        g.fill(bx + 50, by, bx + 52, by + 20, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, imageWidth, 12, 0x80000000);

        long bal = Math.max(0, menu.getClientBalance());
        long bud = Math.max(0, menu.getClientBudget());
        String ownerName = menu.getOwnerActor() != null ? menu.getOwnerActor().getActorDisplayName() : "Owner";

        Component youText = Component.literal("You: " + bal + "⛀").withStyle(ChatFormatting.GREEN);
        Component ownerText = Component.literal(ownerName + ": " + bud + "⛀").withStyle(ChatFormatting.GOLD);

        g.drawString(font, youText, 5, 2, 0x00FF00, false);
        int ownerWidth = font.width(ownerText);
        g.drawString(font, ownerText, imageWidth - ownerWidth - 5, 2, 0xFFD700, false);

        double buyCost = menu.getTotalBuyCost();
        double sellValue = menu.getTotalSellValue();
        double diff = sellValue - buyCost;
        String text;
        int textColor;
        if (diff > 0) { text = String.format("+%.2f⛀", diff); textColor = 0x00FF00; }
        else if (diff < 0) { text = String.format("%.2f⛀", diff); textColor = 0xFF0000; }
        else { text = "0⛀"; textColor = 0xFFFFFF; }

        g.drawString(font, Component.literal("Итого:"), 196, 146, 0xAAAAAA, false);
        g.drawString(font, Component.literal(text), 196 + font.width("Итого: ") + 4, 146, textColor, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (hoveredSlot != null && hoveredSlot.hasItem() && hoverTicks >= HOVER_DELAY) {
            ItemStack stack = hoveredSlot.getItem();
            List<Component> tooltip = new ArrayList<>(stack.getTooltipLines(
                    Item.TooltipContext.of(this.minecraft.level),
                    this.minecraft.player,
                    TooltipFlag.NORMAL
            ));

            double pricePerItem = getPricePerItem(hoveredSlot);
            if (pricePerItem > 0) {
                tooltip.add(Component.literal(String.format("Цена: %.2f⛀/шт.", pricePerItem)).withStyle(ChatFormatting.GOLD));
                if (stack.getCount() > 1) {
                    double total = pricePerItem * stack.getCount();
                    tooltip.add(Component.literal(String.format("Стоимость: %.2f⛀", total)).withStyle(ChatFormatting.GRAY));
                }
            }

            guiGraphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();

        if (failedAnimationTick > 0) { failedAnimationTick--; if (failedAnimationTick == 0) transactionFailed = false; }
        if (successAnimationTick > 0) { successAnimationTick--; if (successAnimationTick == 0) transactionSuccess = false; }

        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.hasItem() && slot == hoveredSlot) {
            hoverTicks++;
        } else {
            hoveredSlot = slot;
            hoverTicks = 0;
        }
    }

    private double getPricePerItem(Slot slot) {
        if (slot == null || !slot.hasItem()) return 0;
        ItemStack stack = slot.getItem();
        int idx = slot.index;

        if (idx < EconomyTradeMenu.OWNER_SLOTS) {
            return menu.getPrice(idx);
        } else if (idx >= EconomyTradeMenu.BUY_START && idx <= EconomyTradeMenu.BUY_END) {
            for (int j = 0; j < EconomyTradeMenu.OWNER_SLOTS; j++) {
                ItemStack ownerStack = menu.slots.get(j).getItem();
                if (ItemStack.isSameItemSameComponents(ownerStack, stack)) {
                    return menu.getPrice(j);
                }
            }
            return 0;
        } else if (idx >= EconomyTradeMenu.SELL_START && idx <= EconomyTradeMenu.SELL_END) {
            return PriceCalculator.getSellPrice(stack, null);
        } else if (idx >= EconomyTradeMenu.PLAYER_INV_START && idx <= EconomyTradeMenu.HOTBAR_END) {
            return PriceCalculator.getSellPrice(stack, null);
        }
        return 0;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot == null) { super.slotClicked(null, slotId, mouseButton, type); return; }
        if (slotId < EconomyTradeMenu.OWNER_SLOTS) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                PacketDistributor.sendToServer(new ServerboundAddToBuySlotPacket(slotId, type == ClickType.QUICK_MOVE));
            }
            return;
        }
        if (slotId >= EconomyTradeMenu.BUY_START && slotId <= EconomyTradeMenu.BUY_END) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                PacketDistributor.sendToServer(new ServerboundRemoveFromBuySlotPacket(slotId - EconomyTradeMenu.BUY_START));
            }
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    public void onTransactionSuccess() {
        transactionSuccess = true;
        successAnimationTick = 20;
        transactionFailed = false;
    }

    public void onTransactionFailed() {
        transactionFailed = true;
        failedAnimationTick = 20;
        transactionSuccess = false;
    }

    public void refreshData() {
        this.successAnimationTick = 2;
    }
}