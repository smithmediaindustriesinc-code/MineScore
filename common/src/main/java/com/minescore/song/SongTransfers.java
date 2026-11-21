package com.minescore.song;

import com.minescore.MineScore;
import com.minescore.network.MineScoreNetwork;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/** Server side of song uploads (from the player cutting a record) and downloads (to players listening). */
public final class SongTransfers {
    /** Download chunks sent to each player per tick (256 KiB each). */
    private static final int CHUNKS_PER_TICK = 2;

    private record Upload(SongMeta meta, long size, ByteArrayOutputStream data, Consumer<SongMeta> onDone) {}

    private record Download(SongMeta meta, byte[] data, int next) {}

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, Deque<Download>> DOWNLOADS = new HashMap<>();

    private SongTransfers() {}

    /** Asks {@code player} to upload a song; {@code onDone} runs once it has arrived and checked out. */
    public static void requestUpload(ServerPlayer player, SongMeta meta, long size, Consumer<SongMeta> onDone) {
        if (size > com.minescore.config.MineScoreConfig.MAX_SONG_MB.integer() * 1024L * 1024L) { // too big for this server (SCOPE.md §10)
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.minescore.song.too_big",
                    com.minescore.config.MineScoreConfig.MAX_SONG_MB.integer()), false);
            return;
        }
        UPLOADS.put(player.getUUID(), new Upload(meta, size, new ByteArrayOutputStream((int) Math.min(size, 1 << 26)), onDone));
        MineScoreNetwork.sendToPlayer(player, new SongPackets.SendSong(meta.id()));
    }

    /** Whether some player is uploading this song right now. */
    public static boolean uploading(String id) {
        return UPLOADS.values().stream().anyMatch(upload -> upload.meta().id().equals(id));
    }

    public static void onUploadChunk(SongPackets.UploadChunk packet, ServerPlayer player) {
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || !upload.meta().id().equals(packet.id())) {
            return;
        }
        upload.data().writeBytes(packet.data());
        if (upload.data().size() < upload.size()) {
            return;
        }
        UPLOADS.remove(player.getUUID());
        byte[] data = upload.data().toByteArray();
        if (data.length != upload.size() || !sha256(data).equals(upload.meta().id())) {
            MineScore.LOGGER.warn("Song upload from {} didn't match its checksum", player.getGameProfile().getName());
            return;
        }
        try {
            SongStore.get(player.server).write(upload.meta(), data);
            upload.onDone().accept(upload.meta());
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't save song {}", upload.meta().id(), e);
        }
    }

    public static void onRequest(SongPackets.RequestSong packet, ServerPlayer player) {
        SongStore store = SongStore.get(player.server);
        store.meta(packet.id()).ifPresent(meta -> {
            byte[] data = store.read(meta);
            if (data != null) {
                DOWNLOADS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).add(new Download(meta, data, 0));
            }
        });
    }

    /** Sends a little of every queued download. Call once per server tick. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        Iterator<Map.Entry<UUID, Deque<Download>>> it = DOWNLOADS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<Download>> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Deque<Download> queue = entry.getValue();
            if (player == null || queue.isEmpty()) {
                it.remove();
                continue;
            }
            for (int sent = 0; sent < CHUNKS_PER_TICK && !queue.isEmpty(); sent++) {
                Download d = queue.poll();
                int total = Math.max(1, (d.data().length + SongPackets.DOWNLOAD_CHUNK - 1) / SongPackets.DOWNLOAD_CHUNK);
                int from = d.next() * SongPackets.DOWNLOAD_CHUNK;
                int to = Math.min(d.data().length, from + SongPackets.DOWNLOAD_CHUNK);
                byte[] piece = java.util.Arrays.copyOfRange(d.data(), from, to);
                MineScoreNetwork.sendToPlayer(player, new SongPackets.DownloadChunk(d.meta().id(), d.meta().format(), d.next(), total, piece));
                if (d.next() + 1 < total) {
                    queue.addFirst(new Download(d.meta(), d.data(), d.next() + 1));
                }
            }
        }
    }

    public static void onLogout(ServerPlayer player) {
        UPLOADS.remove(player.getUUID());
        DOWNLOADS.remove(player.getUUID());
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
