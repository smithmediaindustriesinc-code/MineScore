package com.minescore.wiring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Works out what a source drives, from its cables alone (so the client can do it too). */
public final class SpeakerSystems {
    private SpeakerSystems() {}

    /**
     * A speaker that will play, and the path its sound takes to get there.
     *
     * @param chainIndex     speakers before it since the last hub or line amp (0 for one wired straight to one)
     * @param channelManager the Channel Manager it plays through, if any
     * @param mainAmp        the main Amplifier it plays through, if any
     * @param lineAmps       Line Amplifiers on its speaker line, nearest the source first
     * @param branch         the speaker its chain starts with: the one wired straight to the last hub, whose
     *                       trim scales the whole chain (itself, for a speaker wired straight to the hub)
     */
    public record Feed(BlockPos speaker, Gauge band, int chainIndex, @Nullable BlockPos channelManager, @Nullable BlockPos mainAmp,
                       List<BlockPos> lineAmps, BlockPos branch) {
        public Feed {
            lineAmps = List.copyOf(lineAmps);
        }

        /** Wired straight to the last hub (not daisy-chained, not after a line amp): it has a trim of its own. */
        public boolean direct() {
            return chainIndex == 0 && lineAmps.isEmpty();
        }
    }

    /**
     * Every speaker that plays when {@code start} plays. A speaker plays only if its band has a cable at
     * every step back to the source (SCOPE.md §5.9), so this follows band cables only.
     *
     * @param cablesAt cables with an end at a block
     * @param start    usually a source; a speaker for Bluetooth, or a Channel Manager to list its speakers
     */
    public static List<Feed> speakers(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos start) {
        record Step(BlockPos pos, Gauge band, int chainIndex, @Nullable BlockPos cm, @Nullable BlockPos amp, List<BlockPos> lineAmps,
                    @Nullable BlockPos branch) {}
        List<Feed> feeds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Step> queue = new ArrayDeque<>();
        for (Gauge band : Gauge.values()) {
            queue.add(new Step(start, band, -1, null, null, List.of(), null));
        }
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (!seen.add(step.pos().asLong() + ":" + step.band())) {
                continue;
            }
            for (Cable cable : cablesAt.apply(step.pos())) {
                if (!cable.from().pos().equals(step.pos()) || cable.signal().band() != step.band()) {
                    continue;
                }
                BlockPos to = cable.to().pos();
                switch (cable.toKind()) {
                    case SPEAKER -> {
                        int index = step.chainIndex() + 1;
                        BlockPos branch = step.branch() != null ? step.branch() : to;
                        feeds.add(new Feed(to, step.band(), index, step.cm(), step.amp(), step.lineAmps(), branch));
                        queue.add(new Step(to, step.band(), index, step.cm(), step.amp(), step.lineAmps(), branch));
                    }
                    case CHANNEL_MANAGER -> queue.add(new Step(to, step.band(), -1, to, step.amp(), step.lineAmps(), null));
                    case MEU -> queue.add(new Step(to, step.band(), -1, step.cm(), step.amp(), step.lineAmps(), null));
                    case AMPLIFIER -> {
                        if (cable.fromKind() == BlockKind.SPEAKER) { // a Line Amplifier: the chain starts again after it
                            List<BlockPos> lines = new ArrayList<>(step.lineAmps());
                            lines.add(to);
                            queue.add(new Step(to, step.band(), -1, step.cm(), step.amp(), lines, step.branch()));
                        } else {
                            queue.add(new Step(to, step.band(), -1, step.cm(), to, step.lineAmps(), null));
                        }
                    }
                    default -> { }
                }
            }
        }
        return feeds;
    }

    /** Every block wired into {@code source}'s system, downstream of it, including cable anchors. */
    public static Set<BlockPos> blocks(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos source) {
        Set<BlockPos> result = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(source));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!result.add(pos)) {
                continue;
            }
            for (Cable cable : cablesAt.apply(pos)) {
                if (cable.from().pos().equals(pos)) {
                    result.addAll(cable.anchors());
                    queue.add(cable.to().pos());
                }
            }
        }
        return result;
    }

    /** Every block of one kind downstream of {@code source}, nearest first. */
    public static List<BlockPos> downstream(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos source, BlockKind kind) {
        Set<BlockPos> result = new LinkedHashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(source));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos)) {
                continue;
            }
            for (Cable cable : cablesAt.apply(pos)) {
                if (cable.from().pos().equals(pos)) {
                    if (cable.toKind() == kind) {
                        result.add(cable.to().pos());
                    }
                    queue.add(cable.to().pos());
                }
            }
        }
        return new ArrayList<>(result);
    }

    /** The first block of a kind downstream of {@code source}, or null. */
    public static @Nullable BlockPos first(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos source, BlockKind kind) {
        List<BlockPos> all = downstream(cablesAt, source, kind);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The source feeding {@code pos} (itself, if it is one), following cables back; null if it has none. */
    public static @Nullable BlockPos sourceOf(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(pos));
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (!seen.add(at)) {
                continue;
            }
            for (Cable cable : cablesAt.apply(at)) {
                if (cable.to().pos().equals(at)) {
                    if (cable.fromKind().isSource()) {
                        return cable.from().pos();
                    }
                    queue.add(cable.from().pos());
                }
            }
        }
        return null;
    }

    /** Whether an Amplifier is a Line Amplifier: one fed by a speaker. */
    public static boolean isLineAmp(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos amp) {
        return cablesAt.apply(amp).stream().anyMatch(c -> c.to().pos().equals(amp) && c.fromKind() == BlockKind.SPEAKER);
    }

    /**
     * How loud a main Amplifier may go, set by the thinnest wire linking the hubs that lead to it (SCOPE.md
     * §5.7): the orange Mixer → Amplifier link, or the teal Channel Manager → Amplifier link plus the purple
     * Mixer → Channel Manager link before it. 400% on 12 ga, 300% on 16 ga, 200% on 20 ga; 100% unlinked.
     */
    public static float boostCap(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos amp) {
        return capFor(thinnestLink(cablesAt, amp));
    }

    /** The gauge of the thinnest hub link wire on the way to {@code hub}, or 0 if it isn't linked. */
    public static int thinnestLink(Function<BlockPos, Collection<Cable>> cablesAt, BlockPos hub) {
        int thinnest = 0;
        Set<BlockPos> seen = new HashSet<>();
        BlockPos at = hub;
        while (at != null && seen.add(at)) {
            BlockPos from = null;
            for (Cable cable : cablesAt.apply(at)) {
                if (cable.to().pos().equals(at) && cable.signal().isLink()) {
                    thinnest = Math.max(thinnest, cable.gauge().awg());
                    from = cable.from().pos();
                }
            }
            at = from;
        }
        return thinnest;
    }

    /** The boost cap for the thinnest wire gauge, or 1 (100%) for 0 (no wire). */
    public static float capFor(int awg) {
        return switch (awg) {
            case 12 -> 4;
            case 16 -> 3;
            case 20 -> 2;
            default -> 1;
        };
    }
}
