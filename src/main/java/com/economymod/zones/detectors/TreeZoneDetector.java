package com.economymod.zones.detectors;

import com.economymod.zones.ZoneInstance;
import com.economymod.zones.ZoneRegistry;
import com.economymod.zones.ZoneType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;

public class TreeZoneDetector extends ZoneDetector {

    public TreeZoneDetector() {
        super(ZoneType.TREE);
    }

    @Override
    public void scanChunk(ServerLevel level, ChunkPos chunkPos) {
        ZoneRegistry registry = ZoneRegistry.get(level);

        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 60; y < 120; y++) {
                    mutablePos.set(minX + x, y, minZ + z);

                    BlockState state = level.getBlockState(mutablePos);
                    if (state.is(BlockTags.LOGS)) {
                        BlockPos pos = mutablePos.immutable();
                        if (registry.getZonesAt(pos, ZoneType.TREE).isEmpty()) {
                            tryDetectAndRegisterTree(level, pos, registry);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBlockChanged(ServerLevel level, BlockPos pos) {
        ZoneRegistry registry = ZoneRegistry.get(level);
        BlockState state = level.getBlockState(pos);

        // СЦЕНАРИЙ 1: Игрок или дровосек сломал блок дерева
        if (state.isAir()) {
            Set<ZoneInstance> zones = registry.getZonesAt(pos, ZoneType.TREE);
            for (ZoneInstance zone : zones) {
                // ИСПРАВЛЕНО: Вместо удаления всей зоны удаляем только сломанную координату!
                zone.getPositions().remove(pos.asLong());

                // Проверяем, остались ли бревна в этой зоне дерева
                boolean hasLogsLeft = false;
                for (long pLong : zone.getPositions()) {
                    if (level.getBlockState(BlockPos.of(pLong)).is(BlockTags.LOGS)) {
                        hasLogsLeft = true;
                        break;
                    }
                }

                if (!hasLogsLeft) {
                    // Если все бревна срублены — удаляем всю зону дерева целиком
                    registry.deregister(zone.getId());
                } else {
                    // Если бревна еще есть, просто сохраняем изменения на сервере
                    registry.markChunkAsScanned(new ChunkPos(pos)); // Помечает грязным для сохранения на диск

                    // Шлем точечный пакет отладчикам, чтобы убрать кубик только с этого сломанного блока
                    if (!registry.getDebugPlayers().isEmpty()) {
                        long[] singlePos = new long[]{pos.asLong()};
                        com.economymod.network.ClientboundZoneSyncPacket syncPacket =
                                new com.economymod.network.ClientboundZoneSyncPacket(zone.getId(), zone.getType().name(), singlePos, true);

                        for (UUID playerUUID : registry.getDebugPlayers()) {
                            net.minecraft.world.entity.player.Player p = level.getPlayerByUUID(playerUUID);
                            if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, syncPacket);
                            }
                        }
                    }
                }
            }
            return;
        }

        // СЦЕНАРИЙ 2: Выросло новое дерево или игрок поставил бревно вручную
        if (state.is(BlockTags.LOGS)) {
            if (registry.getZonesAt(pos, ZoneType.TREE).isEmpty()) {
                tryDetectAndRegisterTree(level, pos, registry);
            }
            return;
        }

        // СЦЕНАРИЙ 3: Защита построек. Игрок построил дом рядом с деревом
        if (isManMadeBlock(state)) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos checkPos = pos.offset(dx, dy, dz);
                        Set<ZoneInstance> nearTreeZones = registry.getZonesAt(checkPos, ZoneType.TREE);

                        for (ZoneInstance zone : nearTreeZones) {
                            registry.deregister(zone.getId());
                            com.economymod.EconomyMod.LOGGER.info("Зона дерева {} аннулирована, так как рядом построена конструкция игрока в {}.",
                                    zone.getId(), pos.toShortString());
                        }
                    }
                }
            }
        }
    }

    private void tryDetectAndRegisterTree(ServerLevel level, BlockPos startLog, ZoneRegistry registry) {
        Set<Long> treeBlocks = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(startLog);

        boolean isManMadeDetected = false;
        int logCount = 0;
        int leafCount = 0;

        while (!queue.isEmpty() && treeBlocks.size() < 250) {
            BlockPos current = queue.poll();
            if (treeBlocks.contains(current.asLong())) continue;

            ChunkAccess chunk = level.getChunkSource().getChunkNow(current.getX() >> 4, current.getZ() >> 4);
            if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FULL) {
                continue;
            }

            BlockState state = level.getBlockState(current);
            boolean isLog = state.is(BlockTags.LOGS);
            boolean isLeaf = state.is(BlockTags.LEAVES);

            if (!isLog && !isLeaf) continue;

            treeBlocks.add(current.asLong());
            if (isLog) logCount++;
            if (isLeaf) leafCount++;

            if (checkManMadeSurroundings(level, current)) {
                isManMadeDetected = true;
                break;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        int cx = neighbor.getX() >> 4;
                        int cz = neighbor.getZ() >> 4;

                        ChunkAccess neighborChunk = level.getChunkSource().getChunkNow(cx, cz);
                        if (neighborChunk != null && neighborChunk.getPersistedStatus() == ChunkStatus.FULL) {
                            if (!treeBlocks.contains(neighbor.asLong())) {
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        if (!isManMadeDetected && logCount >= 1 && leafCount >= 2) {
            ZoneInstance treeZone = new ZoneInstance(ZoneType.TREE, treeBlocks);
            registry.register(treeZone);
        }
    }

    private boolean checkManMadeSurroundings(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos check = pos.offset(dx, dy, dz);
                    int chunkX = check.getX() >> 4;
                    int chunkZ = check.getZ() >> 4;

                    ChunkAccess chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FULL) {
                        continue;
                    }

                    BlockState state = chunk.getBlockState(check);
                    if (isManMadeBlock(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isManMadeBlock(BlockState state) {
        if (state.isAir()) return false;
        return state.is(BlockTags.PLANKS) ||
                state.is(BlockTags.STONE_BRICKS) ||
                state.is(BlockTags.FENCES) ||
                state.is(BlockTags.FENCE_GATES) ||
                state.is(BlockTags.WALLS) ||
                state.is(BlockTags.STAIRS) ||
                state.is(BlockTags.SLABS) ||
                state.is(BlockTags.BEDS) ||
                state.is(BlockTags.DOORS) ||
                state.is(BlockTags.TRAPDOORS) ||
                state.is(Blocks.COBBLESTONE) ||
                state.is(Blocks.MOSSY_COBBLESTONE) ||
                state.is(Blocks.GLASS) ||
                state.is(Blocks.GLASS_PANE) ||
                state.is(Blocks.CHEST) ||
                state.is(Blocks.FURNACE) ||
                state.is(Blocks.CRAFTING_TABLE) ||
                state.is(Blocks.LANTERN) ||
                state.is(Blocks.TORCH) ||
                state.is(Blocks.WALL_TORCH) ||
                state.is(Blocks.LADDER);
    }
}