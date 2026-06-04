package com.economymod.zones;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneRegistry {
    private static final Map<ServerLevel, ZoneRegistry> REGISTRIES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final Map<UUID, ZoneInstance> allZones = new ConcurrentHashMap<>();

    // Пространственный индекс: Чанк -> Список зон, которые его пересекают или находятся в нём
    private final Map<ChunkPos, Set<ZoneInstance>> chunkIndex = new ConcurrentHashMap<>();

    // Набор уже просканированных чанков (для исключения повторного сканирования)
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();

    // Игроки, у которых сейчас включен режим отладки зон на этом уровне
    private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    // Прямая ссылка на родительский контейнер сохранения (для исключения дедлока)
    private ZoneSavedData savedData;

    ZoneRegistry(ServerLevel level) {
        this.level = level;
    }

    public void setSavedData(ZoneSavedData savedData) {
        this.savedData = savedData;
    }

    public static ZoneRegistry get(ServerLevel level) {
        return REGISTRIES.computeIfAbsent(level, l -> {
            // Инициализация при первом обращении. Автоматически загружает данные с диска.
            ZoneSavedData savedData = ZoneSavedData.get(l);
            return savedData.getRegistry();
        });
    }

    public static void unload(ServerLevel level) {
        REGISTRIES.remove(level);
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Set<UUID> getDebugPlayers() {
        return debugPlayers;
    }

    /**
     * Включает или выключает режим дебага зон для конкретного игрока и синхронизирует зоны.
     */
    public void toggleDebugPlayer(UUID playerUUID, ServerPlayer player) {
        if (debugPlayers.contains(playerUUID)) {
            debugPlayers.remove(playerUUID);
            // Шлем пакет на очистку всех зон на клиенте
            for (ZoneInstance zone : allZones.values()) {
                long[] posArray = new long[zone.getPositions().size()];
                int idx = 0;
                for (long p : zone.getPositions()) posArray[idx++] = p;

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.economymod.network.ClientboundZoneSyncPacket(zone.getId(), zone.getType().name(), posArray, true));
            }
        } else {
            debugPlayers.add(playerUUID);
            // Шлем пакеты синхронизации всех текущих зон игроку
            for (ZoneInstance zone : allZones.values()) {long[] posArray = new long[zone.getPositions().size()];
                int idx = 0;
                for (long p : zone.getPositions()) posArray[idx++] = p;

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.economymod.network.ClientboundZoneSyncPacket(zone.getId(), zone.getType().name(), posArray, false));
            }
        }
    }

    public synchronized void register(ZoneInstance zone) {
        if (zone == null) return;
        allZones.put(zone.getId(), zone);

        // Индексируем зону по всем чанкам, которые пересекает её AABB
        AABB bounds = zone.getBounds();
        int minChunkX = ((int) bounds.minX) >> 4;
        int maxChunkX = ((int) bounds.maxX) >> 4;
        int minChunkZ = ((int) bounds.minZ) >> 4;
        int maxChunkZ = ((int) bounds.maxZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                chunkIndex.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet()).add(zone);
            }
        }

        // Отправляем пакет всем дебагерам на этом уровне
        if (!debugPlayers.isEmpty()) {
            long[] posArray = new long[zone.getPositions().size()];
            int idx = 0;
            for (long p : zone.getPositions()) posArray[idx++] = p;

            com.economymod.network.ClientboundZoneSyncPacket syncPacket =
                    new com.economymod.network.ClientboundZoneSyncPacket(zone.getId(), zone.getType().name(), posArray, false);

            for (UUID playerUUID : debugPlayers) {
                net.minecraft.world.entity.player.Player p = level.getPlayerByUUID(playerUUID);
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, syncPacket);
                }
            }
        }

        // Отправляем событие создания зоны в шину NeoForge
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.economymod.zones.events.ZoneCreatedEvent(level, zone));

        markDirty();
    }

    public synchronized void deregister(UUID id) {
        ZoneInstance zone = allZones.get(id);
        if (zone == null) return;

        // Отправляем пакет удаления всем дебагерам на этом уровне
        if (!debugPlayers.isEmpty()) {
            long[] posArray = new long[zone.getPositions().size()];
            int idx = 0;
            for (long p : zone.getPositions()) posArray[idx++] = p;

            com.economymod.network.ClientboundZoneSyncPacket syncPacket =
                    new com.economymod.network.ClientboundZoneSyncPacket(zone.getId(), zone.getType().name(), posArray, true);

            for (UUID playerUUID : debugPlayers) {
                net.minecraft.world.entity.player.Player p = level.getPlayerByUUID(playerUUID);
                if (p instanceof ServerPlayer sp) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, syncPacket);
                }
            }
        }

        // Отправляем событие ДО удаления, чтобы потребители могли прочитать данные зоны
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.economymod.zones.events.ZoneRemovedEvent(level, zone));

        allZones.remove(id);

        // Удаляем зону из пространственного индекса чанков
        AABB bounds = zone.getBounds();
        int minChunkX = ((int) bounds.minX) >> 4;
        int maxChunkX = ((int) bounds.maxX) >> 4;
        int minChunkZ = ((int) bounds.minZ) >> 4;
        int maxChunkZ = ((int) bounds.maxZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                Set<ZoneInstance> chunkZones = chunkIndex.get(chunkPos);
                if (chunkZones != null) {
                    chunkZones.remove(zone);
                    if (chunkZones.isEmpty()) {
                        chunkIndex.remove(chunkPos);
                    }
                }
            }
        }

        markDirty();
    }

    public Collection<ZoneInstance> getAllZones() {
        return Collections.unmodifiableCollection(allZones.values());
    }

    public ZoneInstance getZone(UUID id) {
        return allZones.get(id);
    }

    // --- Управление кэшем просканированных чанков ---

    public boolean isChunkScanned(ChunkPos pos) {
        return scannedChunks.contains(pos);
    }

    public void markChunkAsScanned(ChunkPos pos) {
        scannedChunks.add(pos);
        markDirty();
    }

    public void clearScannedChunks() {
        scannedChunks.clear();
        markDirty();
    }

    public Set<ChunkPos> getScannedChunks() {
        return Collections.unmodifiableSet(scannedChunks);
    }

    // --- Query API ---

    /**
     * Возвращает все зоны, пересекающие указанную позицию блока.
     */
    public Set<ZoneInstance> getZonesAt(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Set<ZoneInstance> possibleZones = chunkIndex.get(chunkPos);
        if (possibleZones == null || possibleZones.isEmpty()) {
            return Collections.emptySet();
        }

        Set<ZoneInstance> result = new HashSet<>();
        for (ZoneInstance zone : possibleZones) {
            if (zone.contains(pos)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Возвращает все зоны определенного типа, пересекающие указанную позицию блока.
     */
    public Set<ZoneInstance> getZonesAt(BlockPos pos, ZoneType type) {
        Set<ZoneInstance> zones = getZonesAt(pos);
        if (zones.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ZoneInstance> result = new HashSet<>();
        for (ZoneInstance zone : zones) {
            if (zone.getType() == type) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Быстрая проверка: находится ли позиция внутри зоны определенного типа.
     */
    public boolean isInsideType(BlockPos pos, ZoneType type) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Set<ZoneInstance> possibleZones = chunkIndex.get(chunkPos);
        if (possibleZones == null || possibleZones.isEmpty()) {
            return false;
        }
        for (ZoneInstance zone : possibleZones) {
            if (zone.getType() == type && zone.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Находит все зоны в радиусе вокруг позиции.
     */
    public Set<ZoneInstance> getZonesNear(BlockPos pos, double radius) {
        AABB searchBox = new AABB(pos).inflate(radius);
        int minChunkX = ((int) searchBox.minX) >> 4;
        int maxChunkX = ((int) searchBox.maxX) >> 4;
        int minChunkZ = ((int) searchBox.minZ) >> 4;
        int maxChunkZ = ((int) searchBox.maxZ) >> 4;

        Set<ZoneInstance> result = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Set<ZoneInstance> chunkZones = chunkIndex.get(new ChunkPos(cx, cz));
                if (chunkZones != null) {
                    for (ZoneInstance zone : chunkZones) {
                        if (zone.getBounds().intersects(searchBox)) {
                            result.add(zone);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void markDirty() {
        if (this.savedData != null) {
            this.savedData.setDirty();
        }
    }
}