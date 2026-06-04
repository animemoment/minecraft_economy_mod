package com.economymod.zones.detectors;

import com.economymod.zones.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluids;

import java.util.*;

public class WaterZoneDetector extends ZoneDetector {

    public WaterZoneDetector() {
        super(ZoneType.WATER);
    }

    @Override
    public void scanChunk(ServerLevel level, ChunkPos chunkPos) {
        ZoneRegistry registry = ZoneRegistry.get(level);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    pos.set(minX + x, y, minZ + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource()) {
                        BlockPos waterPos = pos.immutable();
                        if (registry.getZonesAt(waterPos, ZoneType.WATER).isEmpty()) {
                            detectAndRegisterWaterBody(level, waterPos, registry, ZoneOrigin.NATURAL);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBlockChanged(ServerLevel level, BlockPos pos) {
        ZoneRegistry registry = ZoneRegistry.get(level);
        Set<ZoneInstance> zones = registry.getZonesAt(pos, ZoneType.WATER);

        // Удаление исчезнувшей воды
        for (ZoneInstance zone : zones) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().is(Fluids.WATER) || !state.getFluidState().isSource()) {
                registry.deregister(zone.getId());
                return;
            }
        }

        // Появление новой воды (ведро/лёд растаял) – регистрируем как PLAYER_CREATED
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(Fluids.WATER) && state.getFluidState().isSource() && zones.isEmpty()) {
            // Если окружена искусственными блоками – это бассейн/пруд, не регистрируем
            if (isSurroundedByArtificial(level, pos, 2)) return;

            detectAndRegisterWaterBody(level, pos, registry, ZoneOrigin.PLAYER_CREATED);
        }
    }

    private void detectAndRegisterWaterBody(ServerLevel level, BlockPos start, ZoneRegistry registry, ZoneOrigin origin) {
        Set<BlockPos> waterBlocks = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty() && waterBlocks.size() < 500) {
            BlockPos current = queue.poll();
            if (waterBlocks.contains(current)) continue;

            ChunkAccess chunk = level.getChunkSource().getChunkNow(current.getX() >> 4, current.getZ() >> 4);
            if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FULL) continue;

            BlockState state = level.getBlockState(current);
            if (!state.getFluidState().is(Fluids.WATER) || !state.getFluidState().isSource()) continue;

            waterBlocks.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        int cx = neighbor.getX() >> 4;
                        int cz = neighbor.getZ() >> 4;
                        ChunkAccess nChunk = level.getChunkSource().getChunkNow(cx, cz);
                        if (nChunk != null && nChunk.getPersistedStatus() == ChunkStatus.FULL) {
                            if (!waterBlocks.contains(neighbor)) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        if (waterBlocks.size() < 10) return;

        // Для NATURAL – дополнительные проверки
        if (origin == ZoneOrigin.NATURAL) {
            BlockPos center = getCenter(waterBlocks);
            if (isSurroundedByArtificial(level, center, 3)) return;
            if (hasRectangularShape(waterBlocks, 0.65f)) return;
            if (!hasNaturalBottom(level, waterBlocks)) return;
        }

        Set<Long> longSet = new HashSet<>();
        for (BlockPos p : waterBlocks) longSet.add(p.asLong());
        ZoneInstance zone = new ZoneInstance(ZoneType.WATER, longSet);
        zone.setOrigin(origin);
        registry.register(zone);
    }

    private BlockPos getCenter(Set<BlockPos> positions) {
        if (positions.isEmpty()) return BlockPos.ZERO;
        long x = 0, y = 0, z = 0;
        for (BlockPos p : positions) {
            x += p.getX();
            y += p.getY();
            z += p.getZ();
        }
        int size = positions.size();
        return new BlockPos((int)(x / size), (int)(y / size), (int)(z / size));
    }

    private boolean hasNaturalBottom(ServerLevel level, Set<BlockPos> waterBlocks) {
        int total = 0, natural = 0;
        for (BlockPos p : waterBlocks) {
            BlockPos below = p.below();
            ChunkAccess chunk = level.getChunkSource().getChunkNow(below.getX() >> 4, below.getZ() >> 4);
            if (chunk != null && chunk.getPersistedStatus() == ChunkStatus.FULL) {
                BlockState state = level.getBlockState(below);
                if (!state.getFluidState().isEmpty()) continue;
                total++;
                if (isNaturalGround(state)) natural++;
            }
        }
        return total > 0 && (double) natural / total >= 0.3;
    }
}