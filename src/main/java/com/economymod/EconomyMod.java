package com.economymod;

import com.economymod.client.ClientEventHandler;
import com.economymod.command.*;
import com.economymod.entity.ai.*;
import com.economymod.event.ServerEvents;
import com.economymod.event.VillagerInteractionHandler;
import com.economymod.registry.ModAttachments;
import com.economymod.registry.ModEntities;
import com.economymod.registry.ModMenus;
import com.economymod.economy.EconomyManager;
import com.economymod.attachment.VillagerAttachment;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
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
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (event.getPlayer() instanceof ServerPlayer sp) {
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (customData.contains("EconomyValue")) {
                long value = customData.copyTag().getLong("EconomyValue") * (long)stack.getCount();
                var att = sp.getData(ModAttachments.PLAYER_ECONOMY.get());
                if (att != null) att.add(value);
                sp.level().playSound(null, sp.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.2F);
                event.getItemEntity().discard();
                event.setCanPickup(TriState.FALSE);
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
                long coins = att.getBalance();
                if (coins > 0) {
                    ItemStack moneyStack = new ItemStack(Items.GOLD_NUGGET, 1);
                    CompoundTag tag = new CompoundTag(); tag.putLong("EconomyValue", coins);
                    moneyStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    moneyStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    event.getDrops().add(new ItemEntity(villager.level(), villager.getX(), villager.getY(), villager.getZ(), moneyStack));
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Villager villager && !event.getLevel().isClientSide) {
            villager.setCanPickUpLoot(true);
            villager.goalSelector.addGoal(1, new VillagerP2PTradeGoal(villager));
            villager.goalSelector.addGoal(2, new VillagerCraftingGoal(villager));
            villager.goalSelector.addGoal(2, new VillagerSmeltingGoal(villager));
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
                if (currentTime >= entry.getValue()) {
                    var entity = serverLevel.getEntity(entry.getKey());
                    if (entity instanceof Villager villager && villager.isAlive()) {
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

            // ИСПРАВЛЕНО: Безопасный и быстрый перебор жителей в мире
            if (currentTime % 20 == 0) { // Раз в секунду
                for (var entity : serverLevel.getAllEntities()) {
                    if (entity instanceof Villager villager && villager.isAlive()) {
                        siphonInventory(villager);
                    }
                }
            }

            com.economymod.economy.systems.VirtualTraderManager.tick(serverLevel);
            if (economyManager != null) economyManager.tick();
        }
    }

    private void siphonInventory(Villager villager) {
        SimpleContainer vanillaInv = villager.getInventory();
        var att = villager.getData(ModAttachments.VILLAGER.get());
        if (att == null) return;
        SimpleContainer econInv = att.getInventory();
        for (int i = 0; i < vanillaInv.getContainerSize(); i++) {
            ItemStack stack = vanillaInv.getItem(i);
            if (!stack.isEmpty()) {
                ItemStack leftover = econInv.addItem(stack.copy());
                vanillaInv.setItem(i, leftover);
            }
        }
    }

    public static EconomyManager getEconomyManager() { return economyManager; }
    public static void setEconomyManager(EconomyManager manager) { economyManager = manager; }
}