package com.minescore.block;

import com.minescore.playback.PlaybackManager;
import com.minescore.wiring.BlockKind;
import com.minescore.wiring.ModelRotation;
import com.minescore.wiring.Port;
import com.minescore.wiring.PortLayouts;
import com.minescore.wiring.WireNetwork;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A block with jacks (see {@code ports.json}). Breaking it removes its cables and drops their wire. */
public class PortBlock extends PlaceholderBlock {
    /** How far from a jack, in pixels, a look ray still counts as hitting it. */
    private static final double PICK_TOLERANCE = 0.6;
    /** Furthest a jack can be picked from, in pixels (a little past block reach). */
    private static final double PICK_RANGE = 16 * 8;

    private PortLayouts.Layout layout;

    public PortBlock(Properties properties) {
        super(properties);
    }

    public PortBlock(Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    public PortLayouts.Layout layout() {
        if (layout == null) {
            String path = BuiltInRegistries.BLOCK.getKey(this).getPath();
            layout = PortLayouts.get(path);
            if (layout == null) {
                throw new IllegalStateException("No jack layout for " + path);
            }
        }
        return layout;
    }

    /** How far the model is turned, clockwise from north seen from above, in degrees. */
    public double yAngle(BlockState state) {
        return ModelRotation.yRot(state.getValue(FACING));
    }

    /** How far the model is moved up (negative: down) in its cell, in pixels: a speaker sitting flush on another. */
    public double yOffset(BlockState state) {
        return 0;
    }

    /** The jack the player is looking at, found by casting their look ray into the block's own model space. */
    public Optional<Port> pick(BlockState state, BlockPos pos, Vec3 eye, Vec3 look) {
        double angle = yAngle(state);
        Vec3 shift = new Vec3(0, yOffset(state), 0);
        Vec3 origin = ModelRotation.inverseY(eye.subtract(Vec3.atLowerCornerOf(pos)).scale(16).subtract(shift), angle);
        Vec3 end = ModelRotation.inverseY(eye.add(look.normalize().scale(PICK_RANGE / 16)).subtract(Vec3.atLowerCornerOf(pos)).scale(16)
                .subtract(shift), angle);
        Port best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Port port : layout().ports()) {
            Optional<Vec3> hit = port.box().inflate(PICK_TOLERANCE).clip(origin, end);
            if (hit.isPresent() && hit.get().distanceToSqr(origin) < bestDistance) {
                best = port;
                bestDistance = hit.get().distanceToSqr(origin);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Where a cable plugs into {@code port}, in world coordinates. */
    public Vec3 attachPoint(BlockState state, BlockPos pos, Port port) {
        return toWorld(pos, ModelRotation.applyY(port.attachPoint(), yAngle(state)).add(0, yOffset(state), 0));
    }

    /** The centre of the block's front face, in world coordinates. */
    public Vec3 frontPoint(BlockState state, BlockPos pos) {
        return toWorld(pos, ModelRotation.applyY(layout().front(), yAngle(state)).add(0, yOffset(state), 0));
    }

    /** The unit vector the block's front faces. */
    public Vec3 frontDirection(BlockState state) {
        return ModelRotation.directionY(new Vec3(0, 0, -1), yAngle(state)).normalize();
    }

    private static Vec3 toWorld(BlockPos pos, Vec3 px) {
        return Vec3.atLowerCornerOf(pos).add(px.scale(1 / 16.0));
    }

    /** A pixel-space box turned by any horizontal angle, as the smallest block-aligned box around it. */
    protected static AABB rotatedBounds(AABB px, double angle) {
        AABB result = null;
        for (double x : new double[] {px.minX, px.maxX}) {
            for (double z : new double[] {px.minZ, px.maxZ}) {
                Vec3 p = ModelRotation.applyY(new Vec3(x, px.minY, z), angle);
                AABB corner = new AABB(p, p.add(0, px.maxY - px.minY, 0));
                result = result == null ? corner : result.minmax(corner);
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WireNetwork.get(serverLevel).removeBlock(pos);
            PlaybackManager.get(serverLevel).stop(pos); // a source, or a speaker an MP3 Player was linked to
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
