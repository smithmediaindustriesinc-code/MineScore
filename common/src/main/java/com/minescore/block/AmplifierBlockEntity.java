package com.minescore.block;

import com.minescore.audio.EqCurve;
import com.minescore.power.EnergyBuffer;
import com.minescore.power.PowerSettings;
import com.minescore.power.PowerSource;
import com.minescore.power.Powered;
import com.minescore.registry.ModBlockEntities;
import com.minescore.wiring.Gauge;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An Amplifier's master volume and 10-band EQ (SCOPE.md §5.7). How far it may boost depends on its wiring.
 * Inside a system it draws from the source; on its own (one wired only to a Lathe) it takes FE itself (S6).
 */
public class AmplifierBlockEntity extends SettingsBlockEntity implements Powered, RedstoneHub {
    /** The most any Amplifier boosts, on 12 ga wire. */
    public static final float MAX_MASTER = 4;

    private final EnergyBuffer energy = new EnergyBuffer(4_000);
    /** The EQ bands each face's redstone controls (§7.4), 0-based: back 1–3, left 4–5, right 6–7, front 8–10. */
    static final int[][] FACE_BANDS = {{0, 1, 2}, {3, 4}, {5, 6}, {7, 8, 9}};
    /** The speaker type each group feeds: Subwoofers, Middle Speakers twice, Tweeters. */
    private static final Gauge[] FACE_SPEAKERS = {Gauge.G12, Gauge.G16, Gauge.G16, Gauge.G20};
    private boolean redstoneControl = RedstoneHub.controlByDefault();
    /** Redstone on the back, left, right and front faces (0 = none), and on the bottom. */
    private final int[] redstoneSides = new int[4];
    private int redstoneBottom;

    private float master = 1;
    private EqCurve eq = EqCurve.FLAT;

