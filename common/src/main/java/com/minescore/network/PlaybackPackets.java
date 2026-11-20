package com.minescore.network;

import com.minescore.playback.Playback;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Server → client playback sync. */
public final class PlaybackPackets {
    private PlaybackPackets() {}

    /** Everything playing in the dimension the player just entered. */
    public record Snapshot(List<Playback> playing) {
        public void write(FriendlyByteBuf buf) {
            buf.writeCollection(playing, (b, p) -> p.write(b));
        }

        public static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(buf.readList(Playback::read));
        }
    }

    /** A source started playing, or its speakers changed. */
    public record Update(Playback playback) {
        public void write(FriendlyByteBuf buf) {
            playback.write(buf);
        }

        public static Update read(FriendlyByteBuf buf) {
            return new Update(Playback.read(buf));
        }
    }

    public record Stop(BlockPos source) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(source);
        }

        public static Stop read(FriendlyByteBuf buf) {
            return new Stop(buf.readBlockPos());
        }
    }
}
