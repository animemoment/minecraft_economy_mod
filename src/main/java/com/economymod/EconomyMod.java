package com.economymod;

import com.economymod.client.ClientEventHandler;
import com.economymod.command.CommandRegistry;
import com.economymod.command.GiveBudgetCommand;
import com.economymod.command.VillagerTradeCommand;
import com.economymod.entity.ai.VillagerP2PTradeGoal;
import com.economymod.event.ServerEvents;
import com.economymod.event.VillagerInteractionHandler;
import com.economymod.registry.ModAttachments;
import com.economymod.registry.ModEntities;
import com.economymod.registry.ModMenus;
import com.economymod.economy.EconomyManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

@Mod(EconomyMod.MODID)
public class EconomyMod {
    public static final String MODID = "economymod";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile EconomyManager economyManager;

    public EconomyMod(IEventBus modEventBus) {
        LOGGER.info("=== EconomyMod: loading ===");

        // Регистрация реестров
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);

        // Слушатели модовой шины
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ClientEventHandler::registerScreens);
        modEventBus.addListener(ClientEventHandler::registerEntityRenderers);

        // Регистрация на главной шине NeoForge
        NeoForge.EVENT_BUS.register(CommandRegistry.class);
        NeoForge.EVENT_BUS.register(VillagerTradeCommand.class);
        NeoForge.EVENT_BUS.register(GiveBudgetCommand.class);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        NeoForge.EVENT_BUS.register(VillagerInteractionHandler.class);

        // Регистрируем этот класс (EconomyMod) для работы @SubscribeEvent
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("=== EconomyMod: loaded ===");
    }

    /**
     * Вызывается, когда сущность появляется в мире.
     * Добавляет жителям AI торговли и инициализирует их инвентарь.
     */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Villager villager && !event.getLevel().isClientSide) {
            // Устанавливаем приоритет 1, чтобы торговля была важнее прогулок
            villager.goalSelector.addGoal(1, new VillagerP2PTradeGoal(villager));

            var att = villager.getData(ModAttachments.VILLAGER.get());
            if (att != null) {
                att.ensureInitialized();
            }
        }
    }

    /**
     * Тики уровня на сервере.
     */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // Движение виртуальных торговцев
            com.economymod.economy.systems.VirtualTraderManager.tick(serverLevel);

            // Работа менеджера экономики (пересчет цен)
            if (economyManager != null) {
                economyManager.tick();
            }
        }
    }

    public static EconomyManager getEconomyManager() {
        return economyManager;
    }

    public static void setEconomyManager(EconomyManager manager) {
        economyManager = manager;
    }
}