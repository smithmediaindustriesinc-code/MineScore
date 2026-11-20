package com.minescore.wiring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * A placed cable, always stored output → input.
 *
 * @param signal  what the output jack carries
 * @param anchors Cable Anchors it is mounted on, in order from the output end
 * @param path    world points it is drawn through: the output jack, each anchor's two ends, the input jack.
 *                Anchor {@code i} owns points {@code 1 + 2i} and {@code 2 + 2i}.
 */
public record Cable(UUID id, PortRef from, BlockKind fromKind, PortRef to, BlockKind toKind, Signal signal, Gauge gauge,
                    List<BlockPos> anchors, List<Vec3> path) {

    public Cable {
        anchors = List.copyOf(anchors);
        path = List.copyOf(path);
        if (path.size() != 2 + 2 * anchors.size()) {
            throw new IllegalArgumentException("Cable path doesn't match its anchors");
        }
    }

    /**
     * Builds a cable from a successful rule check and the points collected while it was laid.
     *
     * @param anchors anchors in the order they were clicked
     * @param laid    points in click order: the first jack, each anchor's ends, then the second jack
     */
    public static Cable create(WiringRules.Ok ok, Gauge gauge, List<BlockPos> anchors, List<Vec3> laid) {
        List<BlockPos> a = new ArrayList<>(anchors);
        List<Vec3> p = new ArrayList<>(laid);
        if (ok.swapped()) {
            Collections.reverse(a);
            Collections.reverse(p);
        }
        return new Cable(UUID.randomUUID(), ok.from().ref(), ok.from().kind(), ok.to().ref(), ok.to().kind(),
                ok.from().port().signal(), gauge, a, p);
    }

    public boolean hasEndAt(BlockPos pos) {
        return from.pos().equals(pos) || to.pos().equals(pos);
    }

    public boolean usesPort(PortRef port) {
        return from.equals(port) || to.equals(port);
    }

    /** This cable with an anchor removed, so it runs straight past where the anchor was. */
    public Cable withoutAnchor(BlockPos anchor) {
        int i = anchors.indexOf(anchor);
        if (i < 0) {
            return this;
        }
        List<BlockPos> a = new ArrayList<>(anchors);
        List<Vec3> p = new ArrayList<>(path);
        a.remove(i);
        p.remove(1 + 2 * i);
        p.remove(1 + 2 * i);
        return new Cable(id, from, fromKind, to, toKind, signal, gauge, a, p);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.put("From", from.save());
        tag.putString("FromKind", fromKind.id());
        tag.put("To", to.save());
        tag.putString("ToKind", toKind.id());
        tag.putString("Signal", signal.id());
        tag.putString("Gauge", gauge.name());
        ListTag anchorList = new ListTag();
        anchors.forEach(pos -> anchorList.add(NbtUtils.writeBlockPos(pos)));
        tag.put("Anchors", anchorList);
        ListTag pathList = new ListTag();
        for (Vec3 v : path) {
            CompoundTag point = new CompoundTag();
            point.putDouble("X", v.x);
            point.putDouble("Y", v.y);
            point.putDouble("Z", v.z);
            pathList.add(point);
        }
        tag.put("Path", pathList);
        return tag;
    }

    public static Cable load(CompoundTag tag) {
        List<BlockPos> anchors = new ArrayList<>();
        for (Tag t : tag.getList("Anchors", Tag.TAG_COMPOUND)) {
            anchors.add(NbtUtils.readBlockPos((CompoundTag) t));
        }
        List<Vec3> path = new ArrayList<>();
        for (Tag t : tag.getList("Path", Tag.TAG_COMPOUND)) {
            CompoundTag point = (CompoundTag) t;
            path.add(new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")));
        }
        return new Cable(tag.getUUID("Id"), PortRef.load(tag.getCompound("From")), BlockKind.byId(tag.getString("FromKind")),
                PortRef.load(tag.getCompound("To")), BlockKind.byId(tag.getString("ToKind")), Signal.byId(tag.getString("Signal")),
                Gauge.valueOf(tag.getString("Gauge")), anchors, path);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        from.write(buf);
        buf.writeEnum(fromKind);
        to.write(buf);
        buf.writeEnum(toKind);
        buf.writeEnum(signal);
        buf.writeEnum(gauge);
        buf.writeCollection(anchors, FriendlyByteBuf::writeBlockPos);
        buf.writeCollection(path, (b, v) -> {
            b.writeDouble(v.x);
            b.writeDouble(v.y);
            b.writeDouble(v.z);
        });
    }

    public static Cable read(FriendlyByteBuf buf) {
        return new Cable(buf.readUUID(), PortRef.read(buf), buf.readEnum(BlockKind.class), PortRef.read(buf),
                buf.readEnum(BlockKind.class), buf.readEnum(Signal.class), buf.readEnum(Gauge.class),
                buf.readList(FriendlyByteBuf::readBlockPos), buf.readList(b -> new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
    }
}
