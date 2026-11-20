package com.minescore.playback;

import com.minescore.MineScoreSettings;
import com.minescore.audio.EqCurve;
import com.minescore.audio.Loudness;
import com.minescore.audio.OutputChannel;
import com.minescore.block.AmplifierBlockEntity;
import com.minescore.block.ChannelManagerBlockEntity;
import com.minescore.block.MeuBlockEntity;
import com.minescore.block.MixerBlockEntity;
import com.minescore.meu.EffectChain;
import com.minescore.block.PortBlock;
import com.minescore.block.SpeakerBlockEntity;
import com.minescore.block.VinylPlayerBlockEntity;
import com.minescore.power.PowerSettings;
import com.minescore.power.PowerSource;
import com.minescore.power.SystemPower;
import com.minescore.song.SongStore;
import com.minescore.network.MineScoreNetwork;
import com.minescore.network.PlaybackPackets;
import com.minescore.platform.ChunkLoader;
import com.minescore.wiring.BlockKind;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** What is playing in one dimension. Saved with the world and mirrored to its players. */
public final class PlaybackManager extends SavedData {
    private static final String NAME = "minescore_playback";
    /** Ticks between starting a track and its first sample, so every client gets the news in time. */
    public static final int START_DELAY = 10;
    /** Bluetooth: a speaker playing an MP3 Player directly runs at 60% volume and 60% range. */
    public static final float BLUETOOTH_SHARE_DEFAULT = 0.6f;
    /** Bluetooth daisy chains lose more per speaker than wired ones. */
    public static final float BLUETOOTH_CHAIN_LOSS_DEFAULT = 0.15f;

    private static ChunkLoader chunkLoader;

    private final Map<BlockPos, Playback> playing = new LinkedHashMap<>();
    /** Sources and Line Amplifiers knocked out by a short, and the game time it happened. */
    private final Map<BlockPos, Long> trips = new HashMap<>();
    /** Checks in a row each speaker has been driven past its limit. Not saved. */
    private final Map<BlockPos, Integer> strain = new HashMap<>();
    /** Chunks force-loaded for each playing source. Not saved; the loader keeps its own record. */
    private final Map<BlockPos, Set<ChunkPos>> forced = new HashMap<>();
    private ServerLevel level;
    private boolean refreshed;

    public static void setChunkLoader(ChunkLoader loader) {
        chunkLoader = loader;
    }

    /** Reads the saved state without touching speakers or chunks; safe while the level is still loading. */
    public static PlaybackManager peek(ServerLevel level) {
        PlaybackManager manager = level.getDataStorage().computeIfAbsent(PlaybackManager::load, PlaybackManager::new, NAME);
        manager.level = level;
        return manager;
    }

    public static PlaybackManager get(ServerLevel level) {
        PlaybackManager manager = peek(level);
        if (!manager.refreshed) {
            manager.refreshed = true;
            manager.refresh();
        }
        return manager;
    }

    public Collection<Playback> all() {
        return Collections.unmodifiableCollection(playing.values());
    }

    public boolean isPlaying(BlockPos source) {
        return playing.containsKey(source);
    }

    public Optional<Playback> at(BlockPos source) {
        return Optional.ofNullable(playing.get(source));
    }

    /** {@code /minescore play}: starts a track on a Mixer or Desk now. False if the block isn't one. */
    public boolean play(BlockPos source, String track) {
        BlockState state = level.getBlockState(source);
        if (!(state.getBlock() instanceof PortBlock block) || block.layout().kind() != BlockKind.SOURCE) {
            return false;
        }
        start(Playback.Kind.SYSTEM, source, track, level.getGameTime() + START_DELAY, 1, ItemStack.EMPTY);
        return true;
    }

