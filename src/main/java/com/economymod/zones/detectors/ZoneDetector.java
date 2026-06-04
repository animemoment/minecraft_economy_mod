package com.economymod.zones.detectors;

import com.economymod.zones.ZoneType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Set;

public abstract class ZoneDetector {
    private final ZoneType targetType;

    protected ZoneDetector(ZoneType targetType) {
        this.targetType = targetType;
    }

    public ZoneType getTargetType() {
        return targetType;
    }

    public abstract void scanChunk(ServerLevel level, ChunkPos chunkPos);
    public abstract void onBlockChanged(ServerLevel level, BlockPos pos);

    /** Является ли блок искусственным (не встречается в природной генерации ландшафта). */
    public static boolean isArtificial(BlockState state) {
        if (state.isAir()) return false;
        if (state.hasBlockEntity()) return true;

        if (state.is(BlockTags.PLANKS) ||
                state.is(BlockTags.WOODEN_STAIRS) ||
                state.is(BlockTags.WOODEN_SLABS) ||
                state.is(BlockTags.WOODEN_FENCES) ||
                state.is(BlockTags.WOODEN_DOORS) ||
                state.is(BlockTags.WOODEN_TRAPDOORS) ||
                state.is(BlockTags.STANDING_SIGNS) ||
                state.is(BlockTags.WALL_SIGNS)) return true;

        if (state.is(BlockTags.STONE_BRICKS) ||
                state.is(BlockTags.STAIRS) ||
                state.is(BlockTags.SLABS) ||
                state.is(BlockTags.WALLS) ||
                state.is(BlockTags.FENCES) ||
                state.is(BlockTags.FENCE_GATES)) return true;

        if (state.is(BlockTags.CANDLES) ||
                state.is(BlockTags.CANDLE_CAKES) ||
                state.is(Blocks.TORCH) ||
                state.is(Blocks.WALL_TORCH) ||
                state.is(Blocks.LANTERN) ||
                state.is(Blocks.SOUL_LANTERN) ||
                state.is(Blocks.SEA_LANTERN) ||
                state.is(Blocks.GLOWSTONE)) return true;

        if (state.is(BlockTags.BUTTONS) ||
                state.is(BlockTags.PRESSURE_PLATES) ||
                state.is(BlockTags.RAILS) ||
                state.is(Blocks.PISTON) ||
                state.is(Blocks.STICKY_PISTON) ||
                state.is(Blocks.DISPENSER) ||
                state.is(Blocks.DROPPER) ||
                state.is(Blocks.OBSERVER) ||
                state.is(Blocks.HOPPER) ||
                state.is(Blocks.TNT)) return true;

        if (state.is(Blocks.GLASS) ||
                state.is(Blocks.GLASS_PANE) ||
                state.is(Blocks.TINTED_GLASS) ||
                state.is(BlockTags.IMPERMEABLE)) return true;

        if (state.is(BlockTags.BEDS) ||
                state.is(Blocks.NOTE_BLOCK) ||
                state.is(Blocks.JUKEBOX) ||
                state.is(BlockTags.BANNERS)) return true;

        if (state.is(BlockTags.TERRACOTTA) ||
                state.is(BlockTags.WOOL) ||
                state.is(Blocks.WHITE_CONCRETE) ||
                state.is(Blocks.IRON_BARS) ||
                state.is(Blocks.CHAIN) ||
                state.is(Blocks.LIGHTNING_ROD) ||
                state.is(Blocks.LADDER) ||
                state.is(Blocks.SCAFFOLDING)) return true;

        if (state.is(Blocks.IRON_TRAPDOOR) ||
                state.is(Blocks.IRON_DOOR)) return true;

        return false;
    }

    /** Проверяет, окружена ли позиция искусственными блоками в заданном радиусе. */
    protected boolean isSurroundedByArtificial(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos check = center.offset(dx, dy, dz);
                    int cx = check.getX() >> 4;
                    int cz = check.getZ() >> 4;
                    ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk != null && chunk.getPersistedStatus() == ChunkStatus.FULL) {
                        if (isArtificial(chunk.getBlockState(check))) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Проверяет, не является ли форма скопления блоков слишком прямоугольной. */
    protected boolean hasRectangularShape(Set<BlockPos> positions, float fillTolerance) {
        if (positions.size() < 16) return false;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : positions) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume == 0) return false;
        return positions.size() / (double) volume > fillTolerance;
    }

    /** Естественная твёрдая поверхность. */
    protected static boolean isNaturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) ||
                state.is(BlockTags.SAND) ||
                state.is(BlockTags.BASE_STONE_OVERWORLD) ||
                state.is(BlockTags.BASE_STONE_NETHER) ||
                state.is(Blocks.GRAVEL) ||
                state.is(Blocks.CLAY) ||
                state.is(Blocks.MOSS_BLOCK) ||
                state.is(Blocks.DRIPSTONE_BLOCK);
    }
}