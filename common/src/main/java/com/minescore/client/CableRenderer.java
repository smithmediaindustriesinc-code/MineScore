package com.minescore.client;

import com.minescore.MineScore;
import com.minescore.MineScoreSettings;
import com.minescore.item.WireCuttersItem;
import com.minescore.item.WireItem;
import com.minescore.wiring.Cable;
import com.minescore.wiring.CableGeometry;
import com.minescore.wiring.Gauge;
import com.minescore.wiring.PendingCable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws placed cables and the one the player is laying, as two crossed ribbons along the hanging curve. */
public final class CableRenderer {
    private static final Map<Gauge, ResourceLocation> TEXTURES = new EnumMap<>(Gauge.class);
    private static final Map<Gauge, Float> HALF_WIDTH = Map.of(Gauge.G12, 0.045f, Gauge.G16, 0.034f, Gauge.G20, 0.024f);
    /** Blocks of cable per repeat of the texture. */
    private static final double TEXTURE_LENGTH = 0.5;

    static {
        for (Gauge gauge : Gauge.values()) {
            TEXTURES.put(gauge, MineScore.id("textures/entity/cable_" + gauge.awg() + ".png"));
        }
    }

    private CableRenderer() {}

    public static void render(PoseStack pose, Camera camera, Frustum frustum, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();

        if (!MineScoreSettings.invisibleWires.getAsBoolean() || holdsWireTool(player)) {
            for (Cable cable : ClientCables.all()) {
                drawPath(pose, buffers, level, frustum, cam, cable.path(), cable.gauge());
            }
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WireItem wire) {
                PendingCable pending = PendingCable.read(stack);
                if (pending != null && pending.dimension().equals(level.dimension())) {
                    List<Vec3> path = new ArrayList<>(pending.points());
                    path.add(handPosition(player, hand, partialTick));
                    drawPath(pose, buffers, level, frustum, cam, path, wire.gauge());
                }
            }
        }

        for (ResourceLocation texture : TEXTURES.values()) {
            buffers.endBatch(RenderType.entityCutoutNoCull(texture));
        }
    }

    private static boolean holdsWireTool(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            Item item = player.getItemInHand(hand).getItem();
            if (item instanceof WireItem || item instanceof WireCuttersItem) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 handPosition(LocalPlayer player, InteractionHand hand, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
        double sign = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT) ? 1 : -1;
        return player.getEyePosition(partialTick).add(look.scale(0.6)).add(side.scale(0.3 * sign)).add(0, -0.35, 0);
    }

    private static void drawPath(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Frustum frustum, Vec3 cam,
                                 List<Vec3> path, Gauge gauge) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES.get(gauge)));
        float halfWidth = HALF_WIDTH.get(gauge);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        double v = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            AABB bounds = new AABB(a, b).expandTowards(0, -CableGeometry.sag(a.distanceTo(b)), 0).inflate(0.1);
            if (!frustum.isVisible(bounds)) {
                v += a.distanceTo(b) / TEXTURE_LENGTH;
                continue;
            }
            List<Vec3> points = CableGeometry.sample(a, b);
            for (int j = 0; j + 1 < points.size(); j++) {
                Vec3 p0 = points.get(j);
                Vec3 p1 = points.get(j + 1);
                double step = p0.distanceTo(p1) / TEXTURE_LENGTH;
                Vec3 along = p1.subtract(p0).normalize();
                Vec3 side = along.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-6) {
                    side = along.cross(new Vec3(1, 0, 0));
                }
                side = side.normalize();
                Vec3 up = along.cross(side).normalize();
                int light0 = LevelRenderer.getLightColor(level, BlockPos.containing(p0));
                int light1 = LevelRenderer.getLightColor(level, BlockPos.containing(p1));
                ribbon(consumer, matrix, normal, p0.subtract(cam), p1.subtract(cam), side.scale(halfWidth), up,
                        (float) v, (float) (v + step), light0, light1);
                ribbon(consumer, matrix, normal, p0.subtract(cam), p1.subtract(cam), up.scale(halfWidth), side,
                        (float) v, (float) (v + step), light0, light1);
                v += step;
            }
        }
    }

    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, Vec3 p0, Vec3 p1, Vec3 offset,
                               Vec3 facing, float v0, float v1, int light0, int light1) {
        vertex(consumer, matrix, normal, p0.subtract(offset), 0, v0, light0, facing);
        vertex(consumer, matrix, normal, p0.add(offset), 1, v0, light0, facing);
        vertex(consumer, matrix, normal, p1.add(offset), 1, v1, light1, facing);
        vertex(consumer, matrix, normal, p1.subtract(offset), 0, v1, light1, facing);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, Vec3 p, float u, float v, int light,
                               Vec3 facing) {
        consumer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, (float) facing.x, (float) facing.y, (float) facing.z)
                .endVertex();
    }
}
