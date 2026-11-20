package com.minescore.wiring;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** The role a block plays in a speaker system. */
public enum BlockKind {
    /** Audio Mixer, Writer's Desk. */
    SOURCE(Signal.LINK_SOURCE),
    /** The Multi-Effects Unit: straight after a source, before any Channel Manager or Amplifier. */
    MEU(Signal.LINK_MEU),
    CHANNEL_MANAGER(Signal.LINK_CHANNEL_MANAGER),
    AMPLIFIER(Signal.LINK_AMPLIFIER),
    SPEAKER(null),
    LATHE(Signal.LINK_LATHE),
    /** A source that feeds speakers directly: no Channel Manager or Amplifier, no links. */
    VINYL_PLAYER(null);

    private final @Nullable Signal linkSignal;

    BlockKind(@Nullable Signal linkSignal) {
        this.linkSignal = linkSignal;
    }

    /** The link signal that names this kind of block, or null if it can't be linked. */
    public @Nullable Signal linkSignal() {
        return linkSignal;
    }

    public boolean isHub() {
        return this == SOURCE || this == MEU || this == CHANNEL_MANAGER || this == AMPLIFIER;
    }

    /**
     * Whether a band output on this kind of block may feed a band input on {@code target}. Follows the
     * allowed chains: Source → MEU → Channel Manager → Amplifier → Speakers, any step skippable, speakers
     * daisy-chain, and a speaker may feed a line amplifier. An Amplifier never feeds a Channel Manager,
     * but may feed a Disc Cutting Lathe to speed up cutting.
     */
    public boolean canFeed(BlockKind target) {
        return switch (this) {
            case SOURCE -> target == MEU || target == CHANNEL_MANAGER || target == AMPLIFIER || target == SPEAKER;
            case MEU -> target == CHANNEL_MANAGER || target == AMPLIFIER || target == SPEAKER;
            case CHANNEL_MANAGER -> target == AMPLIFIER || target == SPEAKER;
            case AMPLIFIER -> target == SPEAKER || target == LATHE;
            case SPEAKER -> target == SPEAKER || target == AMPLIFIER;
            case VINYL_PLAYER -> target == SPEAKER;
            case LATHE -> false;
        };
    }

    /** Where a speaker system's sound starts. */
    public boolean isSource() {
        return this == SOURCE || this == VINYL_PLAYER;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BlockKind byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