    /**
     * Starts (or restarts) playing at {@code source}, replacing whatever it played. If the same track is
     * already playing from the same moment, only the volume is updated.
     */
    public void start(Playback.Kind kind, BlockPos source, String track, long startTick, float gain, ItemStack disc) {
        Playback old = playing.get(source);
        if (old != null && old.kind() == kind && old.track().equals(track) && old.startTick() == startTick) {
            setGain(source, gain);
            return;
        }
        Playback playback = new Playback(kind, source.immutable(), track, startTick, gain, List.of(), disc.copy(), null);
        if (com.minescore.config.MineScoreConfig.LOG_PLAYBACK.bool()) {
            com.minescore.MineScore.LOGGER.info("Playing {} from {} at {} (start tick {})", track, kind, source, startTick);
        }
        playback = playback.withEffects(effects(playback));
        playback = playback.withSpeakers(speakers(playback));
        playing.put(playback.source(), playback);
        setDirty();
        updateChunks(playback.source());
        MineScoreNetwork.sendToLevel(level, new PlaybackPackets.Update(playback));
        if (old == null) {
            updateComparators(playback.source());
        }
    }

    /** The hubs in a system whose comparator outputs follow whether it plays (SCOPE.md §6). */
    private void updateComparators(BlockPos source) {
        for (BlockPos pos : SpeakerSystems.blocks(WireNetwork.get(level)::endsAt, source)) {
            if (level.getBlockEntity(pos) instanceof com.minescore.block.RedstoneHub) {
                level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
            }
        }
    }

    /** Changes a playing source's volume without restarting it. */
    public void setGain(BlockPos source, float gain) {
        Playback old = playing.get(source);
        if (old == null || old.gain() == gain) {
            return;
        }
        Playback updated = old.withGain(gain);
        updated = updated.withEffects(effects(updated));
        updated = updated.withSpeakers(speakers(updated));
        playing.put(source, updated);
        setDirty();
        MineScoreNetwork.sendToLevel(level, new PlaybackPackets.Update(updated));
    }

    public void stop(BlockPos source) {
        if (playing.remove(source) == null) {
            return;
        }
        if (com.minescore.config.MineScoreConfig.LOG_PLAYBACK.bool()) {
            com.minescore.MineScore.LOGGER.info("Stopped playing at {}", source);
        }
        setDirty();
        updateChunks(source);
        MineScoreNetwork.sendToLevel(level, new PlaybackPackets.Stop(source));
        updateComparators(source);
    }

    /** The wiring changed: work out every playing source's speakers again and tell clients what moved. */
    public void refresh() {
        for (Playback original : new ArrayList<>(playing.values())) {
            EffectChain effects = effects(original);
            float speed = effects == null ? 1 : effects.speed();
            if (original.speed() != speed && level.getBlockEntity(original.source()) instanceof MixerBlockEntity mixer) {
                mixer.onSpeed(speed); // an MEU wired in or out: the Mixer moves the song's timeline and restarts it
            }
            Playback playback = playing.get(original.source());
            if (playback == null) {
                continue;
            }
            List<SpeakerInfo> speakers = speakers(playback.withEffects(effects));
            updateChunks(playback.source());
            if (!speakers.equals(playback.speakers()) || !java.util.Objects.equals(effects, playback.effects())) {
                Playback updated = playback.withEffects(effects).withSpeakers(speakers);
                playing.put(updated.source(), updated);
                MineScoreNetwork.sendToLevel(level, new PlaybackPackets.Update(updated));
            }
        }
    }

    /** The effects a Mixer or Desk system plays through: its Multi-Effects Unit's, if it has one. */
    private @Nullable EffectChain effects(Playback playback) {
        if (playback.kind() != Playback.Kind.SYSTEM) {
            return null;
        }
        BlockPos meu = SpeakerSystems.first(WireNetwork.get(level)::endsAt, playback.source(), BlockKind.MEU);
        return meu != null && level.getBlockEntity(meu) instanceof MeuBlockEntity unit ? unit.chain() : null;
    }

    private List<SpeakerInfo> speakers(Playback playback) {
        return switch (playback.kind()) {
            case SYSTEM -> systemSpeakers(playback.source(), playback.gain(), playback.effects());
            case VINYL -> vinylSpeakers(playback.source(), playback.gain());
            case BLUETOOTH -> bluetoothSpeakers(playback.source(), playback.gain());
        };
    }

