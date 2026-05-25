package com.economymod.entity;

import com.economymod.entity.ai.VillageGuardCombatGoal;
import com.economymod.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class VillageGuardEntity extends Villager {

    public VillageGuardEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(true);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 1.0F);
        }
    }

    @Override
    protected void registerGoals() {
        // Ванильные цели удаляем, чтобы не мешали
        this.goalSelector.getAvailableGoals().clear();
        this.targetSelector.getAvailableGoals().clear();

        // Цели поведения (движение)
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, net.minecraft.world.entity.monster.Creeper.class, 8.0F, 1.2D, 1.5D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true)); // Добавлена базовая атака
        this.goalSelector.addGoal(3, new VillageGuardCombatGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Villager.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // Цели для выбора цели (атака)
        // Важно: checkIfCanUse = true, checkIfCanSee = true
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, true, (living) -> {
            return !(living instanceof Villager); // не атакуем жителей
        }));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this).setAlertOthers());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_SPEED, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    public void aiStep() {
        if (this.level().isClientSide()) {
            super.aiStep();
            return;
        }

        ItemStack mainhandBefore = this.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        ItemStack offhandBefore = this.getItemBySlot(EquipmentSlot.OFFHAND).copy();
        super.aiStep();
        if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !mainhandBefore.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, mainhandBefore);
            this.removeVanillaItem(mainhandBefore);
        }
        if (this.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && !offhandBefore.isEmpty()) {
            this.setItemSlot(EquipmentSlot.OFFHAND, offhandBefore);
            this.removeVanillaItem(offhandBefore);
        }

        if (this.level().getGameTime() % 5 == 0) {
            this.autoEquipFromBackpack();
        }

        var att = this.getData(ModAttachments.VILLAGER.get());
        if (att != null && att.getHunger() > 18.0 && this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0f);
        }
    }

    private void removeVanillaItem(ItemStack stack) {
        SimpleContainer vanillaInv = this.getInventory();
        for (int i = 0; i < vanillaInv.getContainerSize(); i++) {
            ItemStack s = vanillaInv.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, stack)) {
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
            if (isWeapon(stack)) slot = EquipmentSlot.MAINHAND;
            if (slot != null) {
                ItemStack currentEquip = this.getItemBySlot(slot);
                if (currentEquip.isEmpty() || getEquipmentRating(stack) > getEquipmentRating(currentEquip)) {
                    this.setItemSlot(slot, stack.copy());
                    if (!currentEquip.isEmpty()) inv.setItem(i, currentEquip);
                    else inv.setItem(i, ItemStack.EMPTY);
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
        if (item instanceof net.minecraft.world.item.ArmorItem armorItem) return armorItem.getDefense();
        if (item instanceof net.minecraft.world.item.ShieldItem) return 15;
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
            if (offhand.getItem() instanceof net.minecraft.world.item.ShieldItem) return offhand;
            ItemStack mainhand = this.getMainHandItem();
            if (mainhand.getItem() instanceof net.minecraft.world.item.ShieldItem) return mainhand;
        }
        return super.getUseItem();
    }

    @Override
    public net.minecraft.world.InteractionHand getUsedItemHand() {
        if (this.isUsingItem()) {
            ItemStack offhand = this.getOffhandItem();
            if (offhand.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                return net.minecraft.world.InteractionHand.OFF_HAND;
            }
        }
        return super.getUsedItemHand();
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
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
    public boolean doHurtTarget(Entity entity) {
        if (entity instanceof Villager) return false;
        return super.doHurtTarget(entity);
    }
}