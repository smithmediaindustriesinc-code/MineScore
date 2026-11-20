package com.minescore.audio;

/** Which part of a stereo track a speaker plays. Speakers with no channel assigned play {@link #MONO}. */
public enum OutputChannel {
    LEFT,
    RIGHT,
    MONO;

    /** This channel's sample from a stereo frame. */
    public float pick(float left, float right) {
        return switch (this) {
            case LEFT -> left;
            case RIGHT -> right;
            case MONO -> (left + right) * 0.5f;
        };
    }
}
