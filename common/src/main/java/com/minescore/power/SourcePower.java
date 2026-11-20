package com.minescore.power;

import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * A source's power (SCOPE.md §5.3): the system's one power input. While the system plays, it draws what
 * the system asks for, up to the cap, every tick; its budget is what it actually managed to draw over the
 * last second. It also notes when its power was last switched off, which resets a system after a short.
 */
public final class SourcePower {
    /** Twenty seconds of a system at the default cap. */
    public static final int CAPACITY = 10_000;

    private final EnergyBuffer buffer = new EnergyBuffer(CAPACITY);
    private int drawnThisSecond;
    private int ticksPlayedThisSecond;
    /** FE/t the system had over the last second, or -1 before the first second has been measured. */
    private int budget = -1;
    private int demand;
    private int hubs;
    /** Game time of the last whole second in which no FE came in. */
    private long lastOff = Long.MIN_VALUE;
    /** On clients: whether the server runs in Energy mode. */
    private boolean energyMode = true;

    public EnergyBuffer buffer() {
        return buffer;
    }

    /** FE/t the system can use. Before the first measured second: the cap if it has any FE stored, else none. */
    public int budget() {
        return budget >= 0 ? budget : buffer.stored() > 0 ? PowerSettings.systemCap.getAsInt() : 0;
    }

    /** Whether its system has enough power to run: at least what its hubs need (a Vinyl Player's: any). */
    public boolean running() {
        return !PowerSettings.energyMode() || budget() >= Math.max(1, hubs);
    }

    /** FE/t the system asks for. */
    public int demand() {
        return demand;
    }

    /** FE/t the system needs to run at all: its hubs. */
    public int minimum() {
        return hubs;
    }

    /** Whether power counts (Energy mode). On clients, the server's mode. */
    public boolean energyMode() {
        return energyMode;
    }

    /** True if the source's power was off for a whole second after game time {@code tick}. */
    public boolean switchedOffSince(long tick) {
        return lastOff > tick;
    }

    /** Every server tick. True when the budget or demand changed, so the system's volumes need working out again. */
    public boolean tick(ServerLevel level, BlockPos source, boolean playing) {
        boolean wasEnergy = energyMode;
        energyMode = PowerSettings.energyMode();
        if (!energyMode) {
            return wasEnergy;
        }
        int want = Math.min(demand, PowerSettings.systemCap.getAsInt());
        if (playing) {
            drawnThisSecond += buffer.extract(want, false);
            ticksPlayedThisSecond++;
        }
        if (level.getGameTime() % 20 != 0) {
            return false;
        }
        buffer.endSecond();
        if (buffer.receivedLastSecond() == 0) {
            lastOff = level.getGameTime();
        }
        WireNetwork network = WireNetwork.get(level);
        SystemPower.Demand next = SystemPower.demand(level, network::endsAt, source, SpeakerSystems.speakers(network::endsAt, source));
        int nextBudget;
        if (ticksPlayedThisSecond > 0) {
            nextBudget = drawnThisSecond / ticksPlayedThisSecond;
        } else { // idle: what it could give if it started now
            int nextWant = Math.min(next.total(), PowerSettings.systemCap.getAsInt());
            nextBudget = buffer.stored() >= nextWant ? nextWant : Math.min(nextWant, buffer.receivedLastSecond() / 20);
        }
        drawnThisSecond = 0;
        ticksPlayedThisSecond = 0;
        boolean changed = nextBudget != budget || next.total() != demand || next.hubs() != hubs;
        budget = nextBudget;
        demand = next.total();
        hubs = next.hubs();
        return changed;
    }

    public void save(CompoundTag tag) {
        buffer.save(tag);
    }

    public void load(CompoundTag tag) {
        buffer.load(tag);
    }

    /** What a source's screen shows. */
    public CompoundTag saveForClient() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Budget", budget());
        tag.putInt("Demand", demand);
        tag.putInt("Hubs", hubs);
        tag.putInt("Stored", buffer.stored());
        tag.putBoolean("EnergyMode", PowerSettings.energyMode());
        return tag;
    }

    public void loadForClient(CompoundTag tag) {
        budget = tag.getInt("Budget");
        demand = tag.getInt("Demand");
        hubs = tag.getInt("Hubs");
        energyMode = tag.getBoolean("EnergyMode");
        CompoundTag energy = new CompoundTag();
        energy.putInt("Energy", tag.getInt("Stored"));
        buffer.load(energy);
    }
}
