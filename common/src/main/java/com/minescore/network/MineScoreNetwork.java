package com.minescore.network;

import com.minescore.audio.SynthSound;
import com.minescore.block.LatheCore;
import com.minescore.score.ScorePackets;
import com.minescore.score.ScoreSessions;
import com.minescore.block.MeuPackets;
import com.minescore.client.ClientCables;
import com.minescore.client.ClientHooks;
import com.minescore.client.ClientPlayback;
import com.minescore.hub.HubControl;
import com.minescore.hub.HubPackets;
import com.minescore.mp3.Mp3Packets;
import com.minescore.mp3.Mp3Server;
import com.minescore.playback.PlaybackManager;
import com.minescore.song.SongPackets;
import com.minescore.song.SongTransfers;
import com.minescore.wiring.WireNetwork;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class MineScoreNetwork {
    private static NetworkHelper helper;

    private MineScoreNetwork() {}

    public static void register(NetworkHelper network) {
        helper = network;
        network.registerClientbound(CablePackets.Snapshot.class, CablePackets.Snapshot::write, CablePackets.Snapshot::read,
                ClientCables::onSnapshot);
        network.registerClientbound(CablePackets.Upsert.class, CablePackets.Upsert::write, CablePackets.Upsert::read,
                ClientCables::onUpsert);
        network.registerClientbound(CablePackets.Remove.class, CablePackets.Remove::write, CablePackets.Remove::read,
                ClientCables::onRemove);
        network.registerClientbound(PlaybackPackets.Snapshot.class, PlaybackPackets.Snapshot::write,
                PlaybackPackets.Snapshot::read, ClientPlayback::onSnapshot);
        network.registerClientbound(PlaybackPackets.Update.class, PlaybackPackets.Update::write,
                PlaybackPackets.Update::read, ClientPlayback::onUpdate);
        network.registerClientbound(PlaybackPackets.Stop.class, PlaybackPackets.Stop::write, PlaybackPackets.Stop::read,
                ClientPlayback::onStop);

        network.registerServerbound(SongPackets.Cut.class, SongPackets.Cut::write, SongPackets.Cut::read,
                LatheCore::handleCut);
        network.registerServerbound(SongPackets.Copy.class, SongPackets.Copy::write, SongPackets.Copy::read,
                LatheCore::handleCopy);
        network.registerServerbound(SongPackets.UploadChunk.class, SongPackets.UploadChunk::write, SongPackets.UploadChunk::read,
                SongTransfers::onUploadChunk);
        network.registerServerbound(SongPackets.RequestSong.class, SongPackets.RequestSong::write, SongPackets.RequestSong::read,
                SongTransfers::onRequest);
        network.registerServerbound(Mp3Packets.Command.class, Mp3Packets.Command::write, Mp3Packets.Command::read,
                Mp3Server::onCommand);
        network.registerServerbound(Mp3Packets.LoadIntoPlayer.class, Mp3Packets.LoadIntoPlayer::write,
                Mp3Packets.LoadIntoPlayer::read, LatheCore::handleLoad);
        network.registerServerbound(HubPackets.Set.class, HubPackets.Set::write, HubPackets.Set::read, HubControl::onSet);
        network.registerServerbound(MeuPackets.Set.class, MeuPackets.Set::write, MeuPackets.Set::read, MeuPackets::handle);
        network.registerClientbound(HubPackets.Channels.class, HubPackets.Channels::write, HubPackets.Channels::read,
                packet -> ClientHooks.get().onChannels(packet));
        network.registerClientbound(SongPackets.SendSong.class, SongPackets.SendSong::write, SongPackets.SendSong::read,
                packet -> ClientHooks.get().onSendSong(packet));
        network.registerClientbound(SongPackets.DownloadChunk.class, SongPackets.DownloadChunk::write,
                SongPackets.DownloadChunk::read, packet -> ClientHooks.get().onDownloadChunk(packet));
        network.registerServerbound(ScorePackets.Open.class, ScorePackets.Open::write, ScorePackets.Open::read, ScoreSessions::onOpen);
        network.registerServerbound(ScorePackets.Edit.class, ScorePackets.Edit::write, ScorePackets.Edit::read, ScoreSessions::onEdit);
        network.registerServerbound(ScorePackets.Close.class, ScorePackets.Close::write, ScorePackets.Close::read, ScoreSessions::onClose);
        network.registerClientbound(ScorePackets.Snapshot.class, ScorePackets.Snapshot::write, ScorePackets.Snapshot::read,
                packet -> ClientHooks.get().onScoreSnapshot(packet));
        network.registerClientbound(ScorePackets.Edited.class, ScorePackets.Edited::write, ScorePackets.Edited::read,
                packet -> ClientHooks.get().onScoreEdited(packet));
        network.registerClientbound(com.minescore.config.ConfigPackets.Sync.class, com.minescore.config.ConfigPackets.Sync::write,
                com.minescore.config.ConfigPackets.Sync::read, com.minescore.config.ConfigPackets::onSync);
        network.registerServerbound(com.minescore.config.ConfigPackets.Change.class, com.minescore.config.ConfigPackets.Change::write,
                com.minescore.config.ConfigPackets.Change::read, com.minescore.config.ConfigPackets::onChange);
        network.registerServerbound(ScorePackets.Replace.class, ScorePackets.Replace::write, ScorePackets.Replace::read, ScoreSessions::onReplace);
        network.registerServerbound(ScorePackets.Render.class, ScorePackets.Render::write, ScorePackets.Render::read, ScoreSessions::onRender);
        network.registerServerbound(ScorePackets.DeskPlay.class, ScorePackets.DeskPlay::write, ScorePackets.DeskPlay::read,
                com.minescore.block.WritersDeskBlockEntity::onPlay);
        network.registerServerbound(ScorePackets.DeskControl.class, ScorePackets.DeskControl::write, ScorePackets.DeskControl::read,
                com.minescore.block.WritersDeskBlockEntity::onControl);
        network.registerServerbound(ScorePackets.Cursor.class, ScorePackets.Cursor::write, ScorePackets.Cursor::read, ScoreSessions::onCursor);
        network.registerClientbound(ScorePackets.PeerCursor.class, ScorePackets.PeerCursor::write, ScorePackets.PeerCursor::read,
                packet -> ClientHooks.get().onScorePeer(packet));
        network.registerClientbound(SynthSound.Play.class, SynthSound.Play::write, SynthSound.Play::read,
                packet -> ClientHooks.get().onSynthSound(packet));
        network.registerClientbound(SongPackets.LatheStatus.class, SongPackets.LatheStatus::write, SongPackets.LatheStatus::read,
                packet -> ClientHooks.get().onLatheStatus(packet));
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        helper.sendToPlayer(player, message);
    }

    public static void sendToLevel(ServerLevel level, Object message) {
        helper.sendToLevel(level, message);
    }

    public static void sendToServer(Object message) {
        helper.sendToServer(message);
    }

    /** Call whenever a player joins, respawns or changes dimension. */
    public static void syncLevel(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        sendToPlayer(player, new CablePackets.Snapshot(List.copyOf(WireNetwork.get(level).all())));
        sendToPlayer(player, new PlaybackPackets.Snapshot(List.copyOf(PlaybackManager.get(level).all())));
    }
}
