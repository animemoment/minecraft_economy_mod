package com.economymod.registry;

import com.economymod.EconomyMod;
import com.economymod.entity.EconomyTraderEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EconomyMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<EconomyTraderEntity>>
            ECONOMY_TRADER = ENTITY_TYPES.register("economy_trader", () ->
            EntityType.Builder.of(EconomyTraderEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("economy_trader"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ECONOMY_TRADER.get(), EconomyTraderEntity.createAttributes().build());
    }
}