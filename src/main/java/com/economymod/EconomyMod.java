package com.economymod;

import com.economymod.client.ClientEventHandler;
import com.economymod.command.*;
import com.economymod.entity.ai.*;
import com.economymod.event.ServerEvents;
import com.economymod.event.VillagerInteractionHandler;
import com.economymod.registry.ModAttachments;
import com.economymod.registry.ModEntities;
import com.economymod.registry.ModMenus;
import com.economymod.registry.ModProfessions;
import com.economymod.economy.EconomyManager;
import com.economymod.attachment.VillagerAttachment;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import com.economymod.event.GuardAutoAssigner;
import com.economymod.neural.NeuralManager;
import com.economymod.neural.NeuralCommand;



import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(EconomyMod.MODID)
public class EconomyMod {
    public static final String MODID = "economymod";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile EconomyManager economyManager;
    private static final Map<UUID, Long> lootDelayQueue = new HashMap<>();

    public EconomyMod(IEventBus modEventBus) {
        ModProfessions.POI_TYPES.register(modEventBus);
        ModProfessions.PROFESSIONS.register(modEventBus);


        NeoForge.EVENT_BUS.register(GuardAutoAssigner.class);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ClientEventHandler::registerScreens);
        modEventBus.addListener(ClientEventHandler::registerEntityRenderers);

        NeoForge.EVENT_BUS.register(CommandRegistry.class);
        NeoForge.EVENT_BUS.register(VillagerTradeCommand.class);
        NeoForge.EVENT_BUS.register(GiveBudgetCommand.class);
        NeoForge.EVENT_BUS.register(InitializeLootCommand.class);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        NeoForge.EVENT_BUS.register(VillagerInteractionHandler.class);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(GuardAutoAssigner.class);
        //NeoForge.EVENT_BUS.register(NeuralManager.class);
    }

        public static void clearLootQueue() {
            lootDelayQueue.clear();
        }

        @SubscribeEvent
        public void onItemPickup(ItemEntityPickupEvent.Pre event) {
            ItemStack stack = event.getItemEntity().getItem();
            if (event.getPlayer() instanceof ServerPlayer sp) {
                CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                if (customData.contains("EconomyValue")) {
                    double value = customData.copyTag().getDouble("EconomyValue");
                    var pEco = sp.getData(ModAttachments.PLAYER_ECONOMY.get());
                    if (pEco != null) pEco.add(value);
                    sp.level().playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.2F);
                    event.getItemEntity().discard();
                    event.setCanPickup(TriState.FALSE);
                }
            }
        }

        @SubscribeEvent
        public void onEntityJoin(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof Villager villager && !event.getLevel().isClientSide) {
                villager.goalSelector.getAvailableGoals().removeIf(goal ->
                        goal.getGoal() instanceof TradeWithPlayerGoal || goal.getGoal() instanceof LookAtTradingPlayerGoal);
                villager.setCanPickUpLoot(true);
                villager.goalSelector.addGoal(1, new VillagerP2PTradeGoal(villager));
                villager.goalSelector.addGoal(2, new VillagerCraftingGoal(villager));
                villager.goalSelector.addGoal(2, new VillagerSmeltingGoal(villager));
                villager.goalSelector.addGoal(2, new VillagerMiningGoal(villager));
                villager.goalSelector.addGoal(2, new VillagerCompostingGoal(villager));
                villager.goalSelector.addGoal(3, new VillagerDepositTrashGoal(villager));
                var att = villager.getData(ModAttachments.VILLAGER.get());
                if (att != null && !event.loadedFromDisk()) {
                    lootDelayQueue.put(villager.getUUID(), event.getLevel().getGameTime() + 20);
                }
            }
        }

        @SubscribeEvent
        public void onLevelTick(LevelTickEvent.Post event) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                long currentTime = serverLevel.getGameTime();

                lootDelayQueue.entrySet().removeIf(entry -> {
                    Entity entity = serverLevel.getEntity(entry.getKey());
                    if (entity == null || !entity.isAlive()) {
                        return true;
                    }
                    if (currentTime >= entry.getValue()) {
                        if (entity instanceof Villager villager) {
                            var att = villager.getData(ModAttachments.VILLAGER.get());
                            if (att != null && !att.wasLootGenerated()) {
                                if (villager.getVillagerData().getProfession() != VillagerProfession.NONE) att.fillInitialLoot();
                                else att.setLootGenerated(true);
                            }
                        }
                        return true;
                    }
                    return false;
                });

                if (currentTime % 20 == 0) {
                    for (Villager villager : serverLevel.getEntities(EntityTypeTest.forClass(Villager.class), Entity::isAlive)) {
                        processRealisticPickup(villager, serverLevel);
                        siphonInventory(villager);
                    }
                }
                com.economymod.economy.systems.VirtualTraderManager.tick(serverLevel);
                if (economyManager != null) economyManager.tick();
            }
        }

        private void processRealisticPickup(Villager villager, ServerLevel level) {
            var att = villager.getData(ModAttachments.VILLAGER.get());
            if (att == null) return;

            for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(1.5))) {
                if (!itemEntity.isAlive() || !itemEntity.onGround()) continue;
                ItemStack stack = itemEntity.getItem();
                ItemStack leftover = att.getInventory().addItem(stack.copy());
                if (leftover.getCount() < stack.getCount()) {
                    itemEntity.setItem(leftover);
                    if (leftover.isEmpty()) itemEntity.discard();
                    level.playSound(null, villager.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 1.0F);

                    com.economymod.EconomyMod.LOGGER.info("ЭКОНОМИКА: Житель {} успешно засосал с земли {} x{}",
                            villager.getName().getString(), stack.getItem().toString(), (stack.getCount() - leftover.getCount()));
                }
            }
        }

        private void siphonInventory(Villager villager) {
            SimpleContainer vanillaInv = villager.getInventory();
            var att = villager.getData(ModAttachments.VILLAGER.get());
            if (att == null) return;
            for (int i = 0; i < vanillaInv.getContainerSize(); i++) {
                ItemStack stack = vanillaInv.getItem(i);
                if (!stack.isEmpty()) {
                    ItemStack leftover = att.getInventory().addItem(stack.copy());
                    vanillaInv.setItem(i, leftover);
                }
            }
        }

        @SubscribeEvent
        public void onVillagerDrops(LivingDropsEvent event) {
            if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
                var att = villager.getData(ModAttachments.VILLAGER.get());
                if (att != null) {
                    SimpleContainer inv = att.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (!inv.getItem(i).isEmpty()) event.getDrops().add(new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), inv.getItem(i).copy()));
                    }
                    double coins = att.getBalance();
                    if (coins > 0) {
                        ItemStack moneyStack = new ItemStack(Items.GOLD_NUGGET, 1);
                        CompoundTag tag = new CompoundTag(); tag.putDouble("EconomyValue", coins);
                        moneyStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        moneyStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                        event.getDrops().add(new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), moneyStack));
                    }
                }
            }
        }

        public static EconomyManager getEconomyManager() { return economyManager; }
        public static void setEconomyManager(EconomyManager manager) { economyManager = manager; }
    }