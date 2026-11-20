package com.minescore.wiring;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rotates model-space points (in pixels, 0–16) exactly like a blockstate's {@code x} then {@code y}
 * rotation, so jack and anchor positions match what the player sees. Rotations are multiples of 90°.
 */
public final class ModelRotation {
    private ModelRotation() {}

    public static Vec3 apply(Vec3 px, int xRot, int yRot) {
        double x = px.x - 8, y = px.y - 8, z = px.z - 8;
        for (int i = 0; i < steps(xRot); i++) { // up -> north
            double t = y;
            y = z;
            z = -t;
        }
        for (int i = 0; i < steps(yRot); i++) { // north -> east
            double t = x;
            x = -z;
            z = t;
        }
        return new Vec3(x + 8, y + 8, z + 8);
    }

    public static Vec3 inverse(Vec3 px, int xRot, int yRot) {
        double x = px.x - 8, y = px.y - 8, z = px.z - 8;
        for (int i = 0; i < steps(yRot); i++) {
            double t = x;
            x = z;
            z = -t;
        }
        for (int i = 0; i < steps(xRot); i++) {
            double t = y;
            y = -z;
            z = t;
        }
        return new Vec3(x + 8, y + 8, z + 8);
    }

    /** Turns a model-space point (pixels) clockwise seen from above by any angle, around the block centre. */
    public static Vec3 applyY(Vec3 px, double degrees) {
        double a = Math.toRadians(degrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        double x = px.x - 8;
        double z = px.z - 8;
        return new Vec3(8 + x * cos - z * sin, px.y, 8 + x * sin + z * cos);
    }

    public static Vec3 inverseY(Vec3 px, double degrees) {
        return applyY(px, -degrees);
    }

    /** A direction turned the same way as {@link #applyY}. */
    public static Vec3 directionY(Vec3 v, double degrees) {
        return applyY(v.add(8, 0, 8), degrees).subtract(8, 0, 8);
    }

    /** A pixel-space box rotated into a block shape. */
    public static VoxelShape shape(AABB px, int xRot, int yRot) {
        Vec3 a = apply(new Vec3(px.minX, px.minY, px.minZ), xRot, yRot);
        Vec3 b = apply(new Vec3(px.maxX, px.maxY, px.maxZ), xRot, yRot);
        return Shapes.create(new AABB(a.scale(1 / 16.0), b.scale(1 / 16.0)));
    }

    /** The blockstate {@code y} rotation for a horizontally facing block whose model faces north. */
    public static int yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private static int steps(int degrees) {
        return Math.floorMod(degrees / 90, 4);
    }
}