    /**
     * A Mixer or Desk system (SCOPE.md §5.1): volume × Mixer boost × Amplifier master × trim × Line
     * Amplifiers, held to the main Amplifier's boost cap (100% without one). Speakers whose channel is
     * switched off, or whose line is knocked out by a short, stay silent.
     */
    private List<SpeakerInfo> systemSpeakers(BlockPos source, float volume, @Nullable EffectChain effects) {
        List<SpeakerInfo> result = new ArrayList<>();
        WireNetwork network = WireNetwork.get(level);
        float boost = level.getBlockEntity(source) instanceof MixerBlockEntity mixer ? mixer.boost() : 1;
        List<SpeakerSystems.Feed> feeds = SpeakerSystems.speakers(network::endsAt, source);
        SystemPower.Demand demand = SystemPower.demand(level, network::endsAt, source, feeds);
        SystemPower.Allocation power = allocate(source, demand);
        if (!power.running()) {
            return result; // not enough power for its hubs: the system is silent
        }
        if (feeds.isEmpty() && level.getBlockEntity(source) instanceof com.minescore.block.WritersDeskBlockEntity) {
            // a desk with no speakers plays from itself, like an unwired Vinyl Player (SCOPE.md §3.6)
            return List.of(new SpeakerInfo(source.immutable(), Vec3.atCenterOf(source), Vec3.ZERO, null, OutputChannel.MONO, volume,
                    (float) MineScoreSettings.hearingRange(), EqCurve.FLAT, -1, 0));
        }
        if (feeds.stream().anyMatch(feed -> feed.chainIndex() >= 9)) {
            com.minescore.ModAdvancements.awardNear(level, source, "daisy_chain");
        }
        for (int i = 0; i < feeds.size(); i++) {
            SpeakerSystems.Feed feed = feeds.get(i);
            if (trips.containsKey(source) || feed.lineAmps().stream().anyMatch(trips::containsKey)) {
                continue;
            }
            OutputChannel channel = OutputChannel.MONO;
            float gain = volume;
            if (feed.channelManager() != null && level.getBlockEntity(feed.channelManager()) instanceof ChannelManagerBlockEntity manager) {
                OutputChannel assigned = level.getBlockEntity(feed.speaker()) instanceof SpeakerBlockEntity speaker
                        ? speaker.channel() : OutputChannel.MONO;
                channel = manager.play(assigned);
                if (channel == null) {
                    continue; // its channel is switched off
                }
                if (level.getBlockEntity(feed.branch()) instanceof SpeakerBlockEntity branch) {
                    gain *= branch.trim();
                }
            }
            float cap = 1;
            EqCurve eq = EqCurve.FLAT;
            if (feed.mainAmp() != null && level.getBlockEntity(feed.mainAmp()) instanceof AmplifierBlockEntity amp) {
                gain *= boost * amp.master();
                cap = SpeakerSystems.boostCap(network::endsAt, feed.mainAmp());
                eq = amp.eq();
            }
            for (BlockPos line : feed.lineAmps()) {
                if (level.getBlockEntity(line) instanceof AmplifierBlockEntity amp) {
                    gain *= amp.master();
                    eq = eq.then(amp.eq());
                }
            }
            gain = Math.min(gain, cap) * power.factor(i);
            speaker(feed.speaker(), channel, gain, boostedRange(gain), eq, powerShown(power, i), demand.speakers().get(i).cost())
                    .ifPresent(result::add);
        }
        return result;
    }

    /** Once a second: what the playing systems earn the players listening (SCOPE.md §12). */
    private void checkAdvancements() {
        for (Playback playback : playing.values()) {
            if (playback.startTick() > level.getGameTime()) {
                continue;
            }
            java.util.Set<com.minescore.wiring.Gauge> bands = new HashSet<>();
            for (SpeakerInfo speaker : playback.speakers()) {
                if (speaker.band() != null && speaker.gain() > 0) {
                    bands.add(speaker.band());
                }
            }
            if (bands.size() == com.minescore.wiring.Gauge.values().length) {
                com.minescore.ModAdvancements.awardNear(level, playback.source(), "full_range");
            }
            if (playback.speed() < 1 && playback.effects() != null
                    && playback.effects().active().stream().anyMatch(c -> c.type() == com.minescore.meu.CardType.REVERB)) {
                com.minescore.ModAdvancements.awardNear(level, playback.source(), "slowed_reverb");
            }
            if (level.getBlockEntity(playback.source()) instanceof PowerSource powered && powered.power().budget() >= 500) {
                com.minescore.ModAdvancements.awardNear(level, playback.source(), "power_budget");
            }
        }
    }

