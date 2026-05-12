package com.economymod.gui.screen;

import com.economymod.gui.menu.EconomyTradeMenu;
import com.economymod.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class EconomyTradeScreen extends AbstractContainerScreen<EconomyTradeMenu> {
    private Button dealButton;
    private Button clearButton;

    // Анимации
    private boolean transactionFailed = false;
    private int failedAnimationTick = 0;
    private boolean transactionSuccess = false;
    private int successAnimationTick = 0;
    private final Map<Integer, Float> slotAnimations = new HashMap<>();
    private final Map<Integer, Float> slotAnimationTargets = new HashMap<>();

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
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);

        // Каталог владельца
        for (int i = 0; i < 36; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF373737);
            if (s.hasItem()) {
                int pr = menu.getPrice(i);
                if (pr > 0) {
                    String t = pr + "⛀";
                    g.drawString(font, t, sx + 9 - font.width(t) / 2 + 1, sy + 14, 0x000000, false);
                    g.drawString(font, t, sx + 9 - font.width(t) / 2, sy + 13, 0xFFFFFF, false);
                }
            }
        }

        // Инвентарь игрока и хотбар
        for (int i = 36; i <= 71; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF373737);
        }

        // Зелёная зона покупки
        for (int i = 72; i <= 80; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFFA8D5A8);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF2E4A2E);
        }

        // Красная зона продажи
        for (int i = 81; i <= 89; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0xFFD5A8A8);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 15, 0xFF4A2E2E);
        }

        // Рамка ошибки / успеха
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
        long bal = Math.max(0, menu.getClientBalance());
        long bud = Math.max(0, menu.getClientBudget());
        String ownerName = menu.getOwnerActor() != null ? menu.getOwnerActor().getActorDisplayName() : "Owner";

        // Полупрозрачный фон
        g.fill(168, 4, 286, 28, 0x80000000);

        g.drawString(font, Component.literal("You: " + bal + "⛀"), 170, 6, 0x00FF00, false);
        g.drawString(font, Component.literal(ownerName + ": " + bud + "⛀"), 170, 17, 0xFFD700, false);

        long buyCost = menu.getTotalBuyCost();
        long sellValue = menu.getTotalSellValue();
        long diff = sellValue - buyCost;
        String text;
        int textColor;
        if (diff > 0) { text = "+" + diff + "⛀"; textColor = 0x00FF00; }
        else if (diff < 0) { text = diff + "⛀"; textColor = 0xFF0000; }
        else { text = "0⛀"; textColor = 0xFFFFFF; }
        g.drawString(font, "Итог:", 196, 146, 0xAAAAAA, false);
        g.drawString(font, text, 196 + font.width("Итог: ") + 4, 146, textColor, false);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot == null) { super.slotClicked(null, slotId, mouseButton, type); return; }
        if (slotId < 36) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                PacketDistributor.sendToServer(new ServerboundAddToBuySlotPacket(slotId, type == ClickType.QUICK_MOVE));
            }
            return;
        }
        if (slotId >= 72 && slotId <= 80) {
            if (slot.hasItem() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                PacketDistributor.sendToServer(new ServerboundRemoveFromBuySlotPacket(slotId - 72));
            }
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (failedAnimationTick > 0) { failedAnimationTick--; if (failedAnimationTick == 0) transactionFailed = false; }
        if (successAnimationTick > 0) { successAnimationTick--; if (successAnimationTick == 0) transactionSuccess = false; }
        updateSlotAnimations();
    }

    private void updateSlotAnimations() {
        slotAnimations.entrySet().removeIf(entry -> {
            float current = entry.getValue();
            Float target = slotAnimationTargets.get(entry.getKey());
            if (target == null) return true;
            float newValue = current + (target - current) * 0.3f;
            if (Math.abs(newValue - target) < 0.01f) {
                entry.setValue(target);
                slotAnimationTargets.remove(entry.getKey());
                return target == 1.0f;
            }
            entry.setValue(newValue);
            return false;
        });
    }

    // Публичные методы для обратной связи из пакетов
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
}