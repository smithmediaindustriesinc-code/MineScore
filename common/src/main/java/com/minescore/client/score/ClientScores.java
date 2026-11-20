package com.minescore.client.score;

import com.minescore.MineScore;
import com.minescore.client.screen.ScoreEditorScreen;
import com.minescore.score.Score;
import com.minescore.score.ScorePackets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtIo;

/** The client's end of score editing: puts arriving scores back together and opens (or refreshes) the editor. */
public final class ClientScores {
    /** Pieces of scores still arriving, by desk. */
    private static final Map<BlockPos, byte[][]> PENDING = new HashMap<>();

    private ClientScores() {}

    public static void onSnapshot(ScorePackets.Snapshot packet) {
        if (packet.total() <= 0 || packet.total() > 1024 || packet.index() < 0 || packet.index() >= packet.total()) {
            return;
        }
        byte[][] parts = PENDING.compute(packet.desk(), (k, v) -> v == null || v.length != packet.total() ? new byte[packet.total()][] : v);
        parts[packet.index()] = packet.data();
        for (byte[] part : parts) {
            if (part == null) {
                return;
            }
        }
        PENDING.remove(packet.desk());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            all.writeBytes(part);
        }
        Score score;
        try {
            score = Score.load(NbtIo.readCompressed(new ByteArrayInputStream(all.toByteArray())));
        } catch (Exception e) {
            MineScore.LOGGER.warn("Couldn't read the score from the server", e);
            return;
        }
        switch (packet.purpose()) {
            case EDIT -> Minecraft.getInstance().setScreen(new ScoreEditorScreen(packet.desk(), packet.scoreId(), score));
            case DESK_PLAY -> ScoreRendering.forDesk(packet.desk(), score);
            case LATHE_CUT -> ScoreRendering.forLathe(packet.desk(), score);
        }
    }

    /** Someone else editing the same score moved their cursor, or left. */
    public static void onPeer(ScorePackets.PeerCursor packet) {
        if (Minecraft.getInstance().screen instanceof ScoreEditorScreen screen && screen.editor().desk.equals(packet.desk())) {
            if (packet.gone()) {
                screen.editor().peers.remove(packet.player());
            } else {
                screen.editor().peers.put(packet.player(), new ScoreEditor.Peer(packet.name(), packet.state(), ScoreEditor.peerColor(packet.player())));
            }
        }
    }

    public static void onEdited(ScorePackets.Edited packet) {
        if (Minecraft.getInstance().screen instanceof ScoreEditorScreen screen && screen.editor().desk.equals(packet.desk())) {
            screen.editor().remoteEdit(packet.changes());
        }
    }
}