    /** What a source's power budget gives its system right now (SCOPE.md §5.3). */
    private SystemPower.Allocation allocate(BlockPos source, SystemPower.Demand demand) {
        int budget = level.getBlockEntity(source) instanceof PowerSource powered ? powered.power().budget() : 0;
        return SystemPower.allocate(demand, budget, PowerSettings.energyMode());
    }

    private static float powerShown(SystemPower.Allocation power, int speaker) {
        return PowerSettings.energyMode() ? power.factor(speaker) : -1;
    }

    /** Past 100%, a speaker carries further: twice the volume, √2 times the distance. */
    public static float boostedRange(float gain) {
        return (float) (MineScoreSettings.hearingRange() * Math.sqrt(Math.max(1, gain)));
    }

    /**
     * A Vinyl Player plays through the speakers wired to it, Left and Right decided by where they stand
     * (as seen by someone facing its front); with none, it plays from itself, the same all round.
     */
    private List<SpeakerInfo> vinylSpeakers(BlockPos player, float gain) {
        List<SpeakerInfo> wired = new ArrayList<>();
        BlockState state = level.getBlockState(player);
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        WireNetwork network = WireNetwork.get(level);
        List<SpeakerSystems.Feed> feeds = SpeakerSystems.speakers(network::endsAt, player);
        SystemPower.Demand demand = SystemPower.demand(level, network::endsAt, player, feeds);
        SystemPower.Allocation power = allocate(player, demand); // no hubs, so it always runs
        for (int i = 0; i < feeds.size(); i++) {
            SpeakerSystems.Feed feed = feeds.get(i);
            OutputChannel channel = VinylChannels.channel(facing, player, feed.speaker());
            speaker(feed.speaker(), channel, gain * power.factor(i), (float) MineScoreSettings.hearingRange(), EqCurve.FLAT,
                    powerShown(power, i), demand.speakers().get(i).cost()).ifPresent(wired::add);
        }
        if (!wired.isEmpty()) {
            return wired;
        }
        return List.of(new SpeakerInfo(player.immutable(), Vec3.atCenterOf(player), Vec3.ZERO, null, OutputChannel.MONO, gain,
                (float) MineScoreSettings.hearingRange(), EqCurve.FLAT, -1, 0));
    }

    /** The linked speaker and its daisy chain (one speaker type), at 60% volume and range, 15% less each. */
    private List<SpeakerInfo> bluetoothSpeakers(BlockPos speaker, float gain) {
        float bluetooth = com.minescore.config.MineScoreConfig.BLUETOOTH_SHARE.share();
        float share = bluetooth * gain;
        float range = (float) MineScoreSettings.hearingRange() * bluetooth;
        List<SpeakerInfo> result = new ArrayList<>();
        speaker(speaker, OutputChannel.MONO, share, range, EqCurve.FLAT, -1, 0).ifPresent(result::add);
        if (result.isEmpty()) {
            return result;
        }
        for (SpeakerSystems.Feed feed : SpeakerSystems.speakers(WireNetwork.get(level)::endsAt, speaker)) {
            float chained = share * (float) Math.pow(1 - com.minescore.config.MineScoreConfig.BLUETOOTH_LOSS.share(), feed.chainIndex() + 1);
            speaker(feed.speaker(), OutputChannel.MONO, chained, range, EqCurve.FLAT, -1, 0).ifPresent(result::add);
        }
        return result;
    }

