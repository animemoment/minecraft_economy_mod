package com.economymod.command;

import com.economymod.entity.EconomyTraderEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

public class VillagerTradeCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("triggertrade")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = (ServerLevel) player.level();

                    AABB searchBox = player.getBoundingBox().inflate(50);
                    var traders = player.level().getEntitiesOfClass(
                            EconomyTraderEntity.class, searchBox, e -> true);

                    if (traders.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("No trader in 50 blocks!"));
                        return 0;
                    }

                    EconomyTraderEntity trader = traders.get(0);

                    PoiManager poiManager = level.getPoiManager();
                    Optional<BlockPos> bellPos = poiManager.findClosest(
                            poiType -> poiType.is(PoiTypes.MEETING),
                            trader.blockPosition(),
                            64,
                            PoiManager.Occupancy.ANY
                    );

                    if (bellPos.isPresent()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Bell found at " + bellPos.get().toShortString()), false);
                        trader.arriveAtVillage(bellPos.get());
                    } else {
                        ctx.getSource().sendFailure(Component.literal("No bell found in 64 blocks!"));
                    }

                    return 1;
                })
        );
    }
}