package com.minescore.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minescore.wiring.Gauge;
import org.junit.jupiter.api.Test;

/** The numbers in SCOPE.md §8.3. */
class RoomAcousticsTest {
    @Test
    void smallGapsSpoilTheSeal() {
        assertEquals(1, RoomAcoustics.seal(1), 1e-9, "fully closed in");
        assertEquals(0.84, RoomAcoustics.seal(0.96), 1e-9, "one small gap (a door) already costs a sixth");
        assertEquals(0, RoomAcoustics.seal(0.75), 1e-9, "a quarter open: like open air");
        assertEquals(0, RoomAcoustics.seal(0), 1e-9);
    }

    @Test
    void bassBuildsUpMost() {
        assertEquals(8, RoomAcoustics.boostDb(1, Gauge.G12), 1e-9);
        assertEquals(4, RoomAcoustics.boostDb(1, Gauge.G16), 1e-9);
        assertEquals(2.4, RoomAcoustics.boostDb(1, Gauge.G20), 1e-9);
        assertEquals(0, RoomAcoustics.boostDb(0, Gauge.G12), 1e-9, "open air plays as before");
    }

    @Test
    void onlyListenersInTheRoomGetIt() {
        assertEquals(1, RoomAcoustics.listenerShare(0), 1e-9);
        assertTrue(RoomAcoustics.listenerShare(6) < 1);
        assertEquals(0, RoomAcoustics.listenerShare(12), 1e-9, "outside, behind a wall");
    }
}
