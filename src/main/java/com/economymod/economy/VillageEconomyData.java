package com.economymod.economy.data;

import com.economymod.economy.VillageEconomy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillageEconomyData extends SavedData {
    private final Map<BlockPos, VillageEconomy> villages = new HashMap<>();
    private final List<VirtualTraveler> travelers = new ArrayList<>();

    public static VillageEconomyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        VillageEconomyData::new,
                        VillageEconomyData::load,
                        null
                ),
                "village_economy"
        );
    }

    public VillageEconomy getOrCreateVillage(BlockPos pos) {
        return villages.computeIfAbsent(pos, k -> new VillageEconomy());
    }

    public List<VirtualTraveler> getTravelers() { return travelers; }
    public Map<BlockPos, VillageEconomy> getVillages() { return villages; }

    public static VillageEconomyData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageEconomyData data = new VillageEconomyData();
        CompoundTag villagesTag = tag.getCompound("Villages");
        for (String key : villagesTag.getAllKeys()) {
            try {
                BlockPos pos = BlockPos.of(Long.parseLong(key));
                data.villages.put(pos, VillageEconomy.load(villagesTag.getCompound(key)));
            } catch (Exception ignored) {}
        }

        ListTag travelersTag = tag.getList("Travelers", Tag.TAG_COMPOUND);
        for (int i = 0; i < travelersTag.size(); i++) {
            data.travelers.add(VirtualTraveler.load(travelersTag.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag villagesTag = new CompoundTag();
        villages.forEach((pos, economy) -> {
            villagesTag.put(String.valueOf(pos.asLong()), economy.save(new CompoundTag()));
        });
        tag.put("Villages", villagesTag);

        ListTag travelersTag = new ListTag();
        for (VirtualTraveler traveler : travelers) {
            travelersTag.add(traveler.save());
        }
        tag.put("Travelers", travelersTag);
        return tag;
    }

    public record VirtualTraveler(CompoundTag nbt, BlockPos destination, long arrivalTick) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("EntityData", nbt);
            tag.putLong("Dest", destination.asLong());
            tag.putLong("Arrival", arrivalTick);
            return tag;
        }
        public static VirtualTraveler load(CompoundTag tag) {
            return new VirtualTraveler(
                    tag.getCompound("EntityData"),
                    BlockPos.of(tag.getLong("Dest")),
                    tag.getLong("Arrival")
            );
        }
    }
}