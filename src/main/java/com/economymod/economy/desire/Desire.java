package com.economymod.economy.desire;

import net.minecraft.world.item.ItemStack;

public class Desire {
    public enum DesireType {
        CONSUMABLE, // Еда, зелья
        TOOL,       // Инструменты для работы
        MATERIAL,   // Ресурсы для крафта
        UPGRADE,    // Улучшение существующего предмета
        LUXURY      // Предметы роскоши
    }

    public final ItemStack stack;
    public final DesireType type;
    public final int priority; // Чем выше, тем важнее

    public Desire(ItemStack stack, DesireType type, int priority) {
        this.stack = stack;
        this.type = type;
        this.priority = priority;
    }
}