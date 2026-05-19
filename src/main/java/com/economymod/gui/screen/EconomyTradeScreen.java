package com.economymod.gui.screen;

import com.economymod.economy.PriceCalculator;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.*;
import net.minecraft.ChatFormatting;
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
    private boolean transactionFailed = false;
    private int failedAnimationTick = 0;
    private boolean transactionSuccess = false;
    private int successAnimationTick = 0;

    public EconomyTradeScreen(EconomyTradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 200;
        this.imageWidth = 290;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        this.dealButton = Button.builder(Component.literal("Сделка"), btn -> {
            PacketDistributor.sendToServer(new ServerboundProcessTransactionPacket());
        }).bounds(x + 196, y + 172, 60, 20).build();
        this.addRenderableWidget(dealButton);
        this.clearButton = Button.builder(Component.literal("✕"), btn -> {
            PacketDistributor.sendToServer(new ServerboundClearBasketsPacket());
        }).bounds(x + 260, y + 172, 20, 20).build();
        this.addRenderableWidget(clearButton);
        PacketDistributor.sendToServer(new ServerboundRequestInitialSyncPacket());
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        for (Slot s : this.getMenu().slots) {
            int color = 0xFF373737;
            if (s.index >= EconomyTradeMenu.BUY_START && s.index <= EconomyTradeMenu.BUY_END) color = 0xFF2E4A2E;
            if (s.index >= EconomyTradeMenu.SELL_START && s.index <= EconomyTradeMenu.SELL_END) color = 0xFF4A2E2E;
            drawSlotBack(g, x + s.x, y + s.y, color);
        }
        if (transactionFailed) drawStatusBorder(g, x, y, 0xFFFF0000);
        if (transactionSuccess) drawStatusBorder(g, x, y, 0xFF00FF00);
    }

    private void drawSlotBack(GuiGraphics g, int sx, int sy, int innerColor) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B);
        g.fill(sx, sy, sx + 16, sy + 16, innerColor);
    }

    private void drawStatusBorder(GuiGraphics g, int x, int y, int color) {
        int bx = x + 196, by = y + 172;
        g.fill(bx - 2, by - 2, bx + 84, by, color);
        g.fill(bx - 2, by + 20, bx + 84, by + 22, color);
        g.fill(bx - 2, by, bx, by + 20, color);
        g.fill(bx + 82, by, bx + 84, by + 20, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Форматирование с %.2f для отображения копеек
        String youText = String.format("Вы: %.2f⛀", (double)getMenu().getClientBalance());
        String ownerText = String.format("Торговец: %.2f⛀", (double)getMenu().getClientBudget());

        g.drawString(font, youText, 8, 6, 0x00FF00, false);
        g.drawString(font, ownerText, 150, 6, 0xFFD700, false);

        double buyCost = getMenu().getTotalBuyCost();
        double sellValue = getMenu().getTotalSellValue();
        double diff = sellValue - buyCost;

        String text = String.format("Итого: %.2f⛀", diff);
        g.drawString(font, text, 196, 158, diff >= 0 ? 0x00FF00 : 0xFF0000, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y) {
        super.renderTooltip(g, x, y);
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.hasItem()) {
            // ИСПРАВЛЕНО: Правильный метод получения тултипа для 1.21.1
            List<Component> tooltip = new ArrayList<>(slot.getItem().getTooltipLines(
                    Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.Default.NORMAL));

            long price = 0;
            if (slot.index < EconomyTradeMenu.OWNER_SLOTS || (slot.index >= EconomyTradeMenu.BUY_START && slot.index <= EconomyTradeMenu.BUY_END)) {
                price = (long) PriceCalculator.getBuyPrice(slot.getItem(), null);
            } else {
                price = (long) PriceCalculator.getSellPrice(slot.getItem(), null);
            }
            if (price > 0) {
                tooltip.add(net.minecraft.network.chat.Component.literal(String.format("Цена: %.2f⛀/шт.", (double)price)).withStyle(ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, tooltip, x, y);
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot == null) return;
        if (slotId < EconomyTradeMenu.OWNER_SLOTS) {
            PacketDistributor.sendToServer(new ServerboundAddToBuySlotPacket(slotId, type == ClickType.QUICK_MOVE));
            return;
        }
        if (slotId >= EconomyTradeMenu.BUY_START && slotId <= EconomyTradeMenu.BUY_END) {
            PacketDistributor.sendToServer(new ServerboundRemoveFromBuySlotPacket(slotId - EconomyTradeMenu.BUY_START));
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    public void onTransactionSuccess() { transactionSuccess = true; successAnimationTick = 30; transactionFailed = false; }
    public void onTransactionFailed() { transactionFailed = true; failedAnimationTick = 30; transactionSuccess = false; }

    @Override
    public void containerTick() {
        super.containerTick();
        if (failedAnimationTick > 0) failedAnimationTick--;
        if (successAnimationTick > 0) successAnimationTick--;
        if (failedAnimationTick == 0) transactionFailed = false;
        if (successAnimationTick == 0) transactionSuccess = false;
    }
}