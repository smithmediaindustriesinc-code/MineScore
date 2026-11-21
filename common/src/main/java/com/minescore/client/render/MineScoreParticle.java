package com.minescore.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * How each of MineScore's particles moves (SCOPE.md §13.5): notes drift up from playing speakers in their
 * band's colour, vinyl shavings curl off the Lathe's cutter and fall, record shards tumble and bounce, sparks
 * fly and drop, and a short's smoke rises and spreads.
 */
public class MineScoreParticle extends TextureSheetParticle {
    public enum Kind { NOTE, SHAVING, SHARD, SPARK, SMOKE }

    /** The jacks' band colours (tools/textures.py BAND_COLORS): 12 ga red, 16 ga yellow, 20 ga blue. */
    private static final float[][] BANDS = {{0.86f, 0.24f, 0.22f}, {0.95f, 0.80f, 0.20f}, {0.25f, 0.50f, 0.95f}};

    private final Kind kind;
    private final SpriteSet sprites;
    private float spin;

    protected MineScoreParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, Kind kind, SpriteSet sprites) {
        super(level, x, y, z);
        this.kind = kind;
        this.sprites = sprites;
        this.spin = (random.nextFloat() - 0.5f) * 0.4f;
        switch (kind) {
            case NOTE -> {
                float[] c = BANDS[Mth.clamp((int) dx, 0, 2)];
                setColor(c[0], c[1], c[2]);
                xd = (random.nextDouble() - 0.5) * 0.01;
                yd = 0.02 + random.nextDouble() * 0.01;
                zd = (random.nextDouble() - 0.5) * 0.01;
                gravity = 0;
                lifetime = 24 + random.nextInt(10);
                quadSize = 0.12f;
            }
            case SHAVING -> {
                xd = dx + (random.nextDouble() - 0.5) * 0.06;
                yd = 0.06 + random.nextDouble() * 0.04;
                zd = dz + (random.nextDouble() - 0.5) * 0.06;
                gravity = 0.6f;
                lifetime = 18 + random.nextInt(10);
                quadSize = 0.05f + random.nextFloat() * 0.03f;
            }
            case SHARD -> {
                xd = dx + (random.nextDouble() - 0.5) * 0.3;
                yd = 0.15 + random.nextDouble() * 0.2;
                zd = dz + (random.nextDouble() - 0.5) * 0.3;
                gravity = 1.1f;
                lifetime = 30 + random.nextInt(20);
                quadSize = 0.06f + random.nextFloat() * 0.06f;
            }
            case SPARK -> {
                xd = dx + (random.nextDouble() - 0.5) * 0.4;
                yd = 0.1 + random.nextDouble() * 0.25;
                zd = dz + (random.nextDouble() - 0.5) * 0.4;
                gravity = 0.9f;
                lifetime = 6 + random.nextInt(8);
                quadSize = 0.04f;
            }
            case SMOKE -> {
                xd = (random.nextDouble() - 0.5) * 0.03;
                yd = 0.03 + random.nextDouble() * 0.03;
                zd = (random.nextDouble() - 0.5) * 0.03;
                gravity = -0.02f;
                lifetime = 30 + random.nextInt(20);
                quadSize = 0.2f;
            }
        }
        hasPhysics = kind == Kind.SHAVING || kind == Kind.SHARD || kind == Kind.SPARK;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        oRoll = roll;
        switch (kind) {
            case NOTE -> {
                alpha = life < 0.7f ? 1 : 1 - (life - 0.7f) / 0.3f;
                xd += Math.sin(age * 0.4) * 0.002; // a gentle sway as it floats up
            }
            case SHAVING, SHARD -> {
                roll += spin;
                if (onGround) { // lying where it landed
                    xd *= 0.5;
                    zd *= 0.5;
                    spin = 0;
                }
            }
            case SPARK -> alpha = 1 - life;
            case SMOKE -> {
                alpha = 0.8f * (1 - life);
                quadSize *= 1.03f;
            }
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.SPARK || kind == Kind.NOTE ? 0xF000F0 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static ParticleProvider<SimpleParticleType> provider(Kind kind, SpriteSet sprites) {
        // Every MineScore particle comes through here, so this one check covers the Particles setting (off by default).
        return (type, level, x, y, z, dx, dy, dz) -> com.minescore.config.MineScoreConfig.PARTICLES.bool()
                ? new MineScoreParticle(level, x, y, z, dx, dy, dz, kind, sprites) : null;
    }
}
