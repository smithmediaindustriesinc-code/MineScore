package com.minescore.audio.fx;

import com.minescore.audio.Track;
import java.util.Locale;

/**
 * Plays a track at another speed (the Modulation card's Speed effect, 25–200%). Reads from any point and
 * then streams forward: output frame {@code f} after a seek to source frame {@code s} is source time
 * {@code s + f × speed}.
 */
public interface TimeStretch {
    enum Algorithm {
        /** Waveform-similarity overlap-add: keeps the pitch, clean on most music, cheap. The default. */
        WSOLA,
        /** Keeps the pitch, smoother on long notes, heavier, can sound a little "phasey". */
        PHASE_VOCODER,
        /** Varispeed: the pitch follows the speed, like a record. No artefacts. */
        TAPE;

        public static Algorithm byName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return WSOLA;
            }
        }
    }

    /** Starts producing output from this source frame. */
    void seek(double sourceFrame);

    /** Fills the next {@code n} output frames. */
    void read(float[] left, float[] right, int n);

    static TimeStretch create(Algorithm algorithm, Track track, double speed, boolean high) {
        if (Math.abs(speed - 1) < 1e-6) {
            return new TapeStretch(track, 1);
        }
        return switch (algorithm) {
            case TAPE -> new TapeStretch(track, speed);
            case PHASE_VOCODER -> new PhaseVocoderStretch(track, speed);
            case WSOLA -> new WsolaStretch(track, speed, high);
        };
    }

    /** Resampling: at speed 1 an exact copy. */
    final class TapeStretch implements TimeStretch {
        private final Track track;
        private final double speed;
        private double position;

        TapeStretch(Track track, double speed) {
            this.track = track;
            this.speed = speed;
        }

        @Override
        public void seek(double sourceFrame) {
            position = sourceFrame;
        }

        @Override
        public void read(float[] left, float[] right, int n) {
            for (int i = 0; i < n; i++) {
                if (speed == 1 && position == Math.rint(position)) {
                    left[i] = Dsp.sample(track, 0, (long) position);
                    right[i] = Dsp.sample(track, 1, (long) position);
                } else {
                    left[i] = Dsp.sampleAt(track, 0, position);
                    right[i] = Dsp.sampleAt(track, 1, position);
                }
                position += speed;
            }
        }
    }
}
