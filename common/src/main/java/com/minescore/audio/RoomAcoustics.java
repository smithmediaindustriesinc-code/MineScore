package com.minescore.audio;

import com.minescore.wiring.Gauge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Room Sound Physics (SCOPE.md §7.4): a speaker in a sealed room sounds fuller than one in the open, like
 * a car stereo that loses its punch when a door opens. Rays cast out from the speaker in every direction
 * find how much of the space round it is closed in; each gap lets sound escape.
 */
public final class RoomAcoustics {
    /** Directions checked, spread evenly over a sphere. */
    public static final int RAYS = 64;
    /** How far each ray looks for a wall. Spaces bigger than this count as open air. */
    public static final double RANGE = 16;
    /** A ray stopped by this much loss (one stone block's worth, near enough) counts as fully closed. */
    public static final double SEALED_DB = 6;
    /** How fast gaps spoil the room: with a quarter of the rays escaping, the effect is gone. */
    public static final double LEAK_SCALE = 4;
    /** Extra loudness in a fully sealed room, before the band scaling. */
    public static final double ROOM_DB = 4;

    private static final Vec3[] DIRECTIONS = new Vec3[RAYS];

    static {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < RAYS; i++) {
            double y = 1 - 2 * (i + 0.5) / RAYS;
            double r = Math.sqrt(1 - y * y);
            DIRECTIONS[i] = new Vec3(Math.cos(golden * i) * r, y, Math.sin(golden * i) * r);
        }
    }

    private RoomAcoustics() {}

    /** How closed in the space round {@code from} is: 0 in open air, 1 when every ray meets a solid wall. */
    public static double enclosure(BlockGetter level, Vec3 from, BlockPos speaker) {
        double closed = 0;
        for (Vec3 direction : DIRECTIONS) {
            double loss = 0;
            for (BlockPos pos : Occlusion.blocksBetween(from, from.add(direction.scale(RANGE)))) {
                if (!pos.equals(speaker)) {
                    loss += Occlusion.blockDb(level, pos);
                    if (loss >= SEALED_DB) {
                        break;
                    }
                }
            }
            closed += Math.min(1, loss / SEALED_DB);
        }
        return closed / RAYS;
    }

    /** How well sealed a space is, 0–1, from its enclosure: small gaps matter a lot. */
    public static double seal(double enclosure) {
        return Math.max(0, 1 - LEAK_SCALE * (1 - enclosure));
    }

    /** Sealed rooms help bass most: Subwoofer ×2, Middle Speaker ×1, Tweeter ×0.6. */
    public static double bandScale(@Nullable Gauge band) {
        if (band == null) {
            return 1;
        }
        return switch (band) {
            case G12 -> 2;
            case G16 -> 1;
            case G20 -> 0.6;
        };
    }

    /** The boost in dB a speaker gets from its room. */
    public static double boostDb(double seal, @Nullable Gauge band) {
        return ROOM_DB * seal * bandScale(band);
    }

    /**
     * How much of the room's boost reaches a listener: all of it inside the room with the speaker (nothing
     * in the way), fading to none once walls stand between them.
     */
    public static double listenerShare(double pathLossDb) {
        return Math.max(0, 1 - pathLossDb / 12);
    }
}
