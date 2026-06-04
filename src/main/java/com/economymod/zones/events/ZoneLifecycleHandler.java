    package com.economymod.zones.events;

    import com.economymod.zones.ZoneRegistry;
    import net.minecraft.server.level.ServerLevel;
    import net.neoforged.bus.api.SubscribeEvent;
    import net.neoforged.fml.common.EventBusSubscriber;
    import net.neoforged.neoforge.event.level.LevelEvent;

    @EventBusSubscriber(modid = com.economymod.EconomyMod.MODID)
    public class ZoneLifecycleHandler {

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                // Выгружаем реестр данного уровня из оперативной памяти
                ZoneRegistry.unload(serverLevel);
                com.economymod.EconomyMod.LOGGER.info("Реестр зон успешно выгружен для измерения: {}",
                        serverLevel.dimension().location());
            }
        }
    }