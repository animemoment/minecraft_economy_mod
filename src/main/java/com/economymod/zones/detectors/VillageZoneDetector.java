package com.economymod.zones.detectors;

import com.economymod.world.VillageNetworkData;
import com.economymod.zones.ZoneInstance;
import com.economymod.zones.ZoneRegistry;
import com.economymod.zones.ZoneType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.Set;

public class VillageZoneDetector extends ZoneDetector {

    public VillageZoneDetector() {
        super(ZoneType.VILLAGE);
    }

    @Override
    public void scanChunk(ServerLevel level, ChunkPos chunkPos) {
        ZoneRegistry registry = ZoneRegistry.get(level);
        VillageNetworkData networkData = VillageNetworkData.get(level);

        for (VillageNetworkData.VillageInfo village : networkData.getAllVillages()) {
            BlockPos center = village.getCenter();

            double distToChunk = center.distSqr(chunkPos.getMiddleBlockPosition(64));
            if (distToChunk <= 80 * 80) { // Ищем дома в радиусе 80 блоков
                tryRegisterVillageZone(level, center, registry);
            }
        }
    }

    @Override
    public void onBlockChanged(ServerLevel level, BlockPos pos) {
        // Изменение блоков динамически пересчитывается при вызове сканирования чанка
    }

    private void tryRegisterVillageZone(ServerLevel level, BlockPos center, ZoneRegistry registry) {
        // Предотвращаем дублирование
        for (ZoneInstance zone : registry.getAllZones()) {
            if (zone.getType() == ZoneType.VILLAGE && zone.getBounds().contains(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D)) {
                return;
            }
        }

        Set<Long> villageBlocks = new HashSet<>();

        // 1. Очерчиваем площадь собраний вокруг колокола (небольшой радиус 12 блоков)
        addAreaAround(level, center, 12, villageBlocks);

        // Наборы для домов и точечных блоков (тропинки, грядки)
        Set<BlockPos> housePoints = new HashSet<>();
        Set<BlockPos> directVillageBlocks = new HashSet<>();

        // 2. Сканируем область деревни
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-64, -16, -64), center.offset(64, 16, 64))) {
            BlockState state = level.getBlockState(pos);

            // Если нашли кровать или рабочий стол жителя (печь, верстак, картограф, коптильня и т.д.)
            if (state.is(BlockTags.BEDS) || isWorkstation(state)) {
                housePoints.add(pos.immutable());
            }
            // ИСПРАВЛЕНО: Если это грядка или тропинка — запоминаем её для точечного добавления!
            else if (state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) {
                directVillageBlocks.add(pos.immutable());
            }
        }

        // 3. Вокруг каждого дома очерчиваем зону радиусом 10 блоков
        for (BlockPos housePoint : housePoints) {
            addAreaAround(level, housePoint, 10, villageBlocks);
        }

        // 4. ИСПРАВЛЕНО: Добавляем блоки тропинок и грядок строго по их контуру
        for (BlockPos directPos : directVillageBlocks) {
            BlockState blockState = level.getBlockState(directPos);

            // Исключаем воду (на всякий случай)
            if (!blockState.is(Blocks.WATER) && blockState.getFluidState().isEmpty()) {
                villageBlocks.add(directPos.asLong());

                // Для грядок захватываем еще и блок воздуха/посевов над ними, чтобы сетка покрывала растения
                if (blockState.is(Blocks.FARMLAND)) {
                    villageBlocks.add(directPos.above().asLong());
                }
            }
        }

        if (!villageBlocks.isEmpty()) {
            ZoneInstance villageZone = new ZoneInstance(ZoneType.VILLAGE, villageBlocks);
            registry.register(villageZone);
        }
    }

    /**
     * Вспомогательный метод: очерчивает плоскую зону вокруг точки, игнорируя воду и крутые горы
     */
    private void addAreaAround(ServerLevel level, BlockPos point, int radius, Set<Long> blocksSet) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    BlockPos targetPos = point.offset(dx, 0, dz);
                    BlockPos posOnSurface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetPos);

                    // БЕЗОПАСНОСТЬ: Исключаем воду из зоны деревни
                    BlockState surfaceState = level.getBlockState(posOnSurface.below());
                    if (surfaceState.is(Blocks.WATER) || !surfaceState.getFluidState().isEmpty()) {
                        continue;
                    }

                    // Перепад высот: зона плавно ложится на холмики, но не лезет на крутые скалы (разница высот <= 5 блоков)
                    int heightDifference = Math.abs(posOnSurface.getY() - point.getY());
                    if (heightDifference <= 5) {
                        blocksSet.add(posOnSurface.below().asLong());
                    }
                }
            }
        }
    }

    private boolean isWorkstation(BlockState state) {
        return state.is(Blocks.FLETCHING_TABLE) ||
                state.is(Blocks.COMPOSTER) ||
                state.is(Blocks.BLAST_FURNACE) ||
                state.is(Blocks.SMOKER) ||
                state.is(Blocks.FURNACE) ||
                state.is(Blocks.CARTOGRAPHY_TABLE) ||
                state.is(Blocks.LECTERN) ||
                state.is(Blocks.LOOM) ||
                state.is(Blocks.GRINDSTONE) ||
                state.is(Blocks.SMITHING_TABLE) ||
                state.is(Blocks.BREWING_STAND) ||
                state.is(Blocks.CAULDRON) ||
                state.is(Blocks.STONECUTTER);
    }
}