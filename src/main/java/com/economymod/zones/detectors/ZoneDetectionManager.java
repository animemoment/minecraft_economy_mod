package com.economymod.zones.detectors;

import com.economymod.EconomyMod;
import com.economymod.zones.ZoneRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = EconomyMod.MODID)
public class ZoneDetectionManager {
    private static final List<ZoneDetector> DETECTORS = new ArrayList<>();

    static {
        DETECTORS.add(new TreeZoneDetector());
        DETECTORS.add(new CaveZoneDetector());
        DETECTORS.add(new VillageZoneDetector()); // Зарегистрировали детектор деревни
    }

    public static List<ZoneDetector> getDetectors() {
        return DETECTORS;
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (level.getGameTime() < 100) {
            return;
        }

        ChunkAccess chunk = event.getChunk();
        if (chunk.getPersistedStatus() == ChunkStatus.FULL) {
            ChunkPos chunkPos = chunk.getPos();
            ZoneRegistry registry = ZoneRegistry.get(level);

            if (registry.isChunkScanned(chunkPos)) {
                return;
            }

            registry.markChunkAsScanned(chunkPos);

            for (ZoneDetector detector : DETECTORS) {
                try {
                    detector.scanChunk(level, chunkPos);
                } catch (Exception e) {
                    EconomyMod.LOGGER.error("Ошибка при сканировании чанка {} детектором {}",
                            chunkPos, detector.getClass().getSimpleName(), e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        for (ZoneDetector detector : DETECTORS) {
            try {
                detector.onBlockChanged(level, pos);
            } catch (Exception e) {
                EconomyMod.LOGGER.error("Ошибка при обработке изменения блока в {} детектором {}",
                        pos, detector.getClass().getSimpleName(), e);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        for (ZoneDetector detector : DETECTORS) {
            try {
                detector.onBlockChanged(level, pos);
            } catch (Exception e) {
                EconomyMod.LOGGER.error("Ошибка при обработке установки блока в {} детектором {}",
                        pos, detector.getClass().getSimpleName(), e);
            }
        }
    }
}