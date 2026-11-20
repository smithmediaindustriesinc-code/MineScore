package com.minescore.power;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Create rotation settings (SCOPE.md §3.1), bound to the config menu's (§9). */
public final class KineticSettings {
    /** The fastest a Lathe may turn: above it, the record being cut shatters. A cut at this speed takes 3 s. */
    public static IntSupplier maxRpm = () -> 64;
    /** Stress the Lathe puts on its network for each RPM. */
    public static DoubleSupplier stressPerRpm = () -> 1;

    private KineticSettings() {}

    /** Ticks a cut takes turned at {@code rpm}: 3 × max RPM ÷ RPM seconds (64 RPM: 3 s; 1 RPM: 192 s). */
    public static double cutTicks(float rpm) {
        return 3.0 * maxRpm.getAsInt() / rpm * 20;
    }
}
