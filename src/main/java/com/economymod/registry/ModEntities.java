package com.economymod.registry;

import com.economymod.EconomyMod;
import com.economymod.entity.EconomyTraderEntity;
import com.economymod.entity.VillageGuardEntity;
import com.economymod.entity.TestCreatureEntity; // ← ДОБАВИТЬ ИМПОРТ
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

    public static final DeferredHolder<EntityType<?>, EntityType<VillageGuardEntity>>
            VILLAGE_GUARD = ENTITY_TYPES.register("village_guard", () ->
            EntityType.Builder.of(VillageGuardEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("village_guard"));

    // Новая тестовая сущность
    public static final DeferredHolder<EntityType<?>, EntityType<TestCreatureEntity>>
            TEST_CREATURE = ENTITY_TYPES.register("test_creature", () ->
            EntityType.Builder.of(TestCreatureEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(10)
                    .build("test_creature"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ECONOMY_TRADER.get(), EconomyTraderEntity.createAttributes().build());
        event.put(VILLAGE_GUARD.get(), VillageGuardEntity.createAttributes().build());
        event.put(TEST_CREATURE.get(), TestCreatureEntity.createAttributes().build()); // ← добавить
    }
}