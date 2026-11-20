package com.minescore.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.song.SongMeta;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Mp3StateTest {
    private static SongMeta song(char c, double seconds) {
        return new SongMeta(String.valueOf(c).repeat(64), "mp3", "Song " + c, seconds);
    }

    private Mp3State state;
    private final Random random = new Random(1);

    @BeforeEach
    void setUp() {
        state = new Mp3State();
        state.addSong(song('a', 10));
        state.addSong(song('b', 20));
        state.addSong(song('c', 30));
    }

    private String current() {
        return state.current().orElseThrow().title();
    }

    @Test
    void addingTheSameSongTwiceIsRefused() {
        assertFalse(state.addSong(song('a', 10)));
        assertEquals(3, state.library.size());
    }

    @Test
    void playPauseKeepsThePosition() {
        state.play(0);
        state.pause(100); // 5 s in
        assertEquals(5, state.position(1000), 1e-9);
        state.play(200);
        assertEquals(6, state.position(220), 1e-9);
    }

    @Test
    void songsMoveOnAndStopAtTheEnd() {
        state.play(0);
        assertFalse(state.tick(199, random));
        assertTrue(state.tick(200, random)); // song a (10 s) ended
        assertEquals("Song b", current());
        assertEquals(0, state.position(200), 1e-9);
        assertTrue(state.tick(600, random)); // b ended
        assertTrue(state.tick(1200, random)); // c ended: stop
        assertFalse(state.playing);
        assertEquals("Song a", current());
    }

    @Test
    void repeatOneAndAll() {
        state.cycleRepeat(); // all
        state.jumpTo(2, 0);
        assertTrue(state.tick(600, random));
        assertEquals("Song a", current());
        assertTrue(state.playing);
        state.cycleRepeat(); // one
        assertTrue(state.tick(800, random));
        assertEquals("Song a", current());
    }

    @Test
    void previousRestartsLateInASong() {
        state.jumpTo(1, 0);
        state.previous(100); // 5 s in: restart
        assertEquals("Song b", current());
        state.previous(110); // 0.5 s in: go back
        assertEquals("Song a", current());
    }

    @Test
    void shuffleKeepsTheCurrentSongFirst() {
        state.jumpTo(1, 0);
        state.setShuffle(true, random);
        assertEquals("Song b", current());
        assertEquals(3, state.queue.size());
    }

    @Test
    void removingASongTakesItEverywhere() {
        state.createPlaylist("Mix");
        state.addToPlaylist("Mix", "b".repeat(64));
        state.jumpTo(1, 0);
        state.removeSong("b".repeat(64), 10);
        assertEquals(2, state.library.size());
        assertTrue(state.playlist("Mix").orElseThrow().songs.isEmpty());
        assertFalse(state.queue.contains("b".repeat(64)));
    }

    @Test
    void playlistsPlayOnlyTheirSongs() {
        assertTrue(state.createPlaylist("Mix"));
        assertFalse(state.createPlaylist("Mix"));
        state.addToPlaylist("Mix", "c".repeat(64));
        state.addToPlaylist("Mix", "a".repeat(64));
        state.playPlaylist("Mix", null, 0, random);
        assertEquals("Song c", current());
        assertEquals(2, state.queue.size());
        state.next(10, random);
        assertEquals("Song a", current());
    }

    @Test
    void savesAndLoads() {
        state.createPlaylist("Mix");
        state.addToPlaylist("Mix", "a".repeat(64));
        state.eq[3] = 4.5f;
        state.cycleRepeat();
        state.play(40);
        Mp3State back = Mp3State.load(state.save());
        assertEquals(state.id, back.id);
        assertEquals(3, back.library.size());
        assertEquals(1, back.playlists.size());
        assertEquals(4.5f, back.eq[3]);
        assertEquals(Mp3State.Repeat.ALL, back.repeat);
        assertTrue(back.playing);
        assertEquals(40, back.startTick);
    }
}
