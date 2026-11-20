package com.minescore.power;

/** A source block (Mixer, Desk, Vinyl Player): its system's power comes in here (SCOPE.md §5.3). */
public interface PowerSource extends Powered {
    SourcePower power();

    @Override
    default EnergyBuffer energy() {
        return power().buffer();
    }
}
