package com.economymod.registry;

import com.economymod.EconomyMod;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModProfessions {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, EconomyMod.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, EconomyMod.MODID);

    private static final ImmutableSet<net.minecraft.world.level.block.state.BlockState> TARGET_STATES =
            ImmutableSet.copyOf(Blocks.TARGET.getStateDefinition().getPossibleStates());

    public static final DeferredHolder<PoiType, PoiType> GUARD_POI = POI_TYPES.register("guard_poi",
            () -> new PoiType(TARGET_STATES, 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> GUARD = PROFESSIONS.register("guard",
            () -> new VillagerProfession("guard",
                    holder -> holder.is(GUARD_POI.getKey()),
                    holder -> holder.is(GUARD_POI.getKey()),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    net.minecraft.sounds.SoundEvents.VILLAGER_WORK_WEAPONSMITH));
}