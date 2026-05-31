package com.economymod.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class FreedomAnalyzer {
    private final LivingEntity entity;
    private final Level level;
    private BlockPos startPos;

    private int escapeRadius = 20;
    private int maxHorizontalDistance = 150;      // уменьшено с 250
    private int maxNodes = 10000;                  // уменьшено с 50000

    public FreedomAnalyzer(LivingEntity entity) {
        this.entity = entity;
        this.level = entity.level();
    }

    public void setEscapeRadius(int escapeRadius) { this.escapeRadius = escapeRadius; }
    public void setMaxHorizontalDistance(int maxHorizontalDistance) { this.maxHorizontalDistance = maxHorizontalDistance; }
    public void setMaxNodes(int maxNodes) { this.maxNodes = maxNodes; }

    public Result analyze() {
        startPos = entity.blockPosition();

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> reachablePositions = new HashSet<>();

        queue.add(startPos);
        visited.add(startPos);

        boolean free = false;

        while (!queue.isEmpty() && reachablePositions.size() < maxNodes) {
            BlockPos pos = queue.poll();
            reachablePositions.add(pos);

            int manhattanDist = Math.abs(pos.getX() - startPos.getX()) + Math.abs(pos.getZ() - startPos.getZ());

            if (manhattanDist > maxHorizontalDistance) {
                free = true;
                break;
            }

            if (manhattanDist >= escapeRadius) {
                free = true;
                break;
            }

            for (BlockPos neighbor : getPassableNeighbors(pos)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        if (free) {
            return new Result(true, 0);
        } else {
            Set<Long> footprint = new HashSet<>();
            for (BlockPos p : reachablePositions) {
                if (isValidStanding(p)) {
                    footprint.add(((long)p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL));
                }
            }
            return new Result(false, footprint.size());
        }
    }

    private boolean isValidStanding(BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        if (isSolidBlocking(feet)) return false;
        if (isSolidBlocking(head)) return false;

        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolid() && !isPassableSpecial(below)) return false;

        return true;
    }

    private boolean isSolidBlocking(BlockState state) {
        if (state.isAir()) return false;
        if (isPassableSpecial(state)) return false;
        return state.isSolid();
    }

    private boolean isPassableSpecial(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock ||
                block instanceof TrapDoorBlock ||
                block instanceof FenceGateBlock ||
                block instanceof LadderBlock ||
                block instanceof VineBlock;
    }

    private List<BlockPos> getPassableNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos target = pos.relative(dir, 1).offset(0, dy, 0);
                if (canTraverse(pos, target)) {
                    neighbors.add(target);
                }
            }
        }

        BlockState currentBlock = level.getBlockState(pos);
        if (currentBlock.getBlock() instanceof LadderBlock || currentBlock.getBlock() instanceof VineBlock) {
            for (int dy = -1; dy <= 1; dy += 2) {
                BlockPos target = pos.offset(0, dy, 0);
                if (canTraverseLadder(pos, target)) {
                    neighbors.add(target);
                }
            }
        }

        return neighbors;
    }

    private boolean canTraverse(BlockPos from, BlockPos to) {
        if (!isValidStanding(to)) return false;

        int dy = to.getY() - from.getY();
        if (dy > 1) return false;
        if (dy < -2) return false;

        if (dy == 1) {
            BlockPos headCheck = from.above(2);
            if (isSolidBlocking(level.getBlockState(headCheck))) return false;
        }

        if (dy == 0) {
            BlockPos headPos = to.above();
            if (isSolidBlocking(level.getBlockState(headPos))) return false;
        }

        return true;
    }

    private boolean canTraverseLadder(BlockPos from, BlockPos to) {
        BlockState fromState = level.getBlockState(from);
        BlockState toState = level.getBlockState(to);
        if (!(fromState.getBlock() instanceof LadderBlock) && !(fromState.getBlock() instanceof VineBlock)) return false;
        if (!(toState.getBlock() instanceof LadderBlock) && !(toState.getBlock() instanceof VineBlock)) return false;
        return isValidStanding(to);
    }

    public static class Result {
        public final boolean free;
        public final int area;
        public Result(boolean free, int area) {
            this.free = free;
            this.area = area;
        }
    }
}