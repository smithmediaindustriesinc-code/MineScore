package com.minescore.wiring;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * What a jack carries. Band signals carry one frequency band. Link signals connect two hubs; on an
 * output jack they name the kind of block they feed, on an input jack the kind of block that feeds them.
 */
public enum Signal {
    BAND_12(Gauge.G12),
    BAND_16(Gauge.G16),
    BAND_20(Gauge.G20),
    LINK_SOURCE(null),
    LINK_CHANNEL_MANAGER(null),
    LINK_AMPLIFIER(null),
    LINK_MEU(null),
    LINK_LATHE(null);

    private final @Nullable Gauge band;

    Signal(@Nullable Gauge band) {
        this.band = band;
    }

    public @Nullable Gauge band() {
        return band;
    }

    public boolean isBand() {
        return band != null;
    }

    public boolean isLink() {
        return band == null;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Signal byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
