package com.economymod.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.HashMap;
import java.util.Map;

public class LearningSystem {
    private final LivingEntity entity;

    public enum MemoryType { DAMAGE, HUNGER, FATIGUE, BOREDOM, MOB, PLAYER }

    private final Map<MemoryType, Map<String, Float>> memories = new HashMap<>();
    private final Map<MemoryType, Map<String, Long>> lastUpdate = new HashMap<>();
    private final Map<Item, Float> itemFear = new HashMap<>();
    private final Map<Item, Long> itemFearLastUpdate = new HashMap<>();
    private final Map<String, Float> positiveMemory = new HashMap<>();
    private final Map<String, Long> positiveLastUpdate = new HashMap<>();

    private static final float FORGET_RATE = 0.001f;
    private static final long FORGET_DELAY = 24000;

    // Кэш для свободы и контекста
    private long lastFreedomTick = -1;
    private float cachedFreedom = 1f;
    private String cachedContext = "";
    private long lastContextTick = -1;
    private float cachedNovelty = 0.5f;

    // Циркадные ритмы
    private float circadianPhase = 0.5f;
    private long lastCircadianUpdate = -1;
    private static final int CIRCADIAN_UPDATE_INTERVAL = 20;

    public LearningSystem(LivingEntity entity) {
        this.entity = entity;
        for (MemoryType type : MemoryType.values()) {
            memories.put(type, new HashMap<>());
            lastUpdate.put(type, new HashMap<>());
        }
    }

    // ============ ЦИРКАДНЫЕ РИТМЫ ============
    public void updateCircadian(long dayTime) {
        if (dayTime != lastCircadianUpdate) {
            lastCircadianUpdate = dayTime;
            // dayTime от 0 до 24000, нормализуем в 0..1
            float newPhase = (dayTime % 24000) / 24000f;
            // Плавная подстройка внутреннего ритма
            circadianPhase = circadianPhase * 0.95f + newPhase * 0.05f;
        }
    }

    public float getSleepModifier() {
        // Ночь (0.75..1.0 или 0..0.25) → сон усиливается
        boolean isNight = (circadianPhase > 0.75f || circadianPhase < 0.25f);
        return isNight ? 1.5f : 0.5f;
    }

    public float getActivityModifier() {
        // День (0.25..0.75) → активность выше
        boolean isDay = (circadianPhase > 0.25f && circadianPhase < 0.75f);
        return isDay ? 1.2f : 0.7f;
    }

    // ============ КЭШИРОВАННЫЙ КОНТЕКСТ ============
    public String getCurrentContext() {
        long now = entity.level().getGameTime();
        if (now - lastContextTick > 40) {
            lastContextTick = now;
            cachedContext = computeContext();
        }
        return cachedContext;
    }

    private String computeContext() {
        if (entity == null) return "unknown";
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();

        float temp = level.getBiome(pos).value().getBaseTemperature();
        String biomeType = temp < 0.2f ? "cold" : (temp > 0.8f ? "hot" : "temperate");
        boolean enclosed = !hasSkyAccess(pos, 10);
        float light = level.getBrightness(LightLayer.BLOCK, pos) / 15f;
        String lightLevel = light < 0.2f ? "dark" : (light > 0.7f ? "bright" : "dim");
        boolean night = (level.getDayTime() % 24000) > 13000;
        boolean playerNear = !level.players().isEmpty() && level.players().stream().anyMatch(p -> p.distanceToSqr(entity) < 256);
        int height = pos.getY();
        String heightLevel = height < 32 ? "low" : (height > 96 ? "high" : "mid");

        return String.format("%s|%s|%s|%s|%s|%s",
                biomeType, enclosed ? "closed" : "open", lightLevel, night ? "night" : "day",
                playerNear ? "player" : "noplayer", heightLevel);
    }

