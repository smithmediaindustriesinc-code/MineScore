package com.minescore.audio;

import net.minecraft.world.phys.Vec3;

/**
 * How loud a speaker is toward a listener, from the sound's path alone (not where the listener looks).
 *
 * <p>The path runs from the centre of the speaker's front face to the listener's head. While it leaves
 * through the front, volume eases from 100% straight ahead to {@link #SIDE} side-on. Once it has to pass
 * through the speaker's own cabinet to get out the back, it drops quickly to {@code behind} and stays
 * there, as a cabinet muffles what comes out of its back.
 */
public final class Directivity {
    /** Volume heard exactly side-on, where the path just grazes the front face. */
    public static final float SIDE = 0.65f;
    /** How far past side-on, in degrees, the drop through the cabinet takes. */
    private static final double MUFFLE_DEGREES = 25;

    private Directivity() {}

    /**
     * @param front     centre of the speaker's front face
     * @param direction unit vector the front faces
     * @param listener  the listener's head
     * @param behind    volume once the path goes straight through the cabinet (0.3 by default)
     */
    public static float gain(Vec3 front, Vec3 direction, Vec3 listener, float behind) {
        Vec3 path = listener.subtract(front);
        double length = path.length();
        if (length < 1e-6 || direction.lengthSqr() < 1e-6) {
            return 1; // standing on it, or a source with no front (a jukebox)
        }
        double cos = Math.max(-1, Math.min(1, path.dot(direction) / length));
        double degrees = Math.toDegrees(Math.acos(cos));
        if (degrees <= 90) {
            return (float) (1 - (1 - SIDE) * degrees / 90); // leaves through the front
        }
        double t = Math.min(1, (degrees - 90) / MUFFLE_DEGREES); // has to go through the cabinet
        t = t * t * (3 - 2 * t);
        return (float) (SIDE + (behind - SIDE) * t);
    }
}
