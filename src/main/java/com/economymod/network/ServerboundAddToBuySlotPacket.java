package com.economymod.network;

import com.economymod.economy.IEconomicActor;
import com.economymod.gui.menu.EconomyTradeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

public record ServerboundAddToBuySlotPacket(int traderSlot, boolean shift) implements CustomPacketPayload {
    public static final Type<ServerboundAddToBuySlotPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("economymod", "add_to_buy"));
    public static final StreamCodec<ByteBuf, ServerboundAddToBuySlotPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundAddToBuySlotPacket::traderSlot,
            ByteBufCodecs.BOOL, ServerboundAddToBuySlotPacket::shift,
            ServerboundAddToBuySlotPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(final ServerboundAddToBuySlotPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp) || !(sp.containerMenu instanceof EconomyTradeMenu menu)) return;
            IEconomicActor owner = menu.getOwnerActor();
            if (owner == null) return;

            ItemStack source = owner.getInventory().getItem(packet.traderSlot());
            if (source.isEmpty()) return;

            int amount = packet.shift() ? source.getCount() : 1;
            ItemStack toAdd = source.split(amount); // ФИЗИЧЕСКОЕ ИЗЪЯТИЕ
            menu.buyContainer.addItem(toAdd);

            // Синхронизация всех инвентарей
            List<ItemStack> buyItems = new ArrayList<>();
            for (int i = 0; i < 9; i++) buyItems.add(menu.buyContainer.getItem(i).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundBuyContainerSyncPacket(buyItems));

            List<ItemStack> ownerItems = new ArrayList<>();
            for (int j = 0; j < 36; j++) ownerItems.add(owner.getInventory().getItem(j).copy());
            PacketDistributor.sendToPlayer(sp, new ClientboundOwnerInventorySyncPacket(ownerItems, owner.getBalance()));
        });
    }
}