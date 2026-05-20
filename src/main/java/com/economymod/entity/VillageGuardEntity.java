package com.economymod.entity;

import com.economymod.entity.ai.VillageGuardCombatGoal;
import com.economymod.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class VillageGuardEntity extends Villager {

    public VillageGuardEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, net.minecraft.world.entity.monster.Creeper.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new VillageGuardCombatGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Villager.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // Защита рук от ванильного ИИ AbstractVillager (Временной щит)
    @Override
    public void aiStep() {
        if (this.level().isClientSide()) {
            super.aiStep();
            return;
        }

        // 1. Запоминаем, что лежало в руках воина ДО выполнения ванильного тика
        ItemStack mainhandBefore = this.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        ItemStack offhandBefore = this.getItemBySlot(EquipmentSlot.OFFHAND).copy();

        // 2. Запускаем ванильный тик (он принудительно очистит руки и положит оружие в скрытый карман)
        super.aiStep();

        // 3. Возвращаем оружие и щит обратно в руки воину, если ванильный код их отобрал!
        if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !mainhandBefore.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, mainhandBefore);
            this.removeVanillaItem(mainhandBefore); // ИСПРАВЛЕНО: стираем дубликат через наш безопасный метод
        }
        if (this.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && !offhandBefore.isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, offhandBefore);
            this.removeVanillaItem(offhandBefore); // ИСПРАВЛЕНО: стираем дубликат
        }

        // Проверяем рюкзак на лучшую экипировку раз в 2 секунды
        if (this.level().getGameTime() % 40 == 0) {
            this.autoEquipFromBackpack();
        }
    }

    // ИСПРАВЛЕНО: Безопасный метод удаления предмета из ванильного инвентаря по его типу
    private void removeVanillaItem(ItemStack stack) {
        SimpleContainer vanillaInv = this.getInventory();
        for (int i = 0; i < vanillaInv.getContainerSize(); i++) {
            ItemStack s = vanillaInv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                // Забираем предмет по индексу слота и в правильном количестве
                vanillaInv.removeItem(i, stack.getCount());
                break;
            }
        }
    }

    private void autoEquipFromBackpack() {
        var att = this.getData(ModAttachments.VILLAGER.get());
        if (att == null) return;
        SimpleContainer inv = att.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            EquipmentSlot slot = this.getEquipmentSlotForItem(stack);

            if (isWeapon(stack)) {
                slot = EquipmentSlot.MAINHAND;
            }

            if (slot != null) {
                ItemStack currentEquip = this.getItemBySlot(slot);

                if (currentEquip.isEmpty() || getEquipmentRating(stack) > getEquipmentRating(currentEquip)) {
                    this.setItemSlot(slot, stack.copy());

                    if (!currentEquip.isEmpty()) {
                        inv.setItem(i, currentEquip);
                    } else {
                        inv.setItem(i, ItemStack.EMPTY);
                    }

                    this.level().playSound(null, this.blockPosition(),
                            net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    break;
                }
            }
        }
    }

    private boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem ||
                stack.getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    private int getEquipmentRating(ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();

        if (item instanceof net.minecraft.world.item.TieredItem tieredItem) {
            net.minecraft.world.item.Tier tier = tieredItem.getTier();
            if (tier == net.minecraft.world.item.Tiers.NETHERITE) return 50;
            if (tier == net.minecraft.world.item.Tiers.DIAMOND) return 40;
            if (tier == net.minecraft.world.item.Tiers.IRON) return 30;
            if (tier == net.minecraft.world.item.Tiers.STONE) return 20;
            if (tier == net.minecraft.world.item.Tiers.GOLD) return 20;
            if (tier == net.minecraft.world.item.Tiers.WOOD) return 10;
        }

        if (item instanceof net.minecraft.world.item.ArmorItem armorItem) {
            return armorItem.getDefense();
        }

        if (item instanceof net.minecraft.world.item.ShieldItem) {
            return 15;
        }

        return 0;
    }

    @Override
    public boolean isUsingItem() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) != 0;
    }

    @Override
    public ItemStack getUseItem() {
        if (this.isUsingItem()) {
            ItemStack offhand = this.getOffhandItem();
            if (offhand.getItem() instanceof ShieldItem) return offhand;
            ItemStack mainhand = this.getMainHandItem();
            if (mainhand.getItem() instanceof ShieldItem) return mainhand;
        }
        return super.getUseItem();
    }

    @Override
    public net.minecraft.world.InteractionHand getUsedItemHand() {
        if (this.isUsingItem()) {
            ItemStack offhand = this.getOffhandItem();
            if (offhand.getItem() instanceof ShieldItem) {
                return net.minecraft.world.InteractionHand.OFF_HAND;
            }
        }
        return super.getUsedItemHand();
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));

        if (random.nextFloat() < 0.5F) {
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    @Nullable
    @Override
    public Villager getBreedOffspring(ServerLevel level, AgeableMob other) {
        return com.economymod.registry.ModEntities.VILLAGE_GUARD.get().create(level);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof Villager) return false;
        return super.doHurtTarget(entity);
    }
}