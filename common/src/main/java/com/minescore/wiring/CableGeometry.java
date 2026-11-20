package com.minescore.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** How cables hang between their points. Shared by the renderer and the Wire Cutters so both agree. */
public final class CableGeometry {
    private CableGeometry() {}

    /** How far the middle of a span hangs below the straight line, in blocks. */
    public static double sag(double length) {
        return Math.min(0.03 * length + 0.04, 4.0);
    }

    /** Points along one hanging span, including both ends. */
    public static List<Vec3> sample(Vec3 a, Vec3 b) {
        double length = a.distanceTo(b);
        int segments = Mth.clamp((int) Math.ceil(length * 1.5), 4, 96);
        double sag = sag(length);
        List<Vec3> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 p = i == 0 ? a : i == segments ? b : a.lerp(b, t);
            points.add(i == 0 || i == segments ? p : p.subtract(0, sag * 4 * t * (1 - t), 0));
        }
        return points;
    }

    /**
     * The cable whose hanging shape passes closest to a look ray, within {@code tolerance} blocks.
     *
     * @param direction normalised look direction
     */
    public static @Nullable Cable pick(Collection<Cable> cables, Vec3 eye, Vec3 direction, double reach, double tolerance) {
        Vec3 end = eye.add(direction.scale(reach));
        AABB rayBox = new AABB(eye, end).inflate(tolerance);
        Cable best = null;
        double bestDistance = tolerance * tolerance;
        for (Cable cable : cables) {
            List<Vec3> path = cable.path();
            for (int i = 0; i + 1 < path.size(); i++) {
                Vec3 a = path.get(i);
                Vec3 b = path.get(i + 1);
                if (!new AABB(a, b).expandTowards(0, -sag(a.distanceTo(b)), 0).intersects(rayBox)) {
                    continue;
                }
                List<Vec3> points = sample(a, b);
                for (int j = 0; j + 1 < points.size(); j++) {
                    double d = segmentDistanceSqr(points.get(j), points.get(j + 1), eye, end);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = cable;
                    }
                }
            }
        }
        return best;
    }

    /** Squared shortest distance between segments p1–q1 and p2–q2. */
    static double segmentDistanceSqr(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1);
        Vec3 d2 = q2.subtract(p2);
        Vec3 r = p1.subtract(p2);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);
        double s;
        double t;
        if (a < 1e-12 && e < 1e-12) {
            return r.dot(r);
        }
        if (a < 1e-12) {
            s = 0;
            t = Mth.clamp(f / e, 0, 1);
        } else {
            double c = d1.dot(r);
            if (e < 1e-12) {
                t = 0;
                s = Mth.clamp(-c / a, 0, 1);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > 1e-12 ? Mth.clamp((b * f - c * e) / denom, 0, 1) : 0;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0;
                    s = Mth.clamp(-c / a, 0, 1);
                } else if (t > 1) {
                    t = 1;
                    s = Mth.clamp((b - c) / a, 0, 1);
                }
            }
        }
        Vec3 c1 = p1.add(d1.scale(s));
        Vec3 c2 = p2.add(d2.scale(t));
        return c1.distanceToSqr(c2);
    }
}
