package com.minescore.audio;

import com.minescore.wiring.Gauge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Sound through blocks (SCOPE.md §8.2): every block on the straight path from a speaker to a listener
 * muffles it, harder blocks more. Bass gets through walls best.
 */
public final class Occlusion {
    public static final double BASE_DB = 3;
    public static final double PER_HARDNESS_DB = 4;
    public static final double BLOCK_CAP_DB = 24;
    public static final double TOTAL_CAP_DB = 48;
    public static final double FLUID_DB = 2;

    private Occlusion() {}

    /**
     * What one block takes off, in dB.
     *
     * @param hardness its mining hardness (negative for unbreakable)
     * @param coverage how much of the block is solid, 0–1
     * @param dampens  wool, carpet and the like, which lose twice as much
     */
    public static double blockDb(float hardness, double coverage, boolean dampens) {
        if (coverage <= 0) {
            return 0;
        }
        double loss = hardness < 0 ? BLOCK_CAP_DB : (BASE_DB + PER_HARDNESS_DB * hardness) * Math.min(1, coverage);
        if (dampens) {
            loss *= 2;
        }
        return Math.min(BLOCK_CAP_DB, loss);
    }

    /** How much more or less a band loses through walls: Subwoofer ×0.5, Middle Speaker ×1, Tweeter ×1.4. */
    public static double bandScale(@Nullable Gauge band) {
        if (band == null) {
            return 1;
        }
        return switch (band) {
            case G12 -> 0.5;
            case G16 -> 1;
            case G20 -> 1.4;
        };
    }

    /** dB lost along the path to the total, before the band scaling, capped at {@link #TOTAL_CAP_DB}. */
    public static double pathDb(BlockGetter level, Vec3 from, Vec3 to, BlockPos speaker) {
        double total = 0;
        for (BlockPos pos : blocksBetween(from, to)) {
            if (pos.equals(speaker)) {
                continue; // its own cabinet is handled by directivity
            }
            total += blockDb(level, pos);
            if (total >= TOTAL_CAP_DB) {
                return TOTAL_CAP_DB;
            }
        }
        return total;
    }

    /** The volume factor for a loss in dB. */
    public static float gain(double db) {
        return (float) Math.pow(10, -db / 20);
    }

    /** What the block at {@code pos} takes off, in dB. */
    static double blockDb(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        double fluid = state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA) ? FLUID_DB : 0;
        if (state.isAir()) {
            return fluid;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        double coverage = 0;
        for (AABB box : shape.toAabbs()) {
            coverage += box.getXsize() * box.getYsize() * box.getZsize();
        }
        return Math.max(fluid, blockDb(state.getDestroySpeed(level, pos), coverage, state.is(BlockTags.DAMPENS_VIBRATIONS)));
    }

    /** Every block the segment passes through, in order (a 3D grid walk). */
    public static List<BlockPos> blocksBetween(Vec3 from, Vec3 to) {
        List<BlockPos> result = new ArrayList<>();
        Vec3 d = to.subtract(from);
        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int endX = Mth.floor(to.x), endY = Mth.floor(to.y), endZ = Mth.floor(to.z);
        int stepX = (int) Math.signum(d.x), stepY = (int) Math.signum(d.y), stepZ = (int) Math.signum(d.z);
        double tDeltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1 / d.x);
        double tDeltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1 / d.y);
        double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1 / d.z);
        double tMaxX = stepX == 0 ? Double.MAX_VALUE : tDeltaX * (stepX > 0 ? x + 1 - from.x : from.x - x);
        double tMaxY = stepY == 0 ? Double.MAX_VALUE : tDeltaY * (stepY > 0 ? y + 1 - from.y : from.y - y);
        double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : tDeltaZ * (stepZ > 0 ? z + 1 - from.z : from.z - z);
        result.add(new BlockPos(x, y, z));
        for (int guard = 0; guard < 1024 && (x != endX || y != endY || z != endZ); guard++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > 1) {
                    break;
                }
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1) {
                    break;
                }
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > 1) {
                    break;
                }
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            result.add(new BlockPos(x, y, z));
        }
        return result;
    }
}
