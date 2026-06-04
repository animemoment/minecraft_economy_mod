package com.economymod.network;

import com.economymod.EconomyMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ClientboundZoneSyncPacket(UUID zoneId, String zoneType, long[] positions, boolean remove) implements CustomPacketPayload {

    public static final Type<ClientboundZoneSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EconomyMod.MODID, "zone_sync"));

    // ИСПРАВЛЕНО: Стабильный StreamCodec через ручную сериализацию в FriendlyByteBuf
    public static final StreamCodec<ByteBuf, ClientboundZoneSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                FriendlyByteBuf friendly = new FriendlyBufWrapper(buf);
                friendly.writeUUID(packet.zoneId());
                friendly.writeUtf(packet.zoneType());
                friendly.writeLongArray(packet.positions());
                friendly.writeBoolean(packet.remove());
            },
            buf -> {
                FriendlyByteBuf friendly = new FriendlyBufWrapper(buf);
                return new ClientboundZoneSyncPacket(
                        friendly.readUUID(),
                        friendly.readUtf(),
                        friendly.readLongArray(),
                        friendly.readBoolean()
                );
            }
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(final ClientboundZoneSyncPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            com.economymod.zones.client.ClientZoneRenderer.handlePacket(packet);
        });
    }

    // Вспомогательный статический оберточный класс для ByteBuf
    private static class FriendlyBufWrapper extends FriendlyByteBuf {
        public FriendlyBufWrapper(ByteBuf source) {
            super(source);
        }
    }
}