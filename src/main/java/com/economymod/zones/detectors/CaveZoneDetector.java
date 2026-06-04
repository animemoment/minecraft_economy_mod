package com.economymod.zones.detectors;

import com.economymod.zones.ZoneInstance;
import com.economymod.zones.ZoneRegistry;
import com.economymod.zones.ZoneType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;

public class CaveZoneDetector extends ZoneDetector {

    public CaveZoneDetector() {
        super(ZoneType.CAVE);
    }

    @Override
    public void scanChunk(ServerLevel level, ChunkPos chunkPos) {
        ZoneRegistry registry = ZoneRegistry.get(level);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        // Сканируем подземное пространство под Y=40 на наличие полостей (воздуха в глубине)
        for (int x = 0; x < 16; x += 2) { // С шагом 2 для высокой скорости
            for (int z = 0; z < 16; z += 2) {
                for (int y = -64; y < 40; y += 4) {
                    mutablePos.set(minX + x, y, minZ + z);
                    BlockState state = level.getBlockState(mutablePos);

                    if (state.isAir()) {
                        BlockPos pos = mutablePos.immutable();
                        if (registry.getZonesAt(pos, ZoneType.CAVE).isEmpty()) {
                            // Запускаем Flood Fill для подземной пещеры!
                            tryDetectAndRegisterCave(level, pos, registry);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBlockChanged(ServerLevel level, BlockPos pos) {
        // Если игрок застроил пещеру (поставил камень/землю в воздух пещеры)
        if (pos.getY() < 40) {
            ZoneRegistry registry = ZoneRegistry.get(level);
            Set<ZoneInstance> caves = registry.getZonesAt(pos, ZoneType.CAVE);

            for (ZoneInstance cave : caves) {
                // Удаляем зону пещеры из реестра, так как она засыпана/изменена
                registry.deregister(cave.getId());
            }
        }
    }

    private void tryDetectAndRegisterCave(ServerLevel level, BlockPos startAir, ZoneRegistry registry) {
        Set<Long> caveBlocks = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(startAir);

        // Flood Fill собирает пустые пространства (воздух) под землей
        while (!queue.isEmpty() && caveBlocks.size() < 1000) { // Пещеры могут быть большими (до 1000 блоков)
            BlockPos current = queue.poll();
            if (caveBlocks.contains(current.asLong())) continue;

            ChunkAccess chunk = level.getChunkSource().getChunkNow(current.getX() >> 4, current.getZ() >> 4);
            if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FULL) continue;

            BlockState state = level.getBlockState(current);
            if (!state.isAir()) continue; // Нам нужен только воздух под землей

            caveBlocks.add(current.asLong());

            // Ищем полости во всех 6 направлениях
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (neighbor.getY() < 40 && !caveBlocks.contains(neighbor.asLong())) {
                    queue.add(neighbor);
                }
            }
        }

        // Если нашли полость объемом более 20 блоков воздуха под землей — это пещера!
        if (caveBlocks.size() >= 20) {
            ZoneInstance caveZone = new ZoneInstance(ZoneType.CAVE, caveBlocks);
            registry.register(caveZone);
        }
    }
}