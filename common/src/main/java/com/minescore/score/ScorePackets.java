package com.minescore.score;

import com.minescore.song.SongMeta;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** What travels between a score editor and the server (SCOPE.md §9). */
public final class ScorePackets {
    /** Largest piece of a score sent at once (it's compressed first). */
    public static final int CHUNK = 256 * 1024;

    private ScorePackets() {}

    /** Client: open the editor for the score on this desk. */
    public record Open(BlockPos desk) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
        }

        public static Open read(FriendlyByteBuf buf) {
            return new Open(buf.readBlockPos());
        }
    }

    /** What a score is being sent to a client for. */
    public enum Purpose {
        /** Open the editor on it. */
        EDIT,
        /** Render it to audio and play it from the desk (SCOPE.md §9.2). */
        DESK_PLAY,
        /** Render it to audio and cut it onto a record (SCOPE.md §2.4). */
        LATHE_CUT
    }

    /**
     * Server: one piece of a score (compressed NBT), {@code index} of {@code total}; once the last arrives the
     * editor opens, or the score is rendered for the desk or the Lathe at {@code desk}.
     */
    public record Snapshot(BlockPos desk, String scoreId, int index, int total, byte[] data, Purpose purpose) {
        public Snapshot(BlockPos desk, String scoreId, int index, int total, byte[] data) {
            this(desk, scoreId, index, total, data, Purpose.EDIT);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            buf.writeUtf(scoreId, 32);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
            buf.writeEnum(purpose);
        }

        public static Snapshot read(FriendlyByteBuf buf) {
            return new Snapshot(buf.readBlockPos(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK + 64),
                    buf.readEnum(Purpose.class));
        }
    }

    /** Client: send me the score in this desk (or Lathe) to render, for playing it or cutting it. */
    public record Render(BlockPos block, Purpose purpose) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(block);
            buf.writeEnum(purpose);
        }

        public static Render read(FriendlyByteBuf buf) {
            return new Render(buf.readBlockPos(), buf.readEnum(Purpose.class));
        }
    }

    /** Client: the score on this desk, rendered; play it (uploading it first if the server asks). */
    public record DeskPlay(BlockPos desk, SongMeta song, long size, byte[] loudness) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            song.write(buf);
            buf.writeLong(size);
            buf.writeByteArray(loudness);
        }

        public static DeskPlay read(FriendlyByteBuf buf) {
            return new DeskPlay(buf.readBlockPos(), SongMeta.read(buf), buf.readLong(), buf.readByteArray(8192));
        }
    }

    /** The desk screen's Stop and Loop buttons. */
    public enum DeskAction { STOP, LOOP }

    /** Client: a desk button. */
    public record DeskControl(BlockPos desk, DeskAction action) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            buf.writeEnum(action);
        }

        public static DeskControl read(FriendlyByteBuf buf) {
            return new DeskControl(buf.readBlockPos(), buf.readEnum(DeskAction.class));
        }
    }

    /** Client: changes made in the editor. */
    public record Edit(BlockPos desk, List<ScoreChange> changes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            ScoreChange.writeAll(buf, changes);
        }

        public static Edit read(FriendlyByteBuf buf) {
            return new Edit(buf.readBlockPos(), ScoreChange.readAll(buf));
        }
    }

    /** Server: changes someone else made, for every other editor of that score. */
    public record Edited(BlockPos desk, List<ScoreChange> changes) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            ScoreChange.writeAll(buf, changes);
        }

        public static Edited read(FriendlyByteBuf buf) {
            return new Edited(buf.readBlockPos(), ScoreChange.readAll(buf));
        }
    }

    /**
     * Where one editor's selection or note-input cursor is (SCOPE.md §9.10, stage 4): from (measure1, at1) to
     * (measure2, at2) across parts part1–part2; part1 -1 when nothing is selected.
     */
    public record CursorState(int part1, int part2, int measure1, Frac at1, int measure2, Frac at2, boolean input) {
        public static final CursorState NONE = new CursorState(-1, -1, 0, Frac.ZERO, 0, Frac.ZERO, false);

        public boolean shown() {
            return part1 >= 0;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(part1 + 1);
            buf.writeVarInt(part2 + 1);
            buf.writeVarInt(measure1);
            buf.writeUtf(at1.toString(), 32);
            buf.writeVarInt(measure2);
            buf.writeUtf(at2.toString(), 32);
            buf.writeBoolean(input);
        }

        public static CursorState read(FriendlyByteBuf buf) {
            int p1 = buf.readVarInt() - 1, p2 = buf.readVarInt() - 1, m1 = buf.readVarInt();
            Frac a1 = Frac.parse(buf.readUtf(32));
            int m2 = buf.readVarInt();
            Frac a2 = Frac.parse(buf.readUtf(32));
            return new CursorState(p1, p2, m1, a1, m2, a2, buf.readBoolean());
        }
    }

    /** Client: where this player's cursor now is. */
    public record Cursor(BlockPos desk, CursorState state) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            state.write(buf);
        }

        public static Cursor read(FriendlyByteBuf buf) {
            return new Cursor(buf.readBlockPos(), CursorState.read(buf));
        }
    }

    /** Server: where someone else editing the same score has their cursor, or ({@code gone}) that they left. */
    public record PeerCursor(BlockPos desk, UUID player, String name, CursorState state, boolean gone) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            buf.writeUUID(player);
            buf.writeUtf(name, 64);
            state.write(buf);
            buf.writeBoolean(gone);
        }

        public static PeerCursor read(FriendlyByteBuf buf) {
            return new PeerCursor(buf.readBlockPos(), buf.readUUID(), buf.readUtf(64), CursorState.read(buf), buf.readBoolean());
        }
    }

    /** Serverbound packets must stay under 32 KiB, so an imported score goes up in pieces this big. */
    public static final int UPLOAD_CHUNK = 30_000;

    /**
     * Client: one piece of an imported score (compressed NBT) to replace the desk's score with, {@code index} of
     * {@code total} (SCOPE.md §9.12). With {@code copyFirst}, the score as it was is kept as a copy first.
     */
    public record Replace(BlockPos desk, int index, int total, byte[] data, boolean copyFirst) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
            buf.writeVarInt(index);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
            buf.writeBoolean(copyFirst);
        }

        public static Replace read(FriendlyByteBuf buf) {
            return new Replace(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(UPLOAD_CHUNK + 64), buf.readBoolean());
        }
    }

    /** Client: the editor closed. */
    public record Close(BlockPos desk) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(desk);
        }

        public static Close read(FriendlyByteBuf buf) {
            return new Close(buf.readBlockPos());
        }
    }
}
