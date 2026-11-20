package com.minescore.wiring;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A cable being laid, kept on the wire stack between clicks so it survives relogging and the client
 * can draw it.
 *
 * @param points the first jack, then each anchor's two ends, in click order
 */
public record PendingCable(ResourceKey<Level> dimension, PortRef start, List<BlockPos> anchors, List<Vec3> points) {
    private static final String TAG = "MineScorePending";

    public PendingCable {
        anchors = List.copyOf(anchors);
        points = List.copyOf(points);
    }

    public Vec3 lastPoint() {
        return points.get(points.size() - 1);
    }

    public PendingCable withAnchor(BlockPos anchor, Vec3 in, Vec3 out) {
        List<BlockPos> a = new ArrayList<>(anchors);
        a.add(anchor.immutable());
        List<Vec3> p = new ArrayList<>(points);
        p.add(in);
        p.add(out);
        return new PendingCable(dimension, start, a, p);
    }

    /** This cable with an anchor taken out, e.g. because the anchor was broken while laying it. */
    public PendingCable withoutAnchor(BlockPos anchor) {
        int i = anchors.indexOf(anchor);
        if (i < 0) {
            return this;
        }
        List<BlockPos> a = new ArrayList<>(anchors);
        List<Vec3> p = new ArrayList<>(points);
        a.remove(i);
        p.remove(1 + 2 * i);
        p.remove(1 + 2 * i);
        return new PendingCable(dimension, start, a, p);
    }

    public static @Nullable PendingCable read(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = root.getCompound(TAG);
        List<BlockPos> anchors = new ArrayList<>();
        for (Tag t : tag.getList("Anchors", Tag.TAG_COMPOUND)) {
            anchors.add(NbtUtils.readBlockPos((CompoundTag) t));
        }
        List<Vec3> points = new ArrayList<>();
        for (Tag t : tag.getList("Points", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            points.add(new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")));
        }
        if (points.size() != 1 + 2 * anchors.size()) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dimension")));
        return new PendingCable(dimension, PortRef.load(tag.getCompound("Start")), anchors, points);
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Start", start.save());
        ListTag anchorList = new ListTag();
        anchors.forEach(pos -> anchorList.add(NbtUtils.writeBlockPos(pos)));
        tag.put("Anchors", anchorList);
        ListTag pointList = new ListTag();
        for (Vec3 v : points) {
            CompoundTag p = new CompoundTag();
            p.putDouble("X", v.x);
            p.putDouble("Y", v.y);
            p.putDouble("Z", v.z);
            pointList.add(p);
        }
        tag.put("Points", pointList);
        stack.getOrCreateTag().put(TAG, tag);
    }

    public static void clear(ItemStack stack) {
        stack.removeTagKey(TAG);
    }
}
