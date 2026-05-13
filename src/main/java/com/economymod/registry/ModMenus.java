package com.economymod.registry;

import com.economymod.EconomyMod;
import com.economymod.gui.menu.EconomyTradeMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, EconomyMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<EconomyTradeMenu>>
            ECONOMY_TRADE_MENU = MENU_TYPES.register("economy_trade",
            () -> new MenuType<>(EconomyTradeMenu::new, FeatureFlags.DEFAULT_FLAGS));
}