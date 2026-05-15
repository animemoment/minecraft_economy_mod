package com.economymod.gui.screen;

import com.economymod.economy.PriceCalculator;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

        // Кнопка "Сделка"
        this.dealButton = Button.builder(Component.literal("Сделка"), btn -> {
            PacketDistributor.sendToServer(new ServerboundProcessTransactionPacket());
        }).bounds(x + 196, y + 155, 60, 20).build();
        this.addRenderableWidget(dealButton);

        // Кнопка "Очистить"
        this.clearButton = Button.builder(Component.literal("✕"), btn -> {
            PacketDistributor.sendToServer(new ServerboundClearBasketsPacket());
        }).bounds(x + 260, y + 155, 20, 20).build();
        this.addRenderableWidget(clearButton);

        PacketDistributor.sendToServer(new ServerboundRequestInitialSyncPacket());
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;

        // Отрисовка фона
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);

        // Отрисовка слотов владельца
        for (int i = 0; i < EconomyTradeMenu.OWNER_SLOTS; i++) {
            Slot s = menu.slots.get(i);
            drawSlotBack(g, x + s.x, y + s.y, 0xFF373737);
        }

        // Отрисовка слотов инвентаря игрока
        for (int i = EconomyTradeMenu.PLAYER_INV_START; i <= EconomyTradeMenu.HOTBAR_END; i++) {
            Slot s = menu.slots.get(i);
            drawSlotBack(g, x + s.x, y + s.y, 0xFF373737);
        }

        // Слоты покупки (Зеленые)
        for (int i = EconomyTradeMenu.BUY_START; i <= EconomyTradeMenu.BUY_END; i++) {
            Slot s = menu.slots.get(i);
            drawSlotBack(g, x + s.x, y + s.y, 0xFF2E4A2E);
        }

        // Слоты продажи (Красные)
        for (int i = EconomyTradeMenu.SELL_START; i <= EconomyTradeMenu.SELL_END; i++) {
            Slot s = menu.slots.get(i);
            drawSlotBack(g, x + s.x, y + s.y, 0xFF4A2E2E);
        }

        if (transactionFailed) drawStatusBorder(g, x, y, 0xFFFF0000);
        if (transactionSuccess) drawStatusBorder(g, x, y, 0xFF00FF00);
    }

    private void drawSlotBack(GuiGraphics g, int sx, int sy, int innerColor) {
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        g.fill(sx + 1, sy + 1, sx + 15, sy + 15, innerColor);
    }

    private void drawStatusBorder(GuiGraphics g, int x, int y, int color) {
        int bx = x + 196, by = y + 155;
        g.fill(bx - 2, by - 2, bx + 62, by, color);
        g.fill(bx - 2, by + 20, bx + 62, by + 22, color);
        g.fill(bx - 2, by, bx, by + 20, color);
        g.fill(bx + 60, by, bx + 62, by + 20, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Верхняя панель с балансом
        g.fill(0, 0, imageWidth, 12, 0x80000000);

        double bal = (double)menu.getClientBalance();
        double bud = (double)menu.getClientBudget();
        String ownerName = menu.getOwnerActor() != null ? menu.getOwnerActor().getActorDisplayName() : "Торговец";

        String youText = String.format("Вы: %.2f⛀", bal);
        String ownerText = String.format("%s: %.2f⛀", ownerName, bud);

        g.drawString(font, youText, 5, 2, 0x00FF00, false);
        int ownerWidth = font.width(ownerText);
        g.drawString(font, ownerText, imageWidth - ownerWidth - 5, 2, 0xFFD700, false);

        // Итоговая сумма сделки
        long buyCost = menu.getTotalBuyCost();
        long sellValue = menu.getTotalSellValue();
        double diff = (double)(sellValue - buyCost);

        String text = String.format("%.2f⛀", diff);
        int textColor = diff > 0 ? 0x00FF00 : (diff < 0 ? 0xFF0000 : 0xFFFFFF);

        g.drawString(font, Component.literal("Итого:"), 196, 140, 0xAAAAAA, false);
        g.drawString(font, text, 196 + font.width("Итого: ") + 4, 140, textColor, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        if (hoveredSlot != null && hoveredSlot.hasItem() && hoverTicks >= HOVER_DELAY) {
            ItemStack stack = hoveredSlot.getItem();
            List<Component> tooltip = new ArrayList<>(stack.getTooltipLines(
                    Item.TooltipContext.of(this.minecraft.level),
                    this.minecraft.player,
                    TooltipFlag.NORMAL
            ));

            double pricePerItem = (double)getPricePerItem(hoveredSlot);
            if (pricePerItem > 0) {
                tooltip.add(Component.literal(String.format("Цена: %.2f⛀/шт.", pricePerItem)).withStyle(ChatFormatting.GOLD));
                if (stack.getCount() > 1) {
                    tooltip.add(Component.literal(String.format("Стоимость: %.2f⛀", pricePerItem * stack.getCount())).withStyle(ChatFormatting.GRAY));
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

    private long getPricePerItem(Slot slot) {
        if (slot == null || !slot.hasItem()) return 0;
        ItemStack stack = slot.getItem();
        int idx = slot.index;

        if (idx < EconomyTradeMenu.OWNER_SLOTS) {
            return menu.getPrice(idx);
        } else if (idx >= EconomyTradeMenu.BUY_START && idx <= EconomyTradeMenu.BUY_END) {
            for (int j = 0; j < EconomyTradeMenu.OWNER_SLOTS; j++) {
                ItemStack ownerStack = menu.slots.get(j).getItem();
                if (ItemStack.isSameItemSameComponents(ownerStack, stack)) return menu.getPrice(j);
            }
        } else if (idx >= EconomyTradeMenu.SELL_START && idx <= EconomyTradeMenu.SELL_END ||
                idx >= EconomyTradeMenu.PLAYER_INV_START && idx <= EconomyTradeMenu.HOTBAR_END) {
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

    public void refreshData() {
        this.init(this.minecraft, this.width, this.height);
    }

    public void onTransactionFailed() {
        transactionFailed = true;
        failedAnimationTick = 20;
        transactionSuccess = false;
    }
}