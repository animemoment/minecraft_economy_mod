package com.economymod.zones;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public class ZoneSavedData extends SavedData {
    private static final String DATA_NAME = "economymod_zones";
    private final ZoneRegistry registry;

    public ZoneSavedData(ServerLevel level) {
        this.registry = new ZoneRegistry(level);
        this.registry.setSavedData(this); // Связываем сразу
    }

    private ZoneSavedData(ServerLevel level, CompoundTag tag) {
        this.registry = new ZoneRegistry(level);
        this.registry.setSavedData(this); // Связываем ДО десериализации зон!

        // 1. Десериализация зон
        if (tag.contains("Zones", Tag.TAG_LIST)) {
            ListTag zonesList = tag.getList("Zones", Tag.TAG_COMPOUND);
            for (int i = 0; i < zonesList.size(); i++) {
                CompoundTag zoneTag = zonesList.getCompound(i);
                try {
                    ZoneInstance zone = ZoneInstance.deserialize(zoneTag);
                    this.registry.register(zone);
                } catch (Exception e) {
                    com.economymod.EconomyMod.LOGGER.error("Не удалось десериализовать зону на индексе {}", i, e);
                }
            }
        }

        // 2. Десериализация просканированных чанков
        if (tag.contains("ScannedChunks", Tag.TAG_LONG_ARRAY)) {
            long[] scannedArray = tag.getLongArray("ScannedChunks");
            for (long chunkLong : scannedArray) {
                this.registry.markChunkAsScanned(new ChunkPos(chunkLong));
            }
        }
    }

    public ZoneRegistry getRegistry() {
        return registry;
    }

    public static ZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new ZoneSavedData(level),
                        (tag, provider) -> new ZoneSavedData(level, tag)
                ),
                DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // 1. Сохранение зон
        ListTag zonesList = new ListTag();
        for (ZoneInstance zone : registry.getAllZones()) {
            zonesList.add(zone.serialize());
        }
        tag.put("Zones", zonesList);

        // 2. Сохранение просканированных чанков
        java.util.Set<ChunkPos> scanned = registry.getScannedChunks();
        long[] scannedArray = new long[scanned.size()];
        int i = 0;
        for (ChunkPos chunk : scanned) {
            scannedArray[i++] = chunk.toLong();
        }
        tag.putLongArray("ScannedChunks", scannedArray);

        return tag;
    }
}