    private Optional<SpeakerInfo> speaker(BlockPos pos, OutputChannel channel, float gain, float range, EqCurve eq, float power,
                                          int cost) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PortBlock block) || block.layout().kind() != BlockKind.SPEAKER) {
            return Optional.empty();
        }
        var band = block.layout().ports().get(0).signal().band();
        return Optional.of(new SpeakerInfo(pos.immutable(), block.frontPoint(state, pos), block.frontDirection(state), band, channel,
                gain, range, eq, power, cost));
    }

    // --- shorts (SCOPE.md §5.5) ---------------------------------------------------------------

    /** Once a second: blow any speaker that's been driven too hard for too long. */
    public void tick() {
        if (level.getGameTime() % 20 == 0) {
            checkAdvancements();
        }
        if (level.getGameTime() % 20 != 0 || playing.isEmpty() || !Blowouts.enabled()) {
            return;
        }
        record Blown(Playback playback, BlockPos speaker) {}
        List<Blown> blown = new ArrayList<>();
        Set<BlockPos> strained = new HashSet<>();
        for (Playback playback : playing.values()) {
            double seconds = (level.getGameTime() - playback.startTick()) / 20.0;
            if (seconds < 0) {
                continue;
            }
            Loudness loudness = loudness(playback.track());
            SpeakerInfo worst = null;
            double worstOver = 0;
            for (SpeakerInfo speaker : playback.speakers()) {
                if (speaker.band() == null) {
                    continue;
                }
                double songSeconds = seconds * playback.speed();
                double excess = (loudness == null ? 0 : loudness.excessDb(speaker.band(), songSeconds))
                        + (playback.effects() == null ? 0 : playback.effects().loudnessDb());
                double over = Blowouts.driveDb(speaker.gain(), speaker.eq(), speaker.band(), excess) - Blowouts.limitDb(speaker.band());
                if (over <= 0) {
                    continue;
                }
                strained.add(speaker.pos());
                int checks = strain.merge(speaker.pos(), 1, Integer::sum);
                // Bass first: compare across bands by how far past its own limit each is, lowest band winning ties.
                if (checks >= Blowouts.strainChecks() && (worst == null || over > worstOver)) {
                    worst = speaker;
                    worstOver = over;
                }
            }
            if (worst != null) {
                blown.add(new Blown(playback, worst.pos()));
            }
        }
        strain.keySet().retainAll(strained);
        for (Blown b : blown) {
            if (playing.containsKey(b.playback().source())
                    && level.getBlockState(b.speaker()).getBlock() instanceof PortBlock block && block.layout().kind() == BlockKind.SPEAKER) {
                shortOut(b.playback(), b.speaker());
            }
        }
    }

    private @Nullable Loudness loudness(String track) {
        if (!track.startsWith("song:")) {
            return null;
        }
        return SongStore.get(level.getServer()).loudness(track.substring(5)).orElse(null);
    }

    /**
     * A speaker shorts: it sparks and pops off as an item, taking its cables with it, so everything after
     * it loses its link. What fed it is knocked out until restarted: the nearest Line Amplifier before
     * it, or else the whole system.
     */
    private void shortOut(Playback playback, BlockPos speaker) {
        strain.remove(speaker);
        BlockPos root = playback.source();
        if (playback.kind() == Playback.Kind.SYSTEM) {
            for (SpeakerSystems.Feed feed : SpeakerSystems.speakers(WireNetwork.get(level)::endsAt, playback.source())) {
                if (feed.speaker().equals(speaker) && !feed.lineAmps().isEmpty()) {
                    root = feed.lineAmps().get(feed.lineAmps().size() - 1);
                }
            }
        }
        Vec3 at = Vec3.atCenterOf(speaker);
        level.sendParticles(com.minescore.registry.ModParticles.SPARK.get(), at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0);
        level.sendParticles(com.minescore.registry.ModParticles.SMOKE.get(), at.x, at.y + 0.2, at.z, 12, 0.3, 0.3, 0.3, 0);
        com.minescore.audio.SynthSound.SPEAKER_SHORT.play(level, speaker);
        com.minescore.ModAdvancements.awardNear(level, speaker, "too_loud");
        trips.put(root.immutable(), level.getGameTime());
        setDirty();
        level.destroyBlock(speaker, true);
        if (root.equals(playback.source())) {
            if (level.getBlockEntity(root) instanceof MixerBlockEntity mixer) {
                mixer.halt();
            } else if (level.getBlockEntity(root) instanceof VinylPlayerBlockEntity vinyl) {
                vinyl.halt();
            } else if (level.getBlockEntity(root) instanceof com.minescore.block.WritersDeskBlockEntity desk) {
                desk.halt();
            } else {
                stop(root);
            }
        }
        refresh();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < 48 * 48) {
                player.displayClientMessage(Component.translatable(
                        root.equals(playback.source()) ? "msg.minescore.short.system" : "msg.minescore.short.line"), true);
            }
        }
    }

    /** Game time a source or Line Amplifier was knocked out by a short, or -1. */
    public long trippedAt(BlockPos pos) {
        return trips.getOrDefault(pos, -1L);
    }

    /** What to tell a player whose source won't start after a short: how to reset it in this power mode. */
    public static String shortResetMessage() {
        return PowerSettings.energyMode() ? "msg.minescore.short.cycle_power" : "msg.minescore.short.wait";
    }

    /**
     * Someone is starting a source again after a short (SCOPE.md §5.5). In Energy mode its power must have
     * been switched off (a whole second with no FE coming in) since the short; in None mode, 5 s must have
     * passed. Until then it's false and the source stays off; after, the source and every Line Amplifier in
     * its system come back.
     */
    public boolean recover(BlockPos source) {
        Set<BlockPos> system = SpeakerSystems.blocks(WireNetwork.get(level)::endsAt, source);
        long latest = -1;
        for (BlockPos pos : system) {
            latest = Math.max(latest, trippedAt(pos));
        }
        if (latest < 0) {
            return true;
        }
        if (PowerSettings.energyMode()) {
            if (!(level.getBlockEntity(source) instanceof PowerSource powered) || !powered.power().switchedOffSince(latest)) {
                return false;
            }
        } else if (level.getGameTime() - latest < Blowouts.recoveryTicks()) {
            return false;
        }
        trips.keySet().removeAll(system);
        setDirty();
        refresh();
        return true;
    }

    /** Force-loads every chunk a playing system touches, and lets go of the rest. */
    private void updateChunks(BlockPos source) {
        if (chunkLoader == null) {
            return;
        }
        Set<ChunkPos> wanted = new HashSet<>();
        if (playing.containsKey(source) && MineScoreSettings.keepPlayingSystemsLoaded()) {
            for (BlockPos pos : SpeakerSystems.blocks(WireNetwork.get(level)::endsAt, source)) {
                wanted.add(new ChunkPos(pos));
            }
        }
        Set<ChunkPos> had = forced.getOrDefault(source, Set.of());
        for (ChunkPos chunk : had) {
            if (!wanted.contains(chunk)) {
                chunkLoader.setForced(level, source, chunk, false);
            }
        }
        for (ChunkPos chunk : wanted) {
            if (!had.contains(chunk)) {
                chunkLoader.setForced(level, source, chunk, true);
            }
        }
        if (wanted.isEmpty()) {
            forced.remove(source);
        } else {
            forced.put(source, wanted);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        playing.values().forEach(p -> list.add(p.save()));
        tag.put("Playing", list);
        ListTag tripList = new ListTag();
        trips.forEach((pos, time) -> {
            CompoundTag trip = NbtUtils.writeBlockPos(pos);
            trip.putLong("At", time);
            tripList.add(trip);
        });
        tag.put("Trips", tripList);
        return tag;
    }

    private static PlaybackManager load(CompoundTag tag) {
        PlaybackManager manager = new PlaybackManager();
        for (Tag t : tag.getList("Playing", Tag.TAG_COMPOUND)) {
            Playback playback = Playback.load((CompoundTag) t);
            manager.playing.put(playback.source(), playback);
        }
        for (Tag t : tag.getList("Trips", Tag.TAG_COMPOUND)) {
            manager.trips.put(NbtUtils.readBlockPos((CompoundTag) t), ((CompoundTag) t).getLong("At"));
        }
        return manager;
    }
}
