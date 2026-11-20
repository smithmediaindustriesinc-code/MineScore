package com.minescore.playback;

import com.minescore.audio.OutputChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Stereo for a Vinyl Player, from where its speakers stand. For someone facing its front, speakers on
 * their left play Left and on their right play Right; speakers in line with it (ahead or behind) play Mono.
 */
public final class VinylChannels {
    /** Blocks a speaker must be to one side before it counts as left or right. */
    private static final double SIDE_THRESHOLD = 0.5;

    private VinylChannels() {}

    public static OutputChannel channel(Direction facing, BlockPos player, BlockPos speaker) {
        // Facing the player's front, the viewer's left is the player's facing turned clockwise.
        Direction viewerLeft = facing.getClockWise();
        double side = (speaker.getX() - player.getX()) * viewerLeft.getStepX() + (speaker.getZ() - player.getZ()) * viewerLeft.getStepZ();
        if (side > SIDE_THRESHOLD) {
            return OutputChannel.LEFT;
        }
        if (side < -SIDE_THRESHOLD) {
            return OutputChannel.RIGHT;
        }
        return OutputChannel.MONO;
    }
}
