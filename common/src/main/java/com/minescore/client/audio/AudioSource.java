package com.minescore.client.audio;

import com.minescore.audio.Track;

/** What a speaker voice plays: a decoded track as it is, or as it comes out of a Multi-Effects Unit. */
interface AudioSource {
    /** Length in output frames (a slowed-down song is longer). */
    long frames();

    /** Left sample of an output frame, -1 to 1 (0 outside the song). Always asked before {@link #right}. */
    float left(long frame);

    float right(long frame);

    /** A plain track. */
    record Plain(Track track) implements AudioSource {
        @Override
        public long frames() {
            return track.frames();
        }

        @Override
        public float left(long frame) {
            return frame >= 0 && frame < track.frames() ? track.left((int) frame) : 0;
        }

        @Override
        public float right(long frame) {
            return frame >= 0 && frame < track.frames() ? track.right((int) frame) : 0;
        }
    }
}
