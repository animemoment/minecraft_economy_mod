package com.economymod.gui.screen;

import com.economymod.economy.PriceCalculator;
import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
import java.util.Locale;

public class EconomyTradeScreen extends AbstractContainerScreen<EconomyTradeMenu> {

    private Button dealButton;
    private Button clearButton;
    private EditBox priceEdit; // Текстовое поле ввода цены

    private Slot hoveredSlot = null;
    private Slot selectedSlot = null; // Выбранный для торга слот
    private int hoverTicks = 0;
    private static final int HOVER_DELAY = 20;

    private boolean transactionFailed = false;
    private int failedAnimationTick = 0;
    private boolean transactionSuccess = false;
    private int successAnimationTick = 0;

    // Характеристики жителя для вывода на табло
    private float villagerHealth = 1.0f;
    private float villagerHunger = 1.0f;
    private float villagerFatigue = 0.0f;
    private float villagerDistress = 0.0f;
    private String villagerEmotion = "Спокоен";

    public EconomyTradeScreen(EconomyTradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = 185;
        this.imageWidth = 290;
    }

    public void updateVillagerStats(float health, float hunger, float fatigue, float distress, String emotion) {
        this.villagerHealth = health;
        this.villagerHunger = hunger;
        this.villagerFatigue = fatigue;
        this.villagerDistress = distress;
        this.villagerEmotion = emotion;
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

        this.clearButton = Button.builder(Component.literal("✖"), btn -> {
            PacketDistributor.sendToServer(new ServerboundClearBasketsPacket());
            this.selectedSlot = null;
            if (this.priceEdit != null) {
                this.priceEdit.setVisible(false);
            }
        }).bounds(x + 250, y + 155, 20, 20).build();
        this.addRenderableWidget(clearButton);

        // ИСПРАВЛЕНО: Переместили EditBox на y + 76 (в свободный промежуток между корзинами)
        this.priceEdit = new EditBox(font, x + 196, y + 76, 50, 12, Component.literal("Цена"));
        this.priceEdit.setMaxLength(8);
        this.priceEdit.setFilter(text -> text.matches("\\d*\\.?\\d*")); // Фильтр: только цифры и точка
        this.priceEdit.setResponder(text -> {
            if (this.selectedSlot != null && !text.isEmpty()) {
                try {
                    double price = Double.parseDouble(text);
                    boolean isSell = selectedSlot.index >= EconomyTradeMenu.SELL_START && selectedSlot.index <= EconomyTradeMenu.SELL_END;
                    int slotIdx = isSell ? (selectedSlot.index - EconomyTradeMenu.SELL_START) : (selectedSlot.index - EconomyTradeMenu.BUY_START);

                    // Обновляем цены локально
                    if (isSell) menu.setSellCustomPrice(slotIdx, price);
                    else menu.setBuyCustomPrice(slotIdx, price);

                    // Синхронизируем цену с сервером
                    PacketDistributor.sendToServer(new ServerboundSetSlotPricePacket(isSell, slotIdx, price));
                } catch (NumberFormatException ignored) {}
            }
        });
        this.addRenderableWidget(priceEdit);
        this.priceEdit.setVisible(false); // Скрыто, пока игрок не нажмет на слот корзины

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

        // Подсвечиваем выделенный рамкой слот для торга
        if (selectedSlot != null) {
            int sx = x + selectedSlot.x;
            int sy = y + selectedSlot.y;
            boolean isSell = selectedSlot.index >= EconomyTradeMenu.SELL_START && selectedSlot.index <= EconomyTradeMenu.SELL_END;
            int color = isSell ? 0xFFFF3333 : 0xFF33FF33; // Красная рамка для продажи, Зеленая для покупки

            g.fill(sx - 1, sy - 1, sx + 17, sy, color);
            g.fill(sx - 1, sy + 16, sx + 17, sy + 17, color);
            g.fill(sx - 1, sy, sx, sy + 16, color);
            g.fill(sx + 16, sy, sx + 17, sy + 16, color);
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

        double bal = Math.max(0.0, menu.getClientBalance());
        double bud = Math.max(0.0, menu.getClientBudget());
        String ownerName = menu.getOwnerActor() != null ? menu.getOwnerActor().getActorDisplayName() : "Owner";

        // ИСПРАВЛЕНО: Заменили кракозябры кодировок на аккуратное слово 'coins'
        Component youText = Component.literal(String.format("You: %.2f coins", bal)).withStyle(ChatFormatting.GREEN);
        Component ownerText = Component.literal(String.format("%s: %.2f coins", ownerName, bud)).withStyle(ChatFormatting.GOLD);

        g.drawString(font, youText, 5, 2, 0x00FF00, false);
        int ownerWidth = font.width(ownerText);
        g.drawString(font, ownerText, imageWidth - ownerWidth - 5, 2, 0xFFD700, false);

        // Рисуем RPG-параметры дровосека в свободной строке Y=90 над инвентарем игрока
        g.drawString(font, String.format("❤ %.0f%%", villagerHealth * 100), 8, 90, 0xFF5555, false);
        g.drawString(font, String.format("🍖 %.0f%%", villagerHunger * 100), 55, 90, 0xFF9900, false);

        ChatFormatting emotionColor = villagerEmotion.equals("Напуган!") ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE;
        g.drawString(font, String.format("🧠 %s", villagerEmotion), 105, 90, emotionColor.getColor(), false);

        // Расчет итоговой суммы
        double buyCost = menu.getTotalBuyCost();
        double sellValue = menu.getTotalSellValue();
        double diff = sellValue - buyCost;
        String text;
        int textColor;
        if (diff > 0) { text = String.format("+%.2f coins", diff); textColor = 0x00FF00; }
        else if (diff < 0) { text = String.format("%.2f coins", diff); textColor = 0xFF0000; }
        else { text = "0 coins"; textColor = 0xFFFFFF; }

        g.drawString(font, Component.literal("Итого:"), 196, 146, 0xAAAAAA, false);
        g.drawString(font, Component.literal(text), 196 + font.width("Итого: ") + 4, 146, textColor, false);

        // ИСПРАВЛЕНО: Рисуем подсказку рыночной цены аккуратно СЛЕВА от EditBox (на X=135, Y=78)
        if (selectedSlot != null && selectedSlot.hasItem()) {
            double marketPrice = getPricePerItem(selectedSlot);
            g.drawString(font, String.format("Рынок: %.2f", marketPrice), 135, 78, 0x55FF55, false);
        }
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
                tooltip.add(Component.literal(String.format("Рыночная цена: %.2f coins/шт.", pricePerItem)).withStyle(ChatFormatting.GOLD));
                if (stack.getCount() > 1) {
                    double total = pricePerItem * stack.getCount();
                    tooltip.add(Component.literal(String.format("Рыночная стоимость: %.2f coins", total)).withStyle(ChatFormatting.GRAY));
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

        // Клик по корзинам торга (Buy и Sell слоты)
        boolean isBuyBasket = slotId >= EconomyTradeMenu.BUY_START && slotId <= EconomyTradeMenu.BUY_END;
        boolean isSellBasket = slotId >= EconomyTradeMenu.SELL_START && slotId <= EconomyTradeMenu.SELL_END;

        if (isBuyBasket || isSellBasket) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                this.selectedSlot = slot;
                this.priceEdit.setVisible(true);

                int slotIdx = isBuyBasket ? (slotId - EconomyTradeMenu.BUY_START) : (slotId - EconomyTradeMenu.SELL_START);
                double currentCustomPrice = isBuyBasket ? menu.getBuyCustomPrice(slotIdx) : menu.getSellCustomPrice(slotIdx);

                if (currentCustomPrice > 0.0) {
                    this.priceEdit.setValue(String.format(Locale.US, "%.2f", currentCustomPrice));
                } else {
                    this.priceEdit.setValue(""); // Пустое поле, если торг идет по рынку
                }
            } else {
                this.selectedSlot = null;
                this.priceEdit.setVisible(false);
            }
        } else if (slotId < EconomyTradeMenu.OWNER_SLOTS) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                PacketDistributor.sendToServer(new ServerboundAddToBuySlotPacket(slotId, type == ClickType.QUICK_MOVE));
            }
            this.selectedSlot = null;
            this.priceEdit.setVisible(false);
            return;
        } else if (slotId >= EconomyTradeMenu.BUY_START && slotId <= EconomyTradeMenu.BUY_END) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                PacketDistributor.sendToServer(new ServerboundRemoveFromBuySlotPacket(slotId - EconomyTradeMenu.BUY_START));
            }
            this.selectedSlot = null;
            this.priceEdit.setVisible(false);
            return;
        }

        super.slotClicked(slot, slotId, mouseButton, type);
    }

    public void onTransactionSuccess() {
        transactionSuccess = true;
        successAnimationTick = 20;
        transactionFailed = false;
        this.selectedSlot = null;
        this.priceEdit.setVisible(false);
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