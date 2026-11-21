package com.minescore.wiring;

import com.minescore.wiring.WireWorld.Endpoint;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Decides whether two jacks may be connected (SCOPE.md §5.8). */
public final class WiringRules {
    private WiringRules() {}

    public sealed interface Result permits Ok, Error {}

    /**
     * @param swapped true when the player clicked the input jack first
     */
    public record Ok(Endpoint from, Endpoint to, boolean swapped) implements Result {}

    public record Error(String key, Component message) implements Result {}

    /**
     * @param first   the jack clicked first
     * @param second  the jack clicked second
     * @param gauge   the wire being laid
     * @param shifted whether the player held Shift on the second click
     */
    public static Result check(WireWorld world, PortRef first, PortRef second, Gauge gauge, boolean shifted) {
        if (first.pos().equals(second.pos())) {
            return error("same_block");
        }
        Endpoint a = world.endpoint(first);
        Endpoint b = world.endpoint(second);
        if (a == null || b == null) {
            return error("jack_gone");
        }
        if (a.port().output() == b.port().output()) {
            return error("need_in_and_out");
        }
        boolean swapped = !a.port().output();
        Endpoint from = swapped ? b : a;
        Endpoint to = swapped ? a : b;

        Result signal = checkSignals(from, to, gauge, shifted);
        if (signal != null) {
            return signal;
        }
        Result capacity = checkCapacity(world, from, to);
        if (capacity != null) {
            return capacity;
        }
        if (upstream(world, from.ref().pos()).contains(to.ref().pos())) {
            return error("loop");
        }
        Set<BlockPos> fromSources = sources(world, from.ref().pos(), from.kind());
        Set<BlockPos> toSources = sources(world, to.ref().pos(), to.kind());
        if (!fromSources.isEmpty() && !toSources.isEmpty() && !fromSources.equals(toSources)) {
            return error("other_system");
        }
        Result amps = checkAmplifiers(world, from, to);
        if (amps != null) {
            return amps;
        }
        if (to.kind() == BlockKind.LATHE) {
            // A Lathe prints the MEU's effects, or cuts faster with an Amplifier: not both at once.
            BlockKind other = from.kind() == BlockKind.MEU ? BlockKind.AMPLIFIER : BlockKind.MEU;
            if (inputs(world, to.ref().pos()).stream().anyMatch(c -> c.fromKind() == other)) {
                return error("lathe_meu_or_amp");
            }
        }
        return new Ok(from, to, swapped);
    }

    private static Result checkSignals(Endpoint from, Endpoint to, Gauge gauge, boolean shifted) {
        Signal out = from.port().signal();
        Signal in = to.port().signal();
        boolean speaker = from.kind() == BlockKind.SPEAKER || to.kind() == BlockKind.SPEAKER;
        if (speaker && shifted) {
            return error("no_shift_for_speakers");
        }
        if (!speaker && !shifted) {
            return error("shift_for_hubs");
        }
        if (to.kind() == BlockKind.LATHE && from.kind() != BlockKind.AMPLIFIER && from.kind() != BlockKind.MEU) {
            return error("lathe_needs_amp");
        }
        if (in.isLink() || out.isLink()) {
            boolean matches = in.isLink() && out.isLink() && out == to.kind().linkSignal() && in == from.kind().linkSignal();
            return matches ? null : error("link_mismatch");
        }
        if (out != in) {
            return error("band_mismatch");
        }
        if (!from.kind().canFeed(to.kind())) {
            return error("not_allowed", kindName(from.kind()), kindName(to.kind()));
        }
        if (speaker && gauge != out.band()) {
            return error("wrong_gauge", out.band().awg());
        }
        return null;
    }

    private static Result checkCapacity(WireWorld world, Endpoint from, Endpoint to) {
        if (!cablesAt(world, to.ref()).isEmpty()) {
            return error("jack_in_use");
        }
        List<Cable> fromCables = cablesAt(world, from.ref());
        boolean feedsSpeakers = fromCables.stream().anyMatch(c -> c.toKind() == BlockKind.SPEAKER);
        boolean feedsOther = fromCables.stream().anyMatch(c -> c.toKind() != BlockKind.SPEAKER);
        if (to.kind() == BlockKind.SPEAKER) {
            return feedsOther ? error("jack_feeds_hub") : null;
        }
        if (feedsSpeakers) {
            return error("jack_feeds_speakers");
        }
        return feedsOther ? error("jack_busy") : null;
    }

