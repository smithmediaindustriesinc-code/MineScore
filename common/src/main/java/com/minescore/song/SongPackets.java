package com.minescore.song;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Packets for cutting records and moving songs between players and the server. */
public final class SongPackets {
    /** Serverbound packets must stay under 32 KiB. */
    public static final int UPLOAD_CHUNK = 30_000;
    /** Clientbound packets may be up to 1 MiB; keep well under. */
    public static final int DOWNLOAD_CHUNK = 256 * 1024;

    private SongPackets() {}

    /**
     * Client → server: cut the given song onto the Blank Vinyl in this Lathe.
     *
     * @param loudness the song's {@link com.minescore.audio.Loudness}, encoded
     */
    public record Cut(BlockPos lathe, SongMeta song, long size, String title, byte[] loudness) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(lathe);
            song.write(buf);
            buf.writeLong(size);
            buf.writeUtf(title, 128);
            buf.writeByteArray(loudness);
        }

        public static Cut read(FriendlyByteBuf buf) {
            return new Cut(buf.readBlockPos(), SongMeta.read(buf), buf.readLong(), buf.readUtf(128), buf.readByteArray(8192));
        }
    }

    /** Client → server: copy the Vinyl Record in this Lathe onto the Blank Vinyl in its second slot. */
    public record Copy(BlockPos lathe, String title) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(lathe);
            buf.writeUtf(title, 128);
        }

        public static Copy read(FriendlyByteBuf buf) {
            return new Copy(buf.readBlockPos(), buf.readUtf(128));
        }
    }

    /** Server → client: the server doesn't have this song yet, please send it. */
    public record SendSong(String id) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
        }

        public static SendSong read(FriendlyByteBuf buf) {
            return new SendSong(buf.readUtf());
        }
    }

    /** Client → server: one piece of a song being uploaded, in order. */
    public record UploadChunk(String id, byte[] data) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeByteArray(data);
        }

        public static UploadChunk read(FriendlyByteBuf buf) {
            return new UploadChunk(buf.readUtf(), buf.readByteArray(UPLOAD_CHUNK));
        }
    }

    /** Client → server: send me this song, I don't have it. */
    public record RequestSong(String id) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
        }

        public static RequestSong read(FriendlyByteBuf buf) {
            return new RequestSong(buf.readUtf());
        }
    }

    /** Server → client: one piece of a requested song. */
    public record DownloadChunk(String id, String format, int index, int total, byte[] data) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(format);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
        }

        public static DownloadChunk read(FriendlyByteBuf buf) {
            return new DownloadChunk(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(DOWNLOAD_CHUNK));
        }
    }

    /** Server → client: a status line for the Lathe screen (a translation key). */
    public record LatheStatus(BlockPos lathe, String key) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(lathe);
            buf.writeUtf(key);
        }

        public static LatheStatus read(FriendlyByteBuf buf) {
            return new LatheStatus(buf.readBlockPos(), buf.readUtf());
        }
    }
}
