package com.minescore.wiring;

import com.minescore.registry.ModItems;
import net.minecraft.world.item.Item;

/** Speaker wire sizes. Each one also names the frequency band carried by matching jacks. */
public enum Gauge {
    G12(12),
    G16(16),
    G20(20);

    private final int awg;

    Gauge(int awg) {
        this.awg = awg;
    }

    public int awg() {
        return awg;
    }

    public Item item() {
        return switch (this) {
            case G12 -> ModItems.SPEAKER_WIRE_12.get();
            case G16 -> ModItems.SPEAKER_WIRE_16.get();
            case G20 -> ModItems.SPEAKER_WIRE_20.get();
        };
    }
}