    /**
     * An Amplifier is a main amp (fed by a source or Channel Manager) or a line amp (fed by a speaker,
     * handling one band only), never both, and a system has at most one main amp.
     */
    private static Result checkAmplifiers(WireWorld world, Endpoint from, Endpoint to) {
        Gauge band = from.port().signal().band();
        if (to.kind() == BlockKind.AMPLIFIER) {
            boolean line = from.kind() == BlockKind.SPEAKER;
            List<Cable> inputs = inputs(world, to.ref().pos());
            boolean hasLine = inputs.stream().anyMatch(c -> c.fromKind() == BlockKind.SPEAKER);
            boolean hasMain = inputs.stream().anyMatch(c -> c.fromKind() != BlockKind.SPEAKER);
            if ((line && hasMain) || (!line && hasLine)) {
                return error("amp_role");
            }
            if (line && !allOnBand(world, to.ref().pos(), band)) {
                return error("line_amp_band");
            }
            if (!line) {
                Set<BlockPos> mains = new HashSet<>();
                mains.add(to.ref().pos());
                Set<BlockPos> upstream = upstream(world, from.ref().pos());
                upstream.add(from.ref().pos());
                upstream.forEach(pos -> collectMainAmps(world, pos, mains));
                collectMainAmps(world, to.ref().pos(), mains);
                if (mains.size() > 1) {
                    return error("one_main_amp");
                }
            }
        }
        if (from.kind() == BlockKind.AMPLIFIER) {
            boolean isLine = inputs(world, from.ref().pos()).stream().anyMatch(c -> c.fromKind() == BlockKind.SPEAKER);
            if (isLine && !allOnBand(world, from.ref().pos(), band)) {
                return error("line_amp_band");
            }
        }
        return null;
    }

    private static boolean allOnBand(WireWorld world, BlockPos amp, Gauge band) {
        return world.cablesAt(amp).stream().allMatch(c -> c.signal().band() == band);
    }

    /** Adds every main amp at or downstream of {@code start}. */
    private static void collectMainAmps(WireWorld world, BlockPos start, Set<BlockPos> mains) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos)) {
                continue;
            }
            for (Cable c : world.cablesAt(pos)) {
                if (c.from().pos().equals(pos)) {
                    if (c.toKind() == BlockKind.AMPLIFIER && c.fromKind() != BlockKind.SPEAKER) {
                        mains.add(c.to().pos());
                    }
                    queue.add(c.to().pos());
                }
            }
        }
    }

    /** Every block that feeds {@code pos}, directly or through others. */
    static Set<BlockPos> upstream(WireWorld world, BlockPos pos) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(pos));
        while (!queue.isEmpty()) {
            for (Cable c : inputs(world, queue.poll())) {
                if (seen.add(c.from().pos())) {
                    queue.add(c.from().pos());
                }
            }
        }
        return seen;
    }

    /** The sources feeding {@code pos}, including itself if it is one. */
    private static Set<BlockPos> sources(WireWorld world, BlockPos pos, BlockKind kind) {
        Set<BlockPos> result = new HashSet<>();
        if (kind.isSource()) {
            result.add(pos);
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(pos));
        while (!queue.isEmpty()) {
            for (Cable c : inputs(world, queue.poll())) {
                if (c.fromKind().isSource()) {
                    result.add(c.from().pos());
                }
                if (seen.add(c.from().pos())) {
                    queue.add(c.from().pos());
                }
            }
        }
        return result;
    }

    private static List<Cable> inputs(WireWorld world, BlockPos pos) {
        return world.cablesAt(pos).stream().filter(c -> c.to().pos().equals(pos)).toList();
    }

    private static List<Cable> cablesAt(WireWorld world, PortRef port) {
        return world.cablesAt(port.pos()).stream().filter(c -> c.usesPort(port)).toList();
    }

    private static Component kindName(BlockKind kind) {
        return Component.translatable("kind.minescore." + kind.id());
    }

    private static Error error(String key, Object... args) {
        return new Error(key, Component.translatable("msg.minescore.wire." + key, args));
    }
}
