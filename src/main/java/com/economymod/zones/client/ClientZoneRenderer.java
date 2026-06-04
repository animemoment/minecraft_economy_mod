package com.economymod.zones.client;

import com.economymod.EconomyMod;
import com.economymod.network.ClientboundZoneSyncPacket;
import com.economymod.zones.ZoneInstance;
import com.economymod.zones.ZoneType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = EconomyMod.MODID, value = Dist.CLIENT)
public class ClientZoneRenderer {

    private static final Map<UUID, ZoneInstance> clientZones = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Face>> cachedFaces = new ConcurrentHashMap<>();

    // Константы нормалей для 6 направлений
    private static final Vector3f NORMAL_DOWN = new Vector3f(0, -1, 0);
    private static final Vector3f NORMAL_UP = new Vector3f(0, 1, 0);
    private static final Vector3f NORMAL_NORTH = new Vector3f(0, 0, -1);
    private static final Vector3f NORMAL_SOUTH = new Vector3f(0, 0, 1);
    private static final Vector3f NORMAL_WEST = new Vector3f(-1, 0, 0);
    private static final Vector3f NORMAL_EAST = new Vector3f(1, 0, 0);

    public static void handlePacket(ClientboundZoneSyncPacket packet) {
        if (packet.remove()) {
            clientZones.remove(packet.zoneId());
            cachedFaces.remove(packet.zoneId());
        } else {
            Set<Long> positions = new HashSet<>(packet.positions().length);
            for (long pos : packet.positions()) {
                positions.add(pos);
            }
            ZoneInstance zone = new ZoneInstance(packet.zoneId(), ZoneType.valueOf(packet.zoneType()), positions, null);
            clientZones.put(packet.zoneId(), zone);
            cachedFaces.put(packet.zoneId(), calculateVisibleFaces(zone));
        }
    }

    private static List<Face> calculateVisibleFaces(ZoneInstance zone) {
        List<Face> faces = new ArrayList<>();
        Set<Long> zoneSet = zone.getPositions();

        for (long posLong : zoneSet) {
            BlockPos pos = BlockPos.of(posLong);

            if (!zoneSet.contains(pos.north().asLong())) faces.add(new Face(pos, Direction.NORTH));
            if (!zoneSet.contains(pos.south().asLong())) faces.add(new Face(pos, Direction.SOUTH));
            if (!zoneSet.contains(pos.east().asLong())) faces.add(new Face(pos, Direction.EAST));
            if (!zoneSet.contains(pos.west().asLong())) faces.add(new Face(pos, Direction.WEST));
            if (!zoneSet.contains(pos.above().asLong())) faces.add(new Face(pos, Direction.UP));
            if (!zoneSet.contains(pos.below().asLong())) faces.add(new Face(pos, Direction.DOWN));
        }
        return faces;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (clientZones.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // RenderType.lines() требует: Position, Color, Normal
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();
        // Нормаль нужно трансформировать с учётом PoseStack. Получаем матрицу нормалей.
        Matrix4f normalMatrix = new Matrix4f(poseStack.last().normal());

        BlockPos playerPos = mc.player.blockPosition();
        int renderDistanceSqr = (Minecraft.getInstance().options.renderDistance().get() * 16);
        renderDistanceSqr = renderDistanceSqr * renderDistanceSqr;

        for (Map.Entry<UUID, ZoneInstance> entry : clientZones.entrySet()) {
            ZoneInstance zone = entry.getValue();
            List<Face> faces = cachedFaces.get(entry.getKey());

            if (faces == null) {
                faces = calculateVisibleFaces(zone);
                cachedFaces.put(entry.getKey(), faces);
            }

            float r, g, b;
            if (zone.getType() == ZoneType.TREE) {
                r = 0.6f; g = 0.0f; b = 0.8f;
            } else if (zone.getType() == ZoneType.VILLAGE) {
                r = 0.0f; g = 0.8f; b = 0.0f;
            } else if (zone.getType() == ZoneType.CAVE) {
                r = 0.9f; g = 0.4f; b = 0.0f;
            } else {
                r = 0.0f; g = 0.5f; b = 1.0f;
            }
            float a = 0.4f;

            for (Face face : faces) {
                if (face.pos().distSqr(playerPos) > renderDistanceSqr) {
                    continue;
                }
                renderWireframeFace(matrix, normalMatrix, consumer, face, r, g, b, a);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderWireframeFace(Matrix4f matrix, Matrix4f normalMatrix, VertexConsumer consumer,
                                            Face face, float r, float g, float b, float a) {
        BlockPos p = face.pos();
        float x1 = p.getX();
        float y1 = p.getY();
        float z1 = p.getZ();
        float x2 = x1 + 1.0f;
        float y2 = y1 + 1.0f;
        float z2 = z1 + 1.0f;

        Vector3f normal;

        switch (face.dir()) {
            case DOWN:
                normal = transformNormal(normalMatrix, NORMAL_DOWN);
                // 4 линии = 8 вершин (каждая линия: 2 вершины)
                consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                break;

            case UP:
                normal = transformNormal(normalMatrix, NORMAL_UP);
                consumer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                break;

            case NORTH:
                normal = transformNormal(normalMatrix, NORMAL_NORTH);
                consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                break;

            case SOUTH:
                normal = transformNormal(normalMatrix, NORMAL_SOUTH);
                consumer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                break;

            case WEST:
                normal = transformNormal(normalMatrix, NORMAL_WEST);
                consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                break;

            case EAST:
                normal = transformNormal(normalMatrix, NORMAL_EAST);
                consumer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());

                consumer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                consumer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
                break;
        }
    }

    private static Vector3f transformNormal(Matrix4f normalMatrix, Vector3f normal) {
        Vector3f result = new Vector3f(normal);
        result.mulDirection(normalMatrix);
        return result;
    }

    private record Face(BlockPos pos, Direction dir) {}
}