package com.minescore.client.render;

import com.minescore.MineScore;
import com.minescore.block.SpeakerBlock;
import com.minescore.block.SpeakerBlockEntity;
import com.minescore.block.TweeterBlock;
import com.minescore.client.audio.AudioEngine;
import com.minescore.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * A speaker's driver in 3D (SCOPE.md §11.2): the Subwoofer's and Middle Speaker's cones sink into the baffle
 * from the rubber surround to a domed dust cap, and the Tweeter's dome stands proud of its waveguide. While
 * the speaker plays, the cone pumps in and out: harder the louder it's driven (the music's loudness at it
 * times its system's gain), and faster the higher it's playing. The front texture has a hole where the driver
 * goes and the model a dark pocket behind it, so the cone sits in a real recess.
 */
public class SpeakerRenderer implements BlockEntityRenderer<SpeakerBlockEntity> {
    /**
     * Where the driver sits in the north-facing model, in pixels (front face at z = {@code face}), matching
     * gen_resources.py SPEAKER_DRIVERS: centre, the hole's radius, the dust cap's (or dome's) radius, how deep
     * the cone goes, and how far it may move.
     */
    record Driver(double face, double cx, double cy, double rim, double cap, double depth, double travel, boolean dome) {}

    static final Driver SUBWOOFER = new Driver(0, 8, 8, 5.6, 2.5, 3.2, 0.9, false);
    static final Driver MIDDLE = new Driver(2, 8, 6, 3.7, 1.5, 2.4, 0.55, false);
    static final Driver TWEETER = new Driver(4, 8, 4, 1.9, 1.9, 0.9, 0.2, true);

    private static final int SEGMENTS = 24;
    private static final TextureAtlasSprite[] SPRITES = new TextureAtlasSprite[3];

    /** Per speaker: {phase, smoothed level, last frame's time in seconds}. */
    private final Map<SpeakerBlockEntity, double[]> motion = new WeakHashMap<>();

    public SpeakerRenderer(BlockEntityRendererProvider.Context context) {}

    private static TextureAtlasSprite sprite(int i) {
        if (SPRITES[i] == null) {
            String name = new String[] {"speaker_cone", "speaker_cap", "tweeter_dome"}[i];
            SPRITES[i] = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(MineScore.id("block/" + name));
        }
        return SPRITES[i];
    }

    /** Forgets the cached sprites when textures reload (a resource pack changed them). */
    public static void reload() {
        java.util.Arrays.fill(SPRITES, null);
    }

