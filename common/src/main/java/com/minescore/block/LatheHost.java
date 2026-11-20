package com.minescore.block;

import com.minescore.power.EnergyBuffer;
import com.minescore.power.Powered;

/**
 * A block entity that is a Disc Cutting Lathe: the plain one, or the one that takes Create rotation. Both
 * hold a {@link LatheCore}, which does the work.
 */
public interface LatheHost extends Powered {
    LatheCore lathe();

    @Override
    default EnergyBuffer energy() {
        return lathe().energy();
    }

    /** Whether it can take rotation at all (Create is installed). */
    default boolean kinetic() {
        return false;
    }

    /** How fast it's turned, in RPM (sign ignored); 0 when it isn't. */
    default float rpm() {
        return 0;
    }
}
