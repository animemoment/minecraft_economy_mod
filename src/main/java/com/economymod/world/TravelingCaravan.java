package com.economymod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public class TravelingCaravan {
    private final UUID caravanId;
    private final CompoundTag traderData; // NBT торговца
    private final BlockPos origin;
    private final BlockPos destination;
    private final long departureTime;
    private final long arrivalTime;

    public TravelingCaravan(CompoundTag traderData, BlockPos origin, BlockPos destination, long gameTime) {
        this.caravanId = UUID.randomUUID();
        this.traderData = traderData;
        this.origin = origin;
        this.destination = destination;
        this.departureTime = gameTime;
        // Время в пути: 1 секунда на каждый блок расстояния (можно настроить)
        long travelTime = (long) Math.sqrt(origin.distSqr(destination)) * 20;
        this.arrivalTime = gameTime + travelTime;
    }

    private TravelingCaravan(UUID id, CompoundTag data, BlockPos origin, BlockPos dest, long dep, long arr) {
        this.caravanId = id;
        this.traderData = data;
        this.origin = origin;
        this.destination = dest;
        this.departureTime = dep;
        this.arrivalTime = arr;
    }

    public UUID getCaravanId() { return caravanId; }
    public CompoundTag getTraderData() { return traderData; }
    public BlockPos getOrigin() { return origin; }
    public BlockPos getDestination() { return destination; }
    public long getArrivalTime() { return arrivalTime; }

    public boolean hasArrived(long currentGameTime) {
        return currentGameTime >= arrivalTime;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CaravanId", caravanId);
        tag.put("TraderData", traderData);
        tag.putLong("OriginX", origin.getX());
        tag.putLong("OriginY", origin.getY());
        tag.putLong("OriginZ", origin.getZ());
        tag.putLong("DestX", destination.getX());
        tag.putLong("DestY", destination.getY());
        tag.putLong("DestZ", destination.getZ());
        tag.putLong("Departure", departureTime);
        tag.putLong("Arrival", arrivalTime);
        return tag;
    }

    public static TravelingCaravan load(CompoundTag tag) {
        UUID id = tag.getUUID("CaravanId");
        CompoundTag data = tag.getCompound("TraderData");
        BlockPos origin = new BlockPos(
                tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ")
        );
        BlockPos dest = new BlockPos(
                tag.getInt("DestX"), tag.getInt("DestY"), tag.getInt("DestZ")
        );
        long dep = tag.getLong("Departure");
        long arr = tag.getLong("Arrival");
        return new TravelingCaravan(id, data, origin, dest, dep, arr);
    }
}