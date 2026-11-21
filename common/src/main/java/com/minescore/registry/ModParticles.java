package com.minescore.registry;

import com.minescore.platform.RegistryHelper;
import java.util.function.Supplier;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

/**
 * MineScore's own particles (SCOPE.md §13.5), drawn in tools/textures.py. The client's {@code MineScoreParticle}
 * gives each its motion. A note's x speed picks its band colour: 0 bass, 1 mid, 2 treble.
 */
public final class ModParticles {
    public static Supplier<SimpleParticleType> NOTE, SHAVING, SHARD, SPARK, SMOKE;

    private ModParticles() {}

    public static void register(RegistryHelper r) {
        NOTE = particle(r, "note");
        SHAVING = particle(r, "shaving");
        SHARD = particle(r, "shard");
        SPARK = particle(r, "spark");
        SMOKE = particle(r, "smoke");
    }

    private static Supplier<SimpleParticleType> particle(RegistryHelper r, String name) {
        return r.register(Registries.PARTICLE_TYPE, name, () -> new SimpleParticleType(false) {});
    }
}
