package com.minescore.audio;

import com.minescore.network.MineScoreNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * MineScore's own sound effects, synthesized on each client rather than shipped as recordings (SCOPE.md
 * §11.4), in the sound style the client's packs pick ({@link SynthRecipes}). The server only says which one and where.
 */
public enum SynthSound {
    /** A record spun past the Lathe's top speed snaps and its shards clatter down. */
    RECORD_SHATTER,
    /** A cable's plug going into a jack. */
    JACK_PLUG,
    /** A cable's plug coming out of a jack. */
    JACK_UNPLUG,
    /** Wire Cutters snipping a cable. */
    WIRE_CUT,
    /** A speaker shorting out. */
    SPEAKER_SHORT,
    /** The Vinyl Player's needle dropping onto a record. */
    NEEDLE_DROP,
    /** An MP3 Player clicking into the Audio Mixer's dock. */
    DOCK;

    /** How far away it's heard, in blocks. */
    public static final double RANGE = 24;

    /** Plays it at {@code pos} for every player in earshot. */
    public void play(ServerLevel level, BlockPos pos) {
        Play packet = new Play(this, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(packet.x(), packet.y(), packet.z()) < RANGE * RANGE) {
                MineScoreNetwork.sendToPlayer(player, packet);
            }
        }
    }

    /** Server to client: play a synthesized sound at a point. */
    public record Play(SynthSound sound, double x, double y, double z) {
        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(sound);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
        }

        public static Play read(FriendlyByteBuf buf) {
            return new Play(buf.readEnum(SynthSound.class), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
    }
}
