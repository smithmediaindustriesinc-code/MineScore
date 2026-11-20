package com.minescore.mp3;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Client → server packets for driving an MP3 Player. */
public final class Mp3Packets {
    private Mp3Packets() {}

    public enum Action {
        TOGGLE, NEXT, PREVIOUS,
        /** f = seconds */
        SEEK,
        /** a = song id */
        PLAY_SONG,
        /** i = place in the queue */
        JUMP,
        SHUFFLE, REPEAT,
        /** f = 0..1 */
        VOLUME,
        /** i = band, f = dB */
        EQ,
        EQ_RESET,
        /** a = song id */
        REMOVE_SONG,
        /** a = playlist name */
        PLAYLIST_CREATE, PLAYLIST_DELETE,
        /** a = playlist name, b = song id (empty: from the start) */
        PLAYLIST_PLAY,
        /** a = playlist name, b = song id */
        PLAYLIST_ADD, PLAYLIST_REMOVE,
        /** Takes the MP3 Player out of the Mixer it's docked in. */
        EJECT
    }

    /**
     * Which MP3 Player: one in the sender's inventory (by its id), or the one docked in the Mixer at
     * {@code mixer}.
     */
    public record Target(UUID player, BlockPos mixer) {
        public static final BlockPos INVENTORY = BlockPos.ZERO.below(1000);

        public static Target inventory(UUID player) {
            return new Target(player, INVENTORY);
        }

        public boolean inInventory() {
            return mixer.equals(INVENTORY);
        }
    }

    public record Command(Target target, Action action, int i, float f, String a, String b) {
        public static Command of(Target target, Action action) {
            return new Command(target, action, 0, 0, "", "");
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(target.player());
            buf.writeBlockPos(target.mixer());
            buf.writeEnum(action);
            buf.writeVarInt(i);
            buf.writeFloat(f);
            buf.writeUtf(a, 128);
            buf.writeUtf(b, 128);
        }

        public static Command read(FriendlyByteBuf buf) {
            return new Command(new Target(buf.readUUID(), buf.readBlockPos()), buf.readEnum(Action.class), buf.readVarInt(),
                    buf.readFloat(), buf.readUtf(128), buf.readUtf(128));
        }
    }

    /** Client → server: add the Vinyl Record in the Lathe to the MP3 Player in its second slot. */
    public record LoadIntoPlayer(BlockPos lathe) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(lathe);
        }

        public static LoadIntoPlayer read(FriendlyByteBuf buf) {
            return new LoadIntoPlayer(buf.readBlockPos());
        }
    }
}
