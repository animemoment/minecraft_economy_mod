package com.economymod.zones;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ZoneInstance {
    private final UUID id;
    private final ZoneType type;
    private final Set<Long> positions;
    private final AABB bounds;
    private final CompoundTag metadata;
    private ZoneOrigin origin;

    public ZoneInstance(ZoneType type, Set<Long> positions) {
        this(UUID.randomUUID(), type, positions, new CompoundTag(), ZoneOrigin.NATURAL);
    }

    public ZoneInstance(UUID id, ZoneType type, Set<Long> positions, CompoundTag metadata) {
        this(id, type, positions, metadata, ZoneOrigin.NATURAL);
    }

    public ZoneInstance(UUID id, ZoneType type, Set<Long> positions, CompoundTag metadata, ZoneOrigin origin) {
        this.id = id;
        this.type = type;
        this.positions = positions;
        this.metadata = metadata != null ? metadata : new CompoundTag();
        this.origin = origin;
        this.bounds = calculateBounds(positions);
    }

    private AABB calculateBounds(Set<Long> positions) {
        if (positions.isEmpty()) return new AABB(0, 0, 0, 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long posLong : positions) {
            BlockPos pos = BlockPos.of(posLong);
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    public UUID getId() { return id; }
    public ZoneType getType() { return type; }
    public Set<Long> getPositions() { return positions; }
    public AABB getBounds() { return bounds; }
    public CompoundTag getMetadata() { return metadata; }
    public ZoneOrigin getOrigin() { return origin; }
    public void setOrigin(ZoneOrigin origin) { this.origin = origin; }

    public boolean contains(BlockPos pos) {
        if (!bounds.contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) return false;
        return positions.contains(pos.asLong());
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.name());
        long[] posArray = new long[positions.size()];
        int i = 0;
        for (long pos : positions) posArray[i++] = pos;
        tag.putLongArray("Positions", posArray);
        tag.put("Metadata", metadata.copy());
        tag.putString("Origin", origin.name());
        return tag;
    }

    public static ZoneInstance deserialize(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        ZoneType type = ZoneType.valueOf(tag.getString("Type"));
        long[] posArray = tag.getLongArray("Positions");
        Set<Long> positions = new HashSet<>(posArray.length);
        for (long pos : posArray) positions.add(pos);
        CompoundTag metadata = tag.getCompound("Metadata");
        ZoneOrigin origin = tag.contains("Origin")
                ? ZoneOrigin.valueOf(tag.getString("Origin"))
                : ZoneOrigin.NATURAL;
        return new ZoneInstance(id, type, positions, metadata, origin);
    }
}