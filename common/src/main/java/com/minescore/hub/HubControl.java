package com.minescore.hub;

import com.minescore.MineScoreSettings;
import com.minescore.audio.OutputChannel;
import com.minescore.block.AmplifierBlockEntity;
import com.minescore.block.ChannelManagerBlockEntity;
import com.minescore.block.MixerBlockEntity;
import com.minescore.block.SpeakerBlockEntity;
import com.minescore.network.MineScoreNetwork;
import com.minescore.playback.PlaybackManager;
import com.minescore.wiring.BlockKind;
import com.minescore.wiring.SpeakerSystems;
import com.minescore.wiring.WireNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Server side of the Amplifier, Channel Manager and speaker screens. */
public final class HubControl {
    /** How close a player must be to the block whose screen they're using. */
    private static final double REACH_SQR = 8 * 8;

    private HubControl() {}

    public static void onSet(HubPackets.Set packet, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(packet.via().getCenter()) > REACH_SQR || !Float.isFinite(packet.f())) {
            return;
        }
        WireNetwork network = WireNetwork.get(level);
        // A screen may only change its own block, or blocks wired after it (a Channel Manager's speakers, a Mixer's amps).
        if (!packet.pos().equals(packet.via()) && !SpeakerSystems.blocks(network::endsAt, packet.via()).contains(packet.pos())) {
            return;
        }
        BlockEntity target = level.getBlockEntity(packet.pos());
        float f = packet.f();
        switch (packet.setting()) {
            case REDSTONE_CONTROL -> {
                if (target instanceof com.minescore.block.RedstoneHub hub) {
                    hub.setRedstoneControl(f > 0.5f);
                }
            }
            case AMP_MASTER -> {
                if (target instanceof AmplifierBlockEntity amp) {
                    amp.setMaster(f);
                }
            }
            case AMP_EQ -> {
                if (target instanceof AmplifierBlockEntity amp) {
                    amp.setEqBand(packet.i(), f);
                }
            }
            case AMP_EQ_RESET -> {
                if (target instanceof AmplifierBlockEntity amp) {
                    amp.resetEq();
                }
            }
            case CM_LEFT, CM_RIGHT, CM_MONO -> {
                if (target instanceof ChannelManagerBlockEntity manager) {
                    switch (packet.setting()) {
                        case CM_LEFT -> manager.setLeftOn(f > 0.5f);
                        case CM_RIGHT -> manager.setRightOn(f > 0.5f);
                        default -> manager.setForceMono(f > 0.5f);
                    }
                }
            }
            case SPEAKER_NAME -> {
                if (target instanceof SpeakerBlockEntity speaker) {
                    speaker.setName(packet.text());
                }
            }
            case SPEAKER_CHANNEL -> {
                if (target instanceof SpeakerBlockEntity speaker && packet.i() >= 0 && packet.i() < OutputChannel.values().length) {
                    OutputChannel channel = OutputChannel.values()[packet.i()];
                    speaker.setChannel(channel);
                    if (channel == OutputChannel.LEFT || channel == OutputChannel.RIGHT) {
                        com.minescore.ModAdvancements.award(player, channel == OutputChannel.LEFT ? "stereo_left" : "stereo_right");
                    }
                }
            }
            case SPEAKER_TRIM -> {
                if (target instanceof SpeakerBlockEntity speaker) {
                    speaker.setTrim(f);
                }
            }
            case MIXER_BOOST -> {
                if (target instanceof MixerBlockEntity mixer) {
                    mixer.setBoost(f);
                }
            }
            case MIXER_EQ, MIXER_EQ_RESET -> {
                if (target instanceof MixerBlockEntity) {
                    for (BlockPos pos : SpeakerSystems.downstream(network::endsAt, packet.pos(), BlockKind.AMPLIFIER)) {
                        if (level.getBlockEntity(pos) instanceof AmplifierBlockEntity amp) {
                            if (packet.setting() == HubPackets.Setting.MIXER_EQ) {
                                amp.setEqBand(packet.i(), f);
                            } else {
                                amp.resetEq();
                            }
                        }
                    }
                }
            }
        }
        if (level.getBlockEntity(packet.via()) instanceof ChannelManagerBlockEntity) {
            sendChannels(player, packet.via(), false);
        }
    }

    /** Sends a Channel Manager's speaker list, to open its screen or refresh it. */
    public static void sendChannels(ServerPlayer player, BlockPos manager, boolean open) {
        ServerLevel level = player.serverLevel();
        WireNetwork network = WireNetwork.get(level);
        Map<BlockPos, Float> ranges = new HashMap<>();
        BlockPos source = SpeakerSystems.sourceOf(network::endsAt, manager);
        if (source != null) {
            PlaybackManager.get(level).at(source).ifPresent(p -> p.speakers().forEach(s -> ranges.put(s.pos(), s.range())));
        }
        List<HubPackets.Row> rows = new ArrayList<>();
        for (SpeakerSystems.Feed feed : SpeakerSystems.speakers(network::endsAt, manager)) {
            if (level.getBlockEntity(feed.speaker()) instanceof SpeakerBlockEntity speaker) {
                rows.add(new HubPackets.Row(feed.speaker(), feed.band(), speaker.name(), speaker.channel(), speaker.trim(), feed.direct(),
                        ranges.getOrDefault(feed.speaker(), (float) MineScoreSettings.hearingRange())));
            }
        }
        MineScoreNetwork.sendToPlayer(player, new HubPackets.Channels(manager, open, rows));
    }
}
