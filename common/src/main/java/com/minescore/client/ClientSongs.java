package com.minescore.client;

import com.minescore.MineScore;
import com.minescore.block.MixerBlockEntity;
import com.minescore.client.audio.TrackLibrary;
import com.minescore.client.screen.AmplifierScreen;
import com.minescore.client.screen.ChannelManagerScreen;
import com.minescore.client.screen.Mp3Screen;
import com.minescore.client.screen.SpeakerScreen;
import com.minescore.hub.HubPackets;
import com.minescore.mp3.Mp3Packets;
import com.minescore.mp3.Mp3State;
import java.util.UUID;
import com.minescore.network.MineScoreNetwork;
import com.minescore.song.SongMeta;
import com.minescore.song.SongPackets;
import com.minescore.song.SongStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The client's song cache, uploads (when cutting) and downloads (when listening). */
public final class ClientSongs implements ClientHooks {
    public static final ClientSongs INSTANCE = new ClientSongs();
    /** Upload chunks sent per client tick (30 KB each). */
    private static final int CHUNKS_PER_TICK = 16;

    /** Songs prepared for cutting, by id, until the server has them. */
    private final Map<String, byte[]> prepared = new HashMap<>();
    private final Deque<SongPackets.UploadChunk> outgoing = new ArrayDeque<>();
    private final Map<String, ByteArrayOutputStream> incoming = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private @Nullable BlockPos statusLathe;
    private @Nullable String statusKey;

    private ClientSongs() {}

    public static Path cacheDir() {
        return SongStore.cacheDir(Minecraft.getInstance().gameDirectory.toPath());
    }

    /** The cached file for a song, if this client has it. */
    public static @Nullable Path cached(String id) {
        if (!SongMeta.validId(id)) {
            return null;
        }
        for (String format : new String[] {"mp3", "ogg", "flac", "msad"}) {
            Path file = cacheDir().resolve(id + "." + format);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }

    /** Asks the server for a song this client doesn't have (once). */
    public void request(String id) {
        if (requested.add(id)) {
            MineScoreNetwork.sendToServer(new SongPackets.RequestSong(id));
        }
    }

    /** Keeps a prepared song ready to upload if the server asks for it. */
    public void prepare(SongMeta meta, byte[] payload) {
        prepared.put(meta.id(), payload);
    }

    public @Nullable String status(BlockPos lathe) {
        return lathe.equals(statusLathe) ? statusKey : null;
    }

    public void clearStatus() {
        statusLathe = null;
        statusKey = null;
    }

    @Override
    public void onSendSong(SongPackets.SendSong packet) {
        byte[] data = prepared.remove(packet.id());
        if (data == null) {
            return;
        }
        for (int from = 0; from < data.length; from += SongPackets.UPLOAD_CHUNK) {
            int to = Math.min(data.length, from + SongPackets.UPLOAD_CHUNK);
            outgoing.add(new SongPackets.UploadChunk(packet.id(), java.util.Arrays.copyOfRange(data, from, to)));
        }
    }

    @Override
    public void onDownloadChunk(SongPackets.DownloadChunk packet) {
        if (!SongMeta.validId(packet.id()) || !SongMeta.validFormat(packet.format())) {
            return;
        }
        ByteArrayOutputStream data = incoming.computeIfAbsent(packet.id(), id -> new ByteArrayOutputStream());
        data.writeBytes(packet.data());
        if (packet.index() + 1 < packet.total()) {
            return;
        }
        incoming.remove(packet.id());
        try {
            Files.write(cacheDir().resolve(packet.id() + "." + packet.format()), data.toByteArray());
            TrackLibrary.retry("song:" + packet.id());
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't save downloaded song {}", packet.id(), e);
        }
        requested.remove(packet.id());
    }

    @Override
    public void onScoreSnapshot(com.minescore.score.ScorePackets.Snapshot packet) {
        com.minescore.client.score.ClientScores.onSnapshot(packet);
    }

    @Override
    public void onScorePeer(com.minescore.score.ScorePackets.PeerCursor packet) {
        com.minescore.client.score.ClientScores.onPeer(packet);
    }

    @Override
    public void onScoreEdited(com.minescore.score.ScorePackets.Edited packet) {
        com.minescore.client.score.ClientScores.onEdited(packet);
    }

    @Override
    public void onSynthSound(com.minescore.audio.SynthSound.Play packet) {
        com.minescore.client.audio.SynthSounds.play(packet);
    }

    @Override
    public void onLatheStatus(SongPackets.LatheStatus packet) {
        statusLathe = packet.lathe();
        statusKey = packet.key();
    }

    @Override
    public void openManual() {
        Minecraft.getInstance().setScreen(new com.minescore.client.screen.ManualScreen());
    }

    @Override
    public void openMp3(UUID id) {
        Minecraft.getInstance().setScreen(new Mp3Screen(Mp3Packets.Target.inventory(id)));
    }

    @Override
    public void openSpeaker(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SpeakerScreen(pos));
    }

    @Override
    public void openAmplifier(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AmplifierScreen(pos));
    }

    @Override
    public void onChannels(HubPackets.Channels packet) {
        if (Minecraft.getInstance().screen instanceof ChannelManagerScreen screen && screen.manager().equals(packet.manager())) {
            screen.update(packet.rows());
        } else if (packet.open()) {
            Minecraft.getInstance().setScreen(new ChannelManagerScreen(packet.manager(), packet.rows()));
        }
    }

    @Override
    public void openMixer(BlockPos pos) {
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof MixerBlockEntity mixer && mixer.docked()) {
            UUID id = Mp3State.idOf(mixer.mp3());
            if (id != null) {
                Minecraft.getInstance().setScreen(new Mp3Screen(new Mp3Packets.Target(id, pos)));
            }
        }
    }

    /** Sends queued upload chunks. Call once per client tick. */
    public void tick() {
        for (int i = 0; i < CHUNKS_PER_TICK && !outgoing.isEmpty(); i++) {
            MineScoreNetwork.sendToServer(outgoing.poll());
        }
    }

    public void reset() {
        prepared.clear();
        outgoing.clear();
        incoming.clear();
        requested.clear();
        clearStatus();
    }
}
