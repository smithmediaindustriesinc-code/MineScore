package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minescore.audio.OutputChannel;
import com.minescore.playback.VinylChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class EqualizerTest {
    private static double gainDb(float[] bands, double frequency) {
        Equalizer eq = new Equalizer();
        eq.setGains(bands, Track.SAMPLE_RATE);
        double in = 0;
        double out = 0;
        for (int i = 0; i < Track.SAMPLE_RATE; i++) {
            float x = (float) Math.sin(2 * Math.PI * frequency * i / Track.SAMPLE_RATE);
            float y = eq.process(x);
            if (i > Track.SAMPLE_RATE / 2) {
                in += x * x;
                out += y * y;
            }
        }
        return 10 * Math.log10(out / in);
    }

    @Test
    void flatChangesNothing() {
        assertEquals(0, gainDb(new float[10], 1000), 0.01);
    }

    @Test
    void aBandBoostsAndCutsItsOwnFrequency() {
        float[] bands = new float[10];
        bands[5] = 6; // 1 kHz
        assertEquals(6, gainDb(bands, 1000), 0.3);
        assertEquals(0, gainDb(bands, 31), 0.5, "far away bands barely move");
        bands[5] = -9;
        assertEquals(-9, gainDb(bands, 1000), 0.3);
    }

    @Test
    void vinylPlayerStereoFollowsWhereSpeakersStand() {
        BlockPos player = new BlockPos(0, 64, 0);
        // Facing north, so a viewer in front stands to the north looking south: their left is east.
        assertEquals(OutputChannel.LEFT, VinylChannels.channel(Direction.NORTH, player, player.east(3)));
        assertEquals(OutputChannel.RIGHT, VinylChannels.channel(Direction.NORTH, player, player.west(3)));
        assertEquals(OutputChannel.MONO, VinylChannels.channel(Direction.NORTH, player, player.north(4)));
    }
}
