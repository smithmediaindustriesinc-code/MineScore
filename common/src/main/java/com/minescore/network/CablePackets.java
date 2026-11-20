package com.minescore.network;

import com.minescore.wiring.Cable;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Server → client cable sync. */
public final class CablePackets {
    private CablePackets() {}

    /** Every cable in the dimension the player just entered. */
    public record Snapshot(List<Cable> cables) {
        public void write(FriendlyByteBuf buf) {
            buf.writeCollection(cables, (b, cable) -> cable.write(b));
        }

        public static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(buf.readList(Cable::read));
        }
    }

    /** A cable was added or changed. */
    public record Upsert(Cable cable) {
        public void write(FriendlyByteBuf buf) {
            cable.write(buf);
        }

        public static Upsert read(FriendlyByteBuf buf) {
            return new Upsert(Cable.read(buf));
        }
    }

    public record Remove(UUID id) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
        }

        public static Remove read(FriendlyByteBuf buf) {
            return new Remove(buf.readUUID());
        }
    }
}
