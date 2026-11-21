package com.minescore.power;

/** A block entity that takes FE from outside: the loader module hands its buffer to the energy API. */
public interface Powered {
    EnergyBuffer energy();

    /** Whether cables and pipes may fill it right now: never in None mode (SCOPE.md §6.2). */
    default boolean acceptsEnergy() {
        return PowerSettings.energyMode();
    }
}
