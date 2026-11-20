package com.minescore.power;

import com.minescore.block.MeuBlockEntity;
import com.minescore.block.PortBlock;
import com.minescore.wiring.BlockKind;
import com.minescore.wiring.Cable;
import com.minescore.wiring.Gauge;
import com.minescore.wiring.SpeakerSystems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What a speaker system asks for in Energy mode, and what a budget gives it (SCOPE.md §5.3, §5.4, S1).
 *
 * <p>The hubs come first: with less than their total the system can't run. What's left powers the speakers
 * in chain order, nearest the source first, each at full volume. The first speaker the budget can't cover,
 * and every one after it, follows the daisy-chain curve instead; a Line Amplifier starts the curve again.
 */
public final class SystemPower {
    private SystemPower() {}

    /**
     * One speaker's share of the demand.
     *
     * @param group the Line Amplifier it plays after (null: straight from the source); each group has its own curve
     */
    public record Load(int cost, @Nullable BlockPos group) {}

    /** What a system asks for: its hubs, then its speakers in the order they're powered. */
    public record Demand(int hubs, List<Load> speakers) {
        public Demand {
            speakers = List.copyOf(speakers);
        }

        public int total() {
            return hubs + speakers.stream().mapToInt(Load::cost).sum();
        }
    }

    /**
     * What a budget gives a system.
     *
     * @param running whether the hubs are covered (it plays at all)
     * @param factors each speaker's volume from power, 0–1, in the demand's order
     */
    public record Allocation(boolean running, float[] factors) {
        public float factor(int speaker) {
            return speaker < factors.length ? factors[speaker] : 1;
        }
    }

    /** A speaker's cost: its base cost plus a step for each speaker before it in its chain. */
    public static int speakerCost(Gauge band, int chainIndex) {
        int base = switch (band) {
            case G12 -> PowerSettings.subwooferCost.getAsInt();
            case G16 -> PowerSettings.middleCost.getAsInt();
            case G20 -> PowerSettings.tweeterCost.getAsInt();
        };
        return base + PowerSettings.chainStep.getAsInt() * Math.max(0, chainIndex);
    }

    /** What a source's system asks for. A Vinyl Player's has no hubs, so it has no minimum. */
    public static Demand demand(Level level, Function<BlockPos, Collection<Cable>> cablesAt, BlockPos source,
                                List<SpeakerSystems.Feed> feeds) {
        int hubs = 0;
        if (kind(level, source) != BlockKind.VINYL_PLAYER) {
            for (BlockPos pos : SpeakerSystems.blocks(cablesAt, source)) {
                BlockKind kind = kind(level, pos);
                if (kind != null && kind.isHub()) {
                    hubs += PowerSettings.hubCost.getAsInt();
                    if (kind == BlockKind.MEU && level.getBlockEntity(pos) instanceof MeuBlockEntity meu && !meu.bypass()) {
                        hubs += PowerSettings.meuCardCost.getAsInt() * meu.chain().activeCount();
                    }
                }
            }
        }
        List<Load> speakers = new ArrayList<>();
        for (SpeakerSystems.Feed feed : feeds) {
            BlockPos group = feed.lineAmps().isEmpty() ? null : feed.lineAmps().get(feed.lineAmps().size() - 1);
            speakers.add(new Load(speakerCost(feed.band(), feed.chainIndex()), group));
        }
        return new Demand(hubs, speakers);
    }

    private static @Nullable BlockKind kind(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PortBlock block ? block.layout().kind() : null;
    }

    /** Shares {@code budget} FE/t out over a demand. None mode always gives everything. */
    public static Allocation allocate(Demand demand, int budget, boolean energyMode) {
        int count = demand.speakers().size();
        float[] factors = new float[count];
        if (!energyMode) {
            java.util.Arrays.fill(factors, 1);
            return new Allocation(true, factors);
        }
        if (budget < demand.hubs()) {
            return new Allocation(false, factors);
        }
        int left = budget - demand.hubs();
        int firstPast = count;
        for (int i = 0; i < count; i++) {
            int cost = demand.speakers().get(i).cost();
            if (cost > left) {
                firstPast = i;
                break;
            }
            left -= cost;
            factors[i] = 1;
        }
        // Past the budget: a curve per group, counted across every branch in that group.
        Map<Object, Integer> totals = new HashMap<>();
        for (int i = firstPast; i < count; i++) {
            totals.merge(group(demand.speakers().get(i)), 1, Integer::sum);
        }
        Map<Object, Integer> positions = new HashMap<>();
        for (int i = firstPast; i < count; i++) {
            Object group = group(demand.speakers().get(i));
            int position = positions.merge(group, 1, Integer::sum);
            factors[i] = curve(position, totals.get(group));
        }
        return new Allocation(true, factors);
    }

    private static final Object NO_LINE_AMP = new Object();

    private static Object group(Load load) {
        return load.group() == null ? NO_LINE_AMP : load.group();
    }

    /**
     * The daisy-chain curve (SCOPE.md §5.4) for the speaker at {@code position} (from 1) of {@code count}
     * speakers past the budget: each at 95% of the one before, down to 10%. Once that many reach the floor,
     * each extra speaker lowers the first by 5 points, the ones between evenly spaced; once even the first
     * is at 10%, they all share 10% × N₀ ÷ n.
     */
    public static float curve(int position, int count) {
        float loss = PowerSettings.chainLoss();
        float keep = 1 - loss;
        float floor = PowerSettings.chainFloor();
        int reachFloor = (int) Math.ceil(Math.log(floor) / Math.log(keep)); // the first position at the floor: 45
        if (count <= reachFloor) {
            return Math.max(floor, (float) Math.pow(keep, position));
        }
        int extra = count - reachFloor;
        float first = keep - loss * extra;
        if (first > floor + 1e-6f) {
            return first + (floor - first) * (position - 1) / (count - 1);
        }
        int allAtFloor = reachFloor + Math.round((keep - floor) / loss); // N₀
        return floor * allAtFloor / count;
    }
}
