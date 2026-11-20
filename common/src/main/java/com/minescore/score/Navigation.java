package com.minescore.score;

import com.minescore.score.Notation.Marker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order measures are played in: repeats (with 1st/2nd… endings), D.C. and D.S. jumps, To Coda and Fine,
 * the way MuseScore plays them. After a D.C. or D.S. jump, repeats aren't taken again and the last ending
 * is used.
 */
public final class Navigation {
    /** Safety net for scores that jump in circles. */
    private static final int MAX_PLAYED = 20_000;

    private Navigation() {}

    public static List<Integer> order(Score score) {
        List<Bar> bars = score.bars;
        List<Integer> order = new ArrayList<>();
        Map<Integer, Integer> repeatsDone = new HashMap<>();
        int sectionStart = 0;
        int pass = 1;
        int lastPass = 1;
        boolean returning = false;
        boolean jumped = false;
        boolean alFine = false;
        boolean alCoda = false;
        int i = 0;
        while (i < bars.size() && order.size() < MAX_PLAYED) {
            Bar bar = bars.get(i);
            if (bar.repeatStart && !returning) {
                sectionStart = i;
                pass = 1;
            }
            returning = false;
            int playPass = jumped ? lastPass : pass;
            if (bar.endings != 0 && (bar.endings & (1 << (playPass - 1))) == 0) {
                i++; // an ending for another time round
                continue;
            }
            order.add(i);
            if (jumped && alFine && bar.markers.contains(Marker.FINE)) {
                break;
            }
            if (jumped && alCoda && bar.markers.contains(Marker.TO_CODA)) {
                int coda = find(bars, Marker.CODA, i + 1);
                if (coda < 0) {
                    break;
                }
                i = coda;
                continue;
            }
            if (bar.repeatTimes >= 2 && !jumped) {
                int done = repeatsDone.merge(i, 1, Integer::sum);
                if (done < bar.repeatTimes) {
                    pass = done + 1;
                    lastPass = Math.max(lastPass, pass);
                    returning = true;
                    i = sectionStart;
                    continue;
                }
                repeatsDone.remove(i);
                sectionStart = i + 1;
                pass = 1;
            }
            if (!jumped) {
                Marker jump = jumpIn(bar);
                if (jump != null) {
                    jumped = true;
                    alFine = jump == Marker.DC_AL_FINE || jump == Marker.DS_AL_FINE;
                    alCoda = jump == Marker.DC_AL_CODA || jump == Marker.DS_AL_CODA;
                    boolean segno = jump == Marker.DS || jump == Marker.DS_AL_FINE || jump == Marker.DS_AL_CODA;
                    i = segno ? Math.max(0, find(bars, Marker.SEGNO, 0)) : 0;
                    continue;
                }
            }
            i++;
        }
        return order;
    }

    private static Marker jumpIn(Bar bar) {
        for (Marker m : new Marker[] {Marker.DC, Marker.DC_AL_FINE, Marker.DC_AL_CODA, Marker.DS, Marker.DS_AL_FINE, Marker.DS_AL_CODA}) {
            if (bar.markers.contains(m)) {
                return m;
            }
        }
        return null;
    }

    private static int find(List<Bar> bars, Marker marker, int from) {
        for (int i = from; i < bars.size(); i++) {
            if (bars.get(i).markers.contains(marker)) {
                return i;
            }
        }
        return -1;
    }
}
