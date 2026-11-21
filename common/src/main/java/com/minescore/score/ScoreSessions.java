package com.minescore.score;

import com.minescore.MineScore;
import com.minescore.block.WritersDeskBlockEntity;
import com.minescore.item.ScoreItem;
import com.minescore.network.MineScoreNetwork;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Who has which score open in an editor, on the server (SCOPE.md §9.3): edits are checked, applied to the
 * stored score and passed on to everyone else editing it. Scores are saved a few seconds after a change
 * and when the last editor closes.
 */
public final class ScoreSessions {
    /** How close to the desk an editor must stay, in blocks. */
    private static final double REACH = 8;
    private static final int SAVE_TICKS = 100;

    private record Session(ResourceKey<Level> dimension, BlockPos desk, String scoreId) {}

    private static final Map<ServerPlayer, Session> OPEN = new HashMap<>();
    /** Where each editor's cursor is, for showing to the others (SCOPE.md §9.10). */
    private static final Map<ServerPlayer, ScorePackets.CursorState> CURSORS = new HashMap<>();
    private static final Set<String> DIRTY = new HashSet<>();

    private ScoreSessions() {}

    private static @Nullable WritersDeskBlockEntity desk(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH) {
            return null;
        }
        return player.serverLevel().getBlockEntity(pos) instanceof WritersDeskBlockEntity desk ? desk : null;
    }

    public static void onOpen(ScorePackets.Open packet, ServerPlayer player) {
        WritersDeskBlockEntity desk = desk(player, packet.desk());
        ScoreItem.Data data = desk == null ? null : desk.score();
        Score score = data == null ? null : ScoreStore.get(player.server).get(data.id());
        if (score == null) {
            return;
        }
        Session old = OPEN.put(player, new Session(player.level().dimension(), packet.desk(), data.id()));
        if (old != null && !old.scoreId().equals(data.id())) {
            left(player, old);
        }
        sendSnapshot(player, packet.desk(), data.id(), score);
        // everyone already in this score shows up straight away
        for (Map.Entry<ServerPlayer, Session> other : OPEN.entrySet()) {
            ServerPlayer them = other.getKey();
            if (them != player && other.getValue().scoreId().equals(data.id())) {
                ScorePackets.CursorState state = CURSORS.getOrDefault(them, ScorePackets.CursorState.NONE);
                MineScoreNetwork.sendToPlayer(player, new ScorePackets.PeerCursor(packet.desk(), them.getUUID(),
                        them.getGameProfile().getName(), state, false));
            }
        }
    }

    /** Imported scores still arriving, by player. */
    private static final Map<ServerPlayer, byte[][]> REPLACING = new HashMap<>();

    /**
     * A piece of an imported score (SCOPE.md §9.12). Once it's all here it replaces the score on the desk,
     * after (if asked) keeping the old one as a numbered copy on a Blank Score from the player's inventory,
     * and everyone editing it gets the new score.
     */
    public static void onReplace(ScorePackets.Replace packet, ServerPlayer player) {
        Session session = OPEN.get(player);
        WritersDeskBlockEntity desk = desk(player, packet.desk());
        if (session == null || desk == null || !session.desk().equals(packet.desk()) || packet.total() <= 0 || packet.total() > 256
                || packet.index() < 0 || packet.index() >= packet.total()) {
            return;
        }
        byte[][] parts = REPLACING.compute(player, (k, v) -> v == null || v.length != packet.total() ? new byte[packet.total()][] : v);
        parts[packet.index()] = packet.data();
        for (byte[] part : parts) {
            if (part == null) {
                return;
            }
        }
        REPLACING.remove(player);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            all.writeBytes(part);
        }
        Score imported;
        try {
            imported = Score.load(NbtIo.readCompressed(new java.io.ByteArrayInputStream(all.toByteArray())));
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read the score {} imported", player.getGameProfile().getName(), e);
            return;
        }
        if (imported.parts.isEmpty() || imported.parts.size() > Score.MAX_PARTS || imported.measureCount() == 0
                || imported.measureCount() > Score.MAX_MEASURES || imported.parts.stream().anyMatch(p -> p.measures.size() != imported.measureCount())) {
            return;
        }
        ScoreStore store = ScoreStore.get(player.server);
        if (imported.parts.size() > com.minescore.config.MineScoreConfig.MAX_PARTS.integer() || imported.measureCount() > com.minescore.config.MineScoreConfig.MAX_MEASURES.integer()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.minescore.score.limit",
                    com.minescore.config.MineScoreConfig.MAX_PARTS.integer(), com.minescore.config.MineScoreConfig.MAX_MEASURES.integer()), true);
            return;
        }
        if (packet.copyFirst() && com.minescore.config.MineScoreConfig.SCORE_COPIES.bool()) {
            int paper = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(com.minescore.registry.ModItems.BLANK_SCORE.get())) {
                    paper = i;
                    break;
                }
            }
            ScoreItem.Data original = desk.score();
            String copyId = paper < 0 || original == null ? null : store.copy(session.scoreId());
            if (copyId == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.minescore.score.copy_needs_paper"), true);
                return;
            }
            player.getInventory().removeItem(paper, 1);
            net.minecraft.world.item.ItemStack copy = new net.minecraft.world.item.ItemStack(com.minescore.registry.ModItems.SCORE.get());
            new ScoreItem.Data(copyId, original.title(), original.composer(), original.original(), store.nextCopyNumber(original.original())).write(copy);
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }
        for (int m = 0; m < imported.measureCount(); m++) { // every measure adds up, whatever the file said
            for (int p = 0; p < imported.parts.size(); p++) {
                tidy(imported, List.of(new ScoreChange.MeasureContent(p, m, new net.minecraft.nbt.CompoundTag())));
            }
        }
        store.replace(session.scoreId(), imported);
        desk.setTitles(imported.title, imported.composer);
        for (Map.Entry<ServerPlayer, Session> other : OPEN.entrySet()) {
            if (other.getValue().scoreId().equals(session.scoreId())) {
                sendSnapshot(other.getKey(), other.getValue().desk(), session.scoreId(), imported); // their editors reopen on it
            }
        }
    }

    /** An editor moved their cursor: everyone else in the same score sees it. */
    public static void onCursor(ScorePackets.Cursor packet, ServerPlayer player) {
        Session session = OPEN.get(player);
        if (session == null || !session.desk().equals(packet.desk())) {
            return;
        }
        CURSORS.put(player, packet.state());
        tellOthers(player, session, packet.state(), false);
    }

    private static void tellOthers(ServerPlayer player, Session session, ScorePackets.CursorState state, boolean gone) {
        for (Map.Entry<ServerPlayer, Session> other : OPEN.entrySet()) {
            if (other.getKey() != player && other.getValue().scoreId().equals(session.scoreId())) {
                MineScoreNetwork.sendToPlayer(other.getKey(), new ScorePackets.PeerCursor(other.getValue().desk(), player.getUUID(),
                        player.getGameProfile().getName(), state, gone));
            }
        }
    }

    /** Someone stopped editing a score: their cursor goes from everyone else's view. */
    private static void left(ServerPlayer player, Session session) {
        CURSORS.remove(player);
        tellOthers(player, session, ScorePackets.CursorState.NONE, true);
    }

    /** A desk's Play or a Lathe's Cut on a score: the score goes to that player to render. */
    public static void onRender(ScorePackets.Render packet, ServerPlayer player) {
        if (player.distanceToSqr(packet.block().getCenter()) > REACH * REACH) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = switch (packet.purpose()) {
            case DESK_PLAY -> player.serverLevel().getBlockEntity(packet.block()) instanceof WritersDeskBlockEntity desk
                    ? desk.items().getItem(WritersDeskBlockEntity.SCORE) : net.minecraft.world.item.ItemStack.EMPTY;
            case LATHE_CUT -> player.serverLevel().getBlockEntity(packet.block()) instanceof com.minescore.block.LatheHost lathe
                    ? lathe.lathe().items().getItem(com.minescore.block.LatheCore.INPUT) : net.minecraft.world.item.ItemStack.EMPTY;
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
        ScoreItem.Data data = ScoreItem.Data.read(stack);
        Score score = data == null ? null : ScoreStore.get(player.server).get(data.id());
        if (score != null) {
            sendSnapshot(player, packet.block(), data.id(), score, packet.purpose());
        }
    }

    private static void sendSnapshot(ServerPlayer player, BlockPos desk, String id, Score score) {
        sendSnapshot(player, desk, id, score, ScorePackets.Purpose.EDIT);
    }

    private static void sendSnapshot(ServerPlayer player, BlockPos desk, String id, Score score, ScorePackets.Purpose purpose) {
        byte[] bytes;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(score.save(), out);
            bytes = out.toByteArray();
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't send score {}", id, e);
            return;
        }
        int total = Math.max(1, (bytes.length + ScorePackets.CHUNK - 1) / ScorePackets.CHUNK);
        for (int i = 0; i < total; i++) {
            int from = i * ScorePackets.CHUNK;
            byte[] chunk = java.util.Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + ScorePackets.CHUNK));
            MineScoreNetwork.sendToPlayer(player, new ScorePackets.Snapshot(desk, id, i, total, chunk, purpose));
        }
    }

    public static void onEdit(ScorePackets.Edit packet, ServerPlayer player) {
        Session session = OPEN.get(player);
        WritersDeskBlockEntity desk = desk(player, packet.desk());
        if (session == null || desk == null || !session.desk().equals(packet.desk()) || desk.score() == null
                || !desk.score().id().equals(session.scoreId())) {
            return;
        }
        ScoreStore store = ScoreStore.get(player.server);
        Score score = store.get(session.scoreId());
        if (score == null) {
            return;
        }
        String title = score.title, composer = score.composer;
        int addedParts = 0, addedMeasures = 0;
        for (ScoreChange c : packet.changes()) {
            addedParts += c instanceof ScoreChange.AddPart ? 1 : 0;
            addedMeasures += c instanceof ScoreChange.InsertMeasure ? 1 : 0;
        }
        if ((addedParts > 0 && score.parts.size() + addedParts > com.minescore.config.MineScoreConfig.MAX_PARTS.integer())
                || (addedMeasures > 0 && score.measureCount() + addedMeasures > com.minescore.config.MineScoreConfig.MAX_MEASURES.integer())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.minescore.score.limit",
                    com.minescore.config.MineScoreConfig.MAX_PARTS.integer(), com.minescore.config.MineScoreConfig.MAX_MEASURES.integer()), true);
            sendSnapshot(player, packet.desk(), session.scoreId(), score); // put the editor right
            return;
        }
        if (ScoreChange.applyAll(score, packet.changes()) == null) {
            sendSnapshot(player, packet.desk(), session.scoreId(), score); // didn't fit: put the editor right
            return;
        }
        tidy(score, packet.changes());
        DIRTY.add(session.scoreId());
        if (!title.equals(score.title) || !composer.equals(score.composer)) {
            desk.setTitles(score.title, score.composer);
            if (!score.title.isBlank()) {
                com.minescore.ModAdvancements.award(player, "composer");
            }
        }
        for (Map.Entry<ServerPlayer, Session> other : OPEN.entrySet()) {
            if (other.getKey() != player && other.getValue().scoreId().equals(session.scoreId())) {
                MineScoreNetwork.sendToPlayer(other.getKey(), new ScorePackets.Edited(other.getValue().desk(), packet.changes()));
                com.minescore.ModAdvancements.award(player, "duet");
                com.minescore.ModAdvancements.award(other.getKey(), "duet");
            }
        }
    }

    /** Measures an edit touched always add up exactly, whatever a client sent. */
    private static void tidy(Score score, List<ScoreChange> changes) {
        for (ScoreChange change : changes) {
            if (change instanceof ScoreChange.MeasureContent mc && mc.part() < score.parts.size() && mc.measure() < score.measureCount()) {
                Part.Measure measure = score.parts.get(mc.part()).measures.get(mc.measure());
                Frac length = score.lengthOf(mc.measure());
                for (int v = 0; v < Part.VOICES; v++) {
                    if (v == 0 || !measure.voices[v].isEmpty()) {
                        List<Chord> fixed = VoiceEdit.normalize(measure.voices[v], length);
                        if (fixed.size() > 256) {
                            fixed = Rests.fill(length);
                        }
                        measure.voices[v].clear();
                        measure.voices[v].addAll(fixed);
                    }
                }
            }
        }
    }

    public static void onClose(ScorePackets.Close packet, ServerPlayer player) {
        Session session = OPEN.remove(player);
        if (session != null) {
            left(player, session);
            saveIfDone(player.server, session.scoreId());
        }
    }

    public static void onLoggedOut(ServerPlayer player) {
        REPLACING.remove(player);
        Session session = OPEN.remove(player);
        if (session != null) {
            left(player, session);
            saveIfDone(player.server, session.scoreId());
        }
    }

    private static void saveIfDone(MinecraftServer server, String id) {
        if (DIRTY.contains(id) && OPEN.values().stream().noneMatch(s -> s.scoreId().equals(id))) {
            ScoreStore.get(server).save(id);
            DIRTY.remove(id);
        }
    }

    /** Every tick: saves changed scores now and then, and closes editors that wandered off. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SAVE_TICKS == 0 && !DIRTY.isEmpty()) {
            ScoreStore store = ScoreStore.get(server);
            for (String id : new ArrayList<>(DIRTY)) {
                store.save(id);
            }
            DIRTY.clear();
        }
        if (server.getTickCount() % 20 == 0) {
            List<Map.Entry<ServerPlayer, Session>> gone = new ArrayList<>();
            Iterator<Map.Entry<ServerPlayer, Session>> it = OPEN.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ServerPlayer, Session> e = it.next();
                ServerPlayer player = e.getKey();
                if (player.isRemoved() || player.level().dimension() != e.getValue().dimension() || desk(player, e.getValue().desk()) == null) {
                    it.remove();
                    gone.add(e);
                }
            }
            gone.forEach(e -> left(e.getKey(), e.getValue()));
        }
    }

    /** The server is stopping: everything changed gets saved. */
    public static void saveAll(MinecraftServer server) {
        ScoreStore store = ScoreStore.get(server);
        for (String id : DIRTY) {
            store.save(id);
        }
        DIRTY.clear();
        OPEN.clear();
        CURSORS.clear();
    }

}