    public AmplifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMPLIFIER.get(), pos, state);
    }

    /** The volume set on its dial, 0–400%. What reaches the speakers is also held to the system's boost cap. */
    public float master() {
        return master;
    }

    /** The EQ it plays: its screen's, with any redstone overrides on top. */
    public EqCurve eq() {
        return redstoneEq(eq, redstoneSides, redstoneBottom);
    }

    /** Its screen's EQ, as set by hand. */
    public EqCurve settingEq() {
        return eq;
    }

    /** The level a redstone signal asks for, in dB: 15 = 400%. Not yet held to the EQ's range. */
    private static double rawDbForSignal(int signal) {
        return 20 * Math.log10(signal / 15.0 * MAX_MASTER);
    }

    private static float dbForSignal(int signal) {
        return (float) Math.max(-EqCurve.RANGE, Math.min(EqCurve.RANGE, rawDbForSignal(signal)));
    }

    /**
     * The EQ with redstone applied (§7.4). The bottom face (1–15) sets the loudest band to signal ÷ 15 × 400%
     * and moves the rest with it, keeping their ratios to it from the screen's EQ (a band held at the EQ's
     * limit keeps its ratio for when it comes back). A side face (1–15) then sets its own bands to signal ÷
     * 15 × 400%. Signal 0 leaves the screen's values.
     */
    public static EqCurve redstoneEq(EqCurve gui, int[] sides, int bottom) {
        float[] gains = gui.gains();
        if (bottom > 0) {
            float loudest = Float.NEGATIVE_INFINITY;
            for (float g : gains) {
                loudest = Math.max(loudest, g);
            }
            float shift = (float) (rawDbForSignal(bottom) - loudest); // in dB: every band keeps its ratio to the loudest
            for (int i = 0; i < gains.length; i++) {
                gains[i] = Math.max(-EqCurve.RANGE, Math.min(EqCurve.RANGE, gains[i] + shift));
            }
        }
        for (int face = 0; face < FACE_BANDS.length; face++) {
            if (sides[face] > 0) {
                for (int band : FACE_BANDS[face]) {
                    gains[band] = dbForSignal(sides[face]);
                }
            }
        }
        return new EqCurve(gains);
    }

    @Override
    public boolean redstoneControl() {
        return redstoneControl;
    }

    @Override
    public void setRedstoneControl(boolean on) {
        redstoneControl = on;
        readRedstone();
        changed();
    }

    /** The face index (back, left, right, front) of a side, or -1 for the top and bottom. */
    private int faceIndex(Direction side) {
        BlockState state = getBlockState();
        Direction front = RedstoneHub.front(state);
        if (side == front.getOpposite()) {
            return 0;
        }
        if (side == RedstoneHub.left(state)) {
            return 1;
        }
        if (side == RedstoneHub.right(state)) {
            return 2;
        }
        return side == front ? 3 : -1;
    }

    @Override
    public void readRedstone() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean active = redstoneControl && RedstoneHub.feedsSpeakers(level, worldPosition);
        int[] sides = new int[4];
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int face = faceIndex(side);
            sides[face] = active ? RedstoneHub.signalFrom(level, worldPosition, side) : 0;
        }
        int bottom = active ? RedstoneHub.signalFrom(level, worldPosition, Direction.DOWN) : 0;
        if (!java.util.Arrays.equals(sides, redstoneSides) || bottom != redstoneBottom) {
            System.arraycopy(sides, 0, redstoneSides, 0, 4);
            redstoneBottom = bottom;
            changed();
        }
    }

    /**
     * Back, left, right and front: the loudest band of that face's group on the redstone scale (15 = 400%),
     * if a speaker type that group is for is wired after it; nothing otherwise, or on the top and bottom.
     */
    @Override
    public int comparatorSignal(Direction face) {
        int index = face.getAxis().isHorizontal() ? faceIndex(face) : -1;
        if (index < 0 || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        boolean wired = SpeakerSystems.speakers(WireNetwork.get(serverLevel)::endsAt, worldPosition).stream()
                .anyMatch(feed -> feed.band() == FACE_SPEAKERS[index]);
        if (!wired) {
            return 0;
        }
        float[] gains = eq().gains();
        float loudest = Float.NEGATIVE_INFINITY;
        for (int band : FACE_BANDS[index]) {
            loudest = Math.max(loudest, gains[band]);
        }
        double share = Math.pow(10, loudest / 20) / MAX_MASTER;
        return (int) Math.max(1, Math.min(15, Math.round(share * 15)));
    }

    @Override
    public EnergyBuffer energy() {
        return energy;
    }

    /** Only an Amplifier outside any system takes FE (S6); in one, its power comes from the source. */
    @Override
    public boolean acceptsEnergy() {
        return PowerSettings.energyMode() && standalone();
    }

    /** Not fed by any source. */
    public boolean standalone() {
        return level instanceof ServerLevel serverLevel && SpeakerSystems.sourceOf(WireNetwork.get(serverLevel)::endsAt, worldPosition) == null;
    }

    /**
     * Whether it has the power to work for a Lathe this tick, and pays for it when {@code draw}: on its own
     * it spends its hub cost from its own FE; in a system, its source must have power.
     */
    public boolean powered(boolean draw) {
        if (!PowerSettings.energyMode() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        BlockPos source = SpeakerSystems.sourceOf(WireNetwork.get(serverLevel)::endsAt, worldPosition);
        if (source == null) {
            int cost = PowerSettings.hubCost.getAsInt();
            if (!draw) {
                return energy.stored() >= cost;
            }
            boolean paid = energy.take(cost);
            setChanged();
            if (level.getGameTime() % 20 == 0) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); // its screen shows the FE
            }
            return paid;
        }
        return level.getBlockEntity(source) instanceof PowerSource powered && powered.power().budget() > 0;
    }

    public void setMaster(float master) {
        this.master = Mth.clamp(master, 0, MAX_MASTER);
        changed();
    }

    public void setEqBand(int band, float db) {
        if (band < 0 || band >= EqCurve.BANDS) {
            return;
        }
        float[] gains = eq.gains(); // the hand-set EQ, not redstone's
        gains[band] = db;
        eq = new EqCurve(gains);
        changed();
    }

    public void resetEq() {
        eq = EqCurve.FLAT;
        changed();
    }

    @Override
    protected void write(CompoundTag tag) {
        energy.save(tag);
        tag.putBoolean("RedstoneControl", redstoneControl);
        tag.putFloat("Master", master);
        int[] packed = new int[EqCurve.BANDS];
        float[] gains = eq.gains();
        for (int i = 0; i < packed.length; i++) {
            packed[i] = Float.floatToIntBits(gains[i]);
        }
        tag.putIntArray("Eq", packed);
    }

    @Override
    protected void read(CompoundTag tag) {
        energy.load(tag);
        if (tag.contains("RedstoneControl")) {
            redstoneControl = tag.getBoolean("RedstoneControl");
        }
        master = tag.contains("Master") ? Mth.clamp(tag.getFloat("Master"), 0, MAX_MASTER) : 1;
        int[] packed = tag.getIntArray("Eq");
        float[] gains = new float[EqCurve.BANDS];
        for (int i = 0; i < gains.length && i < packed.length; i++) {
            gains[i] = Float.intBitsToFloat(packed[i]);
        }
        eq = new EqCurve(gains);
    }
}
