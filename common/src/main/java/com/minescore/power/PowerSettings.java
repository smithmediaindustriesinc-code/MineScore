package com.minescore.power;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** Power settings (SCOPE.md §6.2–4), bound to the config menu's (§10). */
public final class PowerSettings {
    /** Energy mode (true, the default) or None mode, where nothing needs power and chains lose nothing. */
    public static BooleanSupplier energy = () -> true;
    /** The most FE/t one system draws. */
    public static IntSupplier systemCap = () -> 500;
    /** Every hub: Mixer, Desk, Channel Manager, Amplifier and Line Amplifier, and the MEU before its cards. */
    public static IntSupplier hubCost = () -> 50;
    /** Each Multi-Effects Unit card in use. */
    public static IntSupplier meuCardCost = () -> 10;
    public static IntSupplier tweeterCost = () -> 5;
    public static IntSupplier middleCost = () -> 10;
    public static IntSupplier subwooferCost = () -> 25;
    /** Each speaker further down a chain costs this much more than the one before it. */
    public static IntSupplier chainStep = () -> 5;
    /** The Disc Cutting Lathe while it cuts, copies or loads a song. */
    public static IntSupplier latheCost = () -> 20;

    /** Past the budget, each speaker plays this much quieter than the one before it (5%)… */
    public static float chainLoss() {
        return Math.max(0.001f, com.minescore.config.MineScoreConfig.CHAIN_LOSS.share());
    }

    /** …down to this (10%). */
    public static float chainFloor() {
        return Math.max(0.001f, Math.min(0.999f, com.minescore.config.MineScoreConfig.CHAIN_FLOOR.share()));
    }

    private PowerSettings() {}

    public static boolean energyMode() {
        return energy.getAsBoolean();
    }
}