    private boolean hasSkyAccess(BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos check = center.offset(dx, 0, dz);
                if (entity.level().canSeeSky(check)) return true;
            }
        }
        return false;
    }

    // ============ СВОБОДА ============
    public float getFreedom() {
        long now = entity.level().getGameTime();
        if (now - lastFreedomTick > 100) {
            lastFreedomTick = now;
            FreedomAnalyzer analyzer = new FreedomAnalyzer(entity);
            analyzer.setEscapeRadius(20);
            analyzer.setMaxHorizontalDistance(150);
            analyzer.setMaxNodes(10000);
            FreedomAnalyzer.Result result = analyzer.analyze();
            if (result.free) {
                cachedFreedom = 1.0f;
            } else {
                float maxArea = 1000f;
                cachedFreedom = Math.min(0.9f, result.area / maxArea);
            }
        }
        return cachedFreedom;
    }

    // ============ НОВИЗНА ============
    public void updateNovelty(BlockPos currentPos, BlockPos previousPos) {
        if (previousPos != null && !previousPos.equals(currentPos)) {
            cachedNovelty = Math.min(1f, cachedNovelty + 0.05f);
        } else {
            cachedNovelty = Math.max(0f, cachedNovelty - 0.02f);
        }
    }

    public float getNovelty() {
        return cachedNovelty;
    }

    // ============ ДИСТРЕСС ============
    public float calculateDistress(float health, float hunger, float fatigue) {
        float freedom = getFreedom();
        float novelty = getNovelty();
        float wHealth = 0.4f;
        float wHunger = 0.3f;
        float wFatigue = 0.3f;
        float wFreedom = 0.2f;
        float wNovelty = 0.1f;

        float distress = (1f - health) * wHealth
                + hunger * wHunger
                + fatigue * wFatigue
                + (1f - freedom) * wFreedom
                + (1f - novelty) * wNovelty;
        return Math.min(1f, distress);
    }

    // ============ ОБУЧЕНИЕ ============
    public void learnNegative(MemoryType type, String context, float intensity) {
        if (context == null || context.isEmpty()) return;
        Map<String, Float> mem = memories.get(type);
        Map<String, Long> upd = lastUpdate.get(type);
        float current = mem.getOrDefault(context, 0f);
        float newValue = Math.max(current, Math.min(1f, intensity));
        mem.put(context, newValue);
        upd.put(context, entity.level().getGameTime());
        com.economymod.EconomyMod.LOGGER.info("{} learned {} fear of {} (intensity {})",
                entity.getName().getString(), type.name(), context, newValue);
    }

    public void learnItemFear(Item item, float intensity) {
        float current = itemFear.getOrDefault(item, 0f);
        float newValue = Math.max(current, Math.min(1f, intensity));
        itemFear.put(item, newValue);
        itemFearLastUpdate.put(item, entity.level().getGameTime());
        com.economymod.EconomyMod.LOGGER.info("{} learned fear of item {} (intensity {})",
                entity.getName().getString(), BuiltInRegistries.ITEM.getKey(item).getPath(), newValue);
    }

    public void learnPositive(String context, float intensity) {
        if (context == null || context.isEmpty()) return;
        float current = positiveMemory.getOrDefault(context, 0f);
        float newValue = Math.min(1f, current + intensity);
        positiveMemory.put(context, newValue);
        positiveLastUpdate.put(context, entity.level().getGameTime());
        com.economymod.EconomyMod.LOGGER.info("{} learned positive of {} (intensity {})",
                entity.getName().getString(), context, newValue);
    }

    public float getPositiveBonus(String context) {
        return positiveMemory.getOrDefault(context, 0f);
    }

    public float getFear(MemoryType type) {
        String context = getCurrentContext();
        return memories.get(type).getOrDefault(context, 0f);
    }

    public float getItemFear(Item item) {
        return itemFear.getOrDefault(item, 0f);
    }

    public float getOverallFear(Item targetItem) {
        float maxContext = 0f;
        for (MemoryType type : MemoryType.values()) {
            maxContext = Math.max(maxContext, getFear(type));
        }
        float maxItem = (targetItem != null) ? getItemFear(targetItem) : 0f;
        return Math.min(1f, Math.max(maxContext, maxItem));
    }

    public float modifyDesire(String desire, float baseDesire, Item targetItem, MemoryType primaryFear) {
        float fear = getFear(primaryFear);
        float itemFearValue = (targetItem != null) ? getItemFear(targetItem) : 0f;
        float combinedFear = Math.min(1f, fear + itemFearValue * 0.5f);

        switch (desire) {
            case "Explore":
                if (primaryFear == MemoryType.DAMAGE || primaryFear == MemoryType.MOB || primaryFear == MemoryType.PLAYER || primaryFear == MemoryType.BOREDOM)
                    return baseDesire * (1f - combinedFear);
                else
                    return baseDesire * (1f - combinedFear * 0.5f);
            case "FindFood":
                if (primaryFear == MemoryType.DAMAGE || primaryFear == MemoryType.MOB || primaryFear == MemoryType.PLAYER)
                    return baseDesire * (1f - combinedFear * 0.9f);
                else if (primaryFear == MemoryType.HUNGER)
                    return baseDesire * (1f - combinedFear * 0.2f);
                else
                    return baseDesire * (1f - combinedFear * 0.5f);
            case "Sleep":
                return baseDesire * getSleepModifier();
            case "Socialize":
                if (primaryFear == MemoryType.BOREDOM)
                    return baseDesire * (1f + combinedFear);
                else if (primaryFear == MemoryType.MOB || primaryFear == MemoryType.PLAYER)
                    return baseDesire * (1f - combinedFear);
                else
                    return baseDesire * (1f - combinedFear * 0.3f);
            default:
                return baseDesire;
        }
    }

    public float modifyDesireWithPositive(String desire, float baseDesire, String context) {
        if (!desire.equals("Explore")) return baseDesire;
        float positive = getPositiveBonus(context);
        return baseDesire * (1f + positive) * getActivityModifier();
    }

    public void inheritFrom(LearningSystem parent, float inheritanceRate) {
        for (MemoryType type : MemoryType.values()) {
            for (Map.Entry<String, Float> entry : parent.memories.get(type).entrySet()) {
                float childValue = entry.getValue() * inheritanceRate;
                if (childValue > 0.01f) {
                    this.memories.get(type).put(entry.getKey(), childValue);
                    this.lastUpdate.get(type).put(entry.getKey(), entity.level().getGameTime());
                }
            }
        }
        for (Map.Entry<Item, Float> entry : parent.itemFear.entrySet()) {
            float childValue = entry.getValue() * inheritanceRate;
            if (childValue > 0.01f) {
                this.itemFear.put(entry.getKey(), childValue);
                this.itemFearLastUpdate.put(entry.getKey(), entity.level().getGameTime());
            }
        }
        for (Map.Entry<String, Float> entry : parent.positiveMemory.entrySet()) {
            float childValue = entry.getValue() * inheritanceRate;
            if (childValue > 0.01f) {
                this.positiveMemory.put(entry.getKey(), childValue);
                this.positiveLastUpdate.put(entry.getKey(), entity.level().getGameTime());
            }
        }
    }

    public void mergeFrom(LearningSystem other, float strength) {
        if (other == null) return;
        for (MemoryType type : MemoryType.values()) {
            for (Map.Entry<String, Float> entry : other.memories.get(type).entrySet()) {
                float current = this.memories.get(type).getOrDefault(entry.getKey(), 0f);
                float newVal = Math.max(current, entry.getValue() * strength);
                if (newVal > 0.01f) {
                    this.memories.get(type).put(entry.getKey(), newVal);
                    this.lastUpdate.get(type).put(entry.getKey(), entity.level().getGameTime());
                }
            }
        }
        for (Map.Entry<Item, Float> entry : other.itemFear.entrySet()) {
            float current = this.itemFear.getOrDefault(entry.getKey(), 0f);
            float newVal = Math.max(current, entry.getValue() * strength);
            if (newVal > 0.01f) {
                this.itemFear.put(entry.getKey(), newVal);
                this.itemFearLastUpdate.put(entry.getKey(), entity.level().getGameTime());
            }
        }
        for (Map.Entry<String, Float> entry : other.positiveMemory.entrySet()) {
            float current = this.positiveMemory.getOrDefault(entry.getKey(), 0f);
            float newVal = Math.max(current, entry.getValue() * strength);
            if (newVal > 0.01f) {
                this.positiveMemory.put(entry.getKey(), newVal);
                this.positiveLastUpdate.put(entry.getKey(), entity.level().getGameTime());
            }
        }
    }

    public void tickForgetting() {
        long now = entity.level().getGameTime();
        for (MemoryType type : MemoryType.values()) {
            Map<String, Float> mem = memories.get(type);
            Map<String, Long> upd = lastUpdate.get(type);
            mem.entrySet().removeIf(entry -> {
                long last = upd.getOrDefault(entry.getKey(), 0L);
                if (now - last > FORGET_DELAY && entry.getValue() < 0.1f) {
                    upd.remove(entry.getKey());
                    return true;
                }
                float newVal = entry.getValue() * (1f - FORGET_RATE);
                if (newVal < 0.01f) {
                    upd.remove(entry.getKey());
                    return true;
                }
                entry.setValue(newVal);
                return false;
            });
        }
        itemFear.entrySet().removeIf(entry -> {
            long last = itemFearLastUpdate.getOrDefault(entry.getKey(), 0L);
            if (now - last > FORGET_DELAY && entry.getValue() < 0.1f) {
                itemFearLastUpdate.remove(entry.getKey());
                return true;
            }
            float newVal = entry.getValue() * (1f - FORGET_RATE);
            if (newVal < 0.01f) {
                itemFearLastUpdate.remove(entry.getKey());
                return true;
            }
            entry.setValue(newVal);
            return false;
        });
        positiveMemory.entrySet().removeIf(entry -> {
            long last = positiveLastUpdate.getOrDefault(entry.getKey(), 0L);
            if (now - last > FORGET_DELAY && entry.getValue() < 0.1f) {
                positiveLastUpdate.remove(entry.getKey());
                return true;
            }
            float newVal = entry.getValue() * (1f - FORGET_RATE);
            if (newVal < 0.01f) {
                positiveLastUpdate.remove(entry.getKey());
                return true;
            }
            entry.setValue(newVal);
            return false;
        });
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (MemoryType type : MemoryType.values()) {
            ListTag list = new ListTag();
            for (Map.Entry<String, Float> entry : memories.get(type).entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putString("Context", entry.getKey());
                e.putFloat("Value", entry.getValue());
                list.add(e);
            }
            tag.put(type.name() + "Memory", list);
        }
        ListTag itemList = new ListTag();
        for (Map.Entry<Item, Float> entry : itemFear.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Item", BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
            e.putFloat("Value", entry.getValue());
            itemList.add(e);
        }
        tag.put("ItemFear", itemList);
        ListTag posList = new ListTag();
        for (Map.Entry<String, Float> entry : positiveMemory.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("Context", entry.getKey());
            e.putFloat("Value", entry.getValue());
            posList.add(e);
        }
        tag.put("PositiveMemory", posList);
        tag.putFloat("CircadianPhase", circadianPhase);
        return tag;
    }

    public void load(CompoundTag tag) {
        for (MemoryType type : MemoryType.values()) {
            memories.get(type).clear();
            ListTag list = tag.getList(type.name() + "Memory", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                memories.get(type).put(e.getString("Context"), e.getFloat("Value"));
            }
        }
        itemFear.clear();
        ListTag itemList = tag.getList("ItemFear", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemList.size(); i++) {
            CompoundTag e = itemList.getCompound(i);
            Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(e.getString("Item")));
            if (item != null) {
                itemFear.put(item, e.getFloat("Value"));
            }
        }
        positiveMemory.clear();
        ListTag posList = tag.getList("PositiveMemory", Tag.TAG_COMPOUND);
        for (int i = 0; i < posList.size(); i++) {
            CompoundTag e = posList.getCompound(i);
            positiveMemory.put(e.getString("Context"), e.getFloat("Value"));
        }
        circadianPhase = tag.getFloat("CircadianPhase");
    }

    public Map<MemoryType, Map<String, Float>> getAllMemories() { return memories; }
    public Map<Item, Float> getAllItemFears() { return itemFear; }
    public Map<String, Float> getAllPositive() { return positiveMemory; }
}