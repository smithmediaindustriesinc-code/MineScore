package com.minescore.wiring;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One jack on a block, in model pixels for the north-facing model.
 *
 * @param box  the jack's visible and clickable box
 * @param face the side of the box cables plug into
 */
public record Port(String id, boolean output, Signal signal, AABB box, Direction face) {
    /** Where a cable attaches: the centre of the jack's outer face. */
    public Vec3 attachPoint() {
        Vec3 c = box.getCenter();
        return switch (face) {
            case NORTH -> new Vec3(c.x, c.y, box.minZ);
            case SOUTH -> new Vec3(c.x, c.y, box.maxZ);
            case WEST -> new Vec3(box.minX, c.y, c.z);
            case EAST -> new Vec3(box.maxX, c.y, c.z);
            case DOWN -> new Vec3(c.x, box.minY, c.z);
            case UP -> new Vec3(c.x, box.maxY, c.z);
        };
    }

    /** Distance in pixels from a model-space point to this jack (0 if inside). */
    public double distanceTo(Vec3 px) {
        double dx = Math.max(Math.max(box.minX - px.x, 0), px.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - px.y, 0), px.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - px.z, 0), px.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Translation key for this jack's name, e.g. {@code port.minescore.band_12.out} ("12 ga out"). */
    public String translationKey() {
        return "port.minescore." + signal.id() + (output ? ".out" : ".in");
    }
}
