package com.minescore.wiring;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;

/** A specific jack in the world. */
public record PortRef(BlockPos pos, String port) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        tag.putString("Port", port);
        return tag;
    }

    public static PortRef load(CompoundTag tag) {
        return new PortRef(NbtUtils.readBlockPos(tag.getCompound("Pos")), tag.getString("Port"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(port);
    }

    public static PortRef read(FriendlyByteBuf buf) {
        return new PortRef(buf.readBlockPos(), buf.readUtf());
    }
}
