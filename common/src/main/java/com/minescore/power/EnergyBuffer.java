package com.minescore.power;

import net.minecraft.nbt.CompoundTag;

/**
 * A block's store of FE (SCOPE.md §6.3). The loader's energy API (Forge Energy on Forge) fills it; the block
 * spends it. It also remembers how much came in over the last whole second, so a source can tell when its
 * power has been switched off.
 */
public final class EnergyBuffer {
    private final int capacity;
    private int stored;
    private int receivedThisSecond;
    private int receivedLastSecond;

    public EnergyBuffer(int capacity) {
        this.capacity = capacity;
    }

    public int stored() {
        return stored;
    }

    public int capacity() {
        return capacity;
    }

    /** Takes in up to {@code max} FE; returns how much fitted. */
    public int receive(int max, boolean simulate) {
        int accepted = Math.max(0, Math.min(max, capacity - stored));
        if (!simulate) {
            stored += accepted;
            receivedThisSecond += accepted;
        }
        return accepted;
    }

    /** Spends up to {@code max} FE; returns how much there was. */
    public int extract(int max, boolean simulate) {
        int taken = Math.max(0, Math.min(max, stored));
        if (!simulate) {
            stored -= taken;
        }
        return taken;
    }

    /** Spends exactly {@code amount} FE if it's all there, else nothing. */
    public boolean take(int amount) {
        if (stored < amount) {
            return false;
        }
        stored -= amount;
        return true;
    }

    /** Call once a second: closes the second for {@link #receivedLastSecond()}. */
    public void endSecond() {
        receivedLastSecond = receivedThisSecond;
        receivedThisSecond = 0;
    }

    /** FE that came in over the last whole second; 0 means its power is off. */
    public int receivedLastSecond() {
        return receivedLastSecond;
    }

    public void save(CompoundTag tag) {
        tag.putInt("Energy", stored);
    }

    public void load(CompoundTag tag) {
        stored = Math.max(0, Math.min(capacity, tag.getInt("Energy")));
    }
}