    @Override
    public void render(SpeakerBlockEntity speaker, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = speaker.getBlockState();
        Driver driver = state.is(ModBlocks.SUBWOOFER.get()) ? SUBWOOFER : state.is(ModBlocks.MIDDLE_SPEAKER.get()) ? MIDDLE : TWEETER;
        double excursion = excursion(speaker, driver);

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, ((SpeakerBlock) state.getBlock()).yOffset(state) / 16, 0.5); // sitting flush on the speaker below
        pose.mulPose(Axis.YP.rotationDegrees(-switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        }));
        if (state.hasProperty(TweeterBlock.DIAGONAL) && state.getValue(TweeterBlock.DIAGONAL)) {
            pose.mulPose(Axis.YP.rotationDegrees(-45));
        }
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        int lit = speaker.getLevel() == null ? light : LevelRenderer.getLightColor(speaker.getLevel(), speaker.getBlockPos().relative(facing));
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        if (driver.dome()) {
            dome(pose, out, driver, excursion, lit);
        } else {
            cone(pose, out, driver, excursion, lit);
        }
        pose.popPose();
    }

    /** How far forward the cone is this frame, in pixels: a wave whose size follows the level and speed the pitch. */
    private double excursion(SpeakerBlockEntity speaker, Driver driver) {
        float[] drive = AudioEngine.cone(speaker.getBlockPos());
        double now = System.nanoTime() / 1e9;
        double[] m = motion.computeIfAbsent(speaker, s -> new double[] {0, 0, now});
        double dt = Math.min(0.1, now - m[2]);
        m[2] = now;
        double level = drive == null ? 0 : drive[0];
        m[1] += (level - m[1]) * Math.min(1, dt * 12); // smoothed, so it swells and settles rather than jumps
        if (m[1] < 0.002) {
            return 0;
        }
        // Visible wobbles a second: about 3 at 30 Hz up to about 24 at 8 kHz, so the bass pumps and the treble buzzes.
        double pitch = drive == null ? 60 : Math.max(20, drive[1]);
        double rate = 3 + 3 * Math.log(pitch / 20) / Math.log(2);
        m[0] = (m[0] + dt * Math.min(24, rate) * 2 * Math.PI) % (2 * Math.PI * 1000);
        double size = driver.travel() * Math.min(1, m[1] * 4);
        double wobble = Math.sin(m[0]) * 0.8 + Math.sin(m[0] * 2.3 + 1) * 0.2; // a little uneven, like real music
        return size * wobble;
    }

    /** The cone from the surround down to the cap ring, then the domed dust cap, moving forward by {@code e}. */
    private static void cone(PoseStack pose, VertexConsumer out, Driver d, double e, int light) {
        TextureAtlasSprite cone = sprite(0), cap = sprite(1);
        double rimZ = d.face() + 0.15 - e * 0.3, capZ = d.face() + d.depth() - e;
        ring(pose, out, cone, d, d.rim(), rimZ, d.cap(), capZ, light);
        double domeHeight = d.cap() * 0.45;
        ring(pose, out, cap, d, d.cap(), capZ, d.cap() * 0.6, capZ - domeHeight * 0.7, light);
        ring(pose, out, cap, d, d.cap() * 0.6, capZ - domeHeight * 0.7, 0.001, capZ - domeHeight, light);
    }

    /** The Tweeter's dome, standing out of its waveguide. */
    private static void dome(PoseStack pose, VertexConsumer out, Driver d, double e, int light) {
        TextureAtlasSprite dome = sprite(2);
        double base = d.face() + 0.05, height = d.depth() + e;
        double r1 = d.cap() * 0.75, r2 = d.cap() * 0.4;
        ring(pose, out, dome, d, d.cap(), base, r1, base - height * 0.55, light);
        ring(pose, out, dome, d, r1, base - height * 0.55, r2, base - height * 0.88, light);
        ring(pose, out, dome, d, r2, base - height * 0.88, 0.001, base - height, light);
    }

    /** A band of quads between two circles round the driver's centre: the outer at depth z1, the inner at z2. */
    private static void ring(PoseStack pose, VertexConsumer out, TextureAtlasSprite sprite, Driver d, double r1, double z1, double r2,
                             double z2, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i < SEGMENTS; i++) {
            double a1 = 2 * Math.PI * i / SEGMENTS, a2 = 2 * Math.PI * (i + 1) / SEGMENTS;
            float u1 = sprite.getU(16.0 * i / SEGMENTS), u2 = sprite.getU(16.0 * (i + 1) / SEGMENTS);
            float v1 = sprite.getV(0), v2 = sprite.getV(16);
            // lit from the top-left, like the textures
            float shade1 = shade(a1), shade2 = shade(a2);
            vertex(out, m, n, d.cx() + Math.cos(a1) * r1, d.cy() + Math.sin(a1) * r1, z1, u1, v1, shade1, light);
            vertex(out, m, n, d.cx() + Math.cos(a2) * r1, d.cy() + Math.sin(a2) * r1, z1, u2, v1, shade2, light);
            vertex(out, m, n, d.cx() + Math.cos(a2) * r2, d.cy() + Math.sin(a2) * r2, z2, u2, v2, shade2, light);
            vertex(out, m, n, d.cx() + Math.cos(a1) * r2, d.cy() + Math.sin(a1) * r2, z2, u1, v2, shade1, light);
        }
    }

    private static float shade(double angle) {
        // up-left (seen from the front: the model's +x is the viewer's left) catches the light
        return (float) (0.85 + 0.15 * Math.cos(angle - Math.PI * 0.75));
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Matrix3f n, double x, double y, double z, float u, float v, float shade,
                               int light) {
        out.vertex(m, (float) x, (float) y, (float) z).color(shade, shade, shade, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0, 0, -1).endVertex();
    }
}
