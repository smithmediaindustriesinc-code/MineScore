package com.minescore.client.score;

import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Bar;
import com.minescore.score.Chord;
import com.minescore.score.Frac;
import com.minescore.score.Notation.Articulation;
import com.minescore.score.Notation.Barline;
import com.minescore.score.Notation.Clef;
import com.minescore.score.Notation.Marker;
import com.minescore.score.Notation.Ornament;
import com.minescore.score.Notation.TimeSig;
import com.minescore.score.Note;
import com.minescore.score.Part;
import com.minescore.score.Score;
import com.minescore.score.VoiceEdit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Lays a score out the way MuseScore's continuous view does (SCOPE.md §8.1): each part a row of staves
 * running sideways, measures spaced by their notes' lengths, and draws it: staves, clefs, key and time
 * signatures, notes, rests, beams, accidentals, ties, marks and text. It also says what's under the mouse.
 *
 * <p>Coordinates are in "score space": x from the first measure's left, y from the top of the title block.
 */
public final class ScoreView {
    public static final int INK = 0xFF2A2630, PAPER = 0xFFF4ECD8, SELECTED = 0xFF2F6FE0, OUTSIDE = 0xFFE07A1E,
            VOICE_2 = 0xFF2E8B3E, VOICE_3 = 0xFFB0602E, VOICE_4 = 0xFF8E3FA6, FAINT = 0xFFB8AE98;
    private static final int[] VOICE_COLORS = {INK, VOICE_2, VOICE_3, VOICE_4};
    private static final int[] SHARP_STEPS = {3, 0, 4, 1, 5, 2, 6}, FLAT_STEPS = {6, 2, 5, 1, 4, 0, 3};
    /** Treble-clef positions of each key signature accidental (staff steps), in the order they're added. */
    private static final int[] SHARP_POS = {38, 35, 39, 36, 33, 37, 34}, FLAT_POS = {34, 37, 33, 36, 32, 35, 31};

    private final Score score;
    private final Font font;
    /** One staff space, in GUI pixels. */
    public final float space;
    private final float scale;
    public final float titleHeight;
    public final float rowHeight;
    private final float[] measureX;
    private final float[] headerWidth;
    /** Per measure: the time positions things start at, and their x from the measure's left. */
    private final List<Frac>[] slicePos;
    private final List<Float>[] sliceX;
    /** Where each selectable mark was drawn last frame, in screen pixels, for clicking on. */
    private final List<Box> boxes = new ArrayList<>();
    /** The mark selected on its own, drawn in the selection colour. */
    public @Nullable ScoreElement selectedElement;

    private record Box(float x1, float y1, float x2, float y2, ScoreElement element) {}

    @SuppressWarnings("unchecked")
    public ScoreView(Score score, Font font, float space) {
        this.score = score;
        this.font = font;
        this.space = space;
        this.scale = space / ScoreGlyphs.SPACE;
        this.titleHeight = 6 * space + 24;
        this.rowHeight = 14 * space;
        int n = score.measureCount();
        measureX = new float[n + 1];
        headerWidth = new float[n];
        slicePos = new List[n];
        sliceX = new List[n];
        float x = 0;
        for (int m = 0; m < n; m++) {
            measureX[m] = x;
            headerWidth[m] = header(m);
            layoutMeasure(m);
            List<Float> xs = sliceX[m];
            x += xs.get(xs.size() - 1) + space;
        }
        measureX[n] = x;
    }

    public Score score() {
        return score;
    }

    public float width() {
        return measureX[measureX.length - 1];
    }

    public float height() {
        return titleHeight + rowHeight * score.parts.size();
    }

    public float measureLeft(int m) {
        return measureX[m];
    }

    public float measureRight(int m) {
        return measureX[m + 1];
    }

    /** The top line of part {@code p}'s staff. */
    public float staffTop(int p) {
        return titleHeight + p * rowHeight + 5 * space;
    }

    // --- layout -----------------------------------------------------------------------------

    private boolean showsClef(int m) {
        if (m == 0) {
            return true;
        }
        for (Part part : score.parts) {
            if (part.measures.get(m).clefChange != null) {
                return true;
            }
        }
        return false;
    }

    private boolean showsKey(int m) {
        return m == 0 ? score.key != 0 : score.bars.get(m).keyChange != null;
    }

    private boolean showsTime(int m) {
        return m == 0 || score.bars.get(m).timeChange != null;
    }

    /** Room for the clef, key and time signature at a measure's start. */
    private float header(int m) {
        float w = space;
        if (showsClef(m)) {
            w += (m == 0 ? 3.2f : 2.6f) * space;
        }
        if (showsKey(m)) {
            w += Math.max(1, Math.abs(score.keyAt(m))) * 1.1f * space + 0.6f * space;
        }
        if (showsTime(m)) {
            w += 2.4f * space;
        }
        return w;
    }

    private void layoutMeasure(int m) {
        Frac length = score.lengthOf(m);
        TreeSet<Frac> starts = new TreeSet<>();
        starts.add(Frac.ZERO);
        Map<Frac, Boolean> accidental = new HashMap<>();
        Map<Frac, Boolean> dotted = new HashMap<>();
        for (Part part : score.parts) {
            for (List<Chord> voice : part.measures.get(m).voices) {
                List<Frac> at = VoiceEdit.starts(voice);
                for (int i = 0; i < voice.size(); i++) {
                    Chord c = voice.get(i);
                    if (c.isGrace() || at.get(i).compareTo(length) >= 0) {
                        continue;
                    }
                    starts.add(at.get(i));
                    if (c.notes.stream().anyMatch(n -> n.alter() != 0)) {
                        accidental.put(at.get(i), true);
                    }
                    if (c.duration.dots() > 0) {
                        dotted.put(at.get(i), true);
                    }
                }
            }
        }
        List<Frac> pos = new ArrayList<>(starts);
        List<Float> xs = new ArrayList<>();
        float x = headerWidth[m] + space * 0.8f;
        for (int i = 0; i < pos.size(); i++) {
            Frac next = i + 1 < pos.size() ? pos.get(i + 1) : length;
            double d = Math.max(1 / 64.0, next.minus(pos.get(i)).toDouble());
            float w = (float) Math.max(1.8, 1.4 + 1.1 * (Math.log(d * 32) / Math.log(2))) * space;
            if (accidental.getOrDefault(pos.get(i), false)) {
                x += space * 1.1f;
            }
            xs.add(x);
            x += w + (dotted.getOrDefault(pos.get(i), false) ? 0.6f * space : 0);
        }
        xs.add(x); // the end
        pos.add(length);
        slicePos[m] = pos;
        sliceX[m] = xs;
    }

    /** The x (from the measure's left) of a time position, between slices if it falls between them. */
    public float xAt(int m, Frac at) {
        List<Frac> pos = slicePos[m];
        List<Float> xs = sliceX[m];
        for (int i = 0; i < pos.size() - 1; i++) {
            if (at.compareTo(pos.get(i + 1)) < 0) {
                double t = at.minus(pos.get(i)).divide(pos.get(i + 1).minus(pos.get(i))).toDouble();
                return (float) (xs.get(i) + t * (xs.get(i + 1) - xs.get(i)));
            }
        }
        return xs.get(xs.size() - 1);
    }

    /** The screen y of a staff step on a staff whose top line is drawn at {@code top}. */
    private float yOn(float top, int p, int m, int step) {
        return top + (score.parts.get(p).clefAt(m).middleLine() + 4 - step) * space / 2;
    }

    /** The y of a staff step (octave × 7 + letter) on part {@code p}'s staff, in score space. */
    public float stepY(int p, int m, int step) {
        int top = score.parts.get(p).clefAt(m).middleLine() + 4;
        return staffTop(p) + (top - step) * space / 2;
    }

    /** The staff step nearest a y on part {@code p}'s staff. */
    public int stepAt(int p, int m, float y) {
        int top = score.parts.get(p).clefAt(m).middleLine() + 4;
        return top - Math.round((y - staffTop(p)) / (space / 2));
    }

    // --- hit testing ------------------------------------------------------------------------

    /** What's under a point: a part, a measure, the time position it's nearest, and the staff step. */
    public record Hit(int part, int measure, Frac at, int step) {}

    public @Nullable Hit hit(float x, float y) {
        if (y < titleHeight || score.parts.isEmpty()) {
            return null;
        }
        int p = Math.min(score.parts.size() - 1, (int) ((y - titleHeight) / rowHeight));
        int m = measureAt(x);
        if (m < 0) {
            return null;
        }
        float local = x - measureX[m];
        List<Frac> pos = slicePos[m];
        List<Float> xs = sliceX[m];
        int best = 0;
        for (int i = 0; i < pos.size() - 1; i++) {
            if (Math.abs(xs.get(i) - local) < Math.abs(xs.get(best) - local)) {
                best = i;
            }
        }
        return new Hit(p, m, pos.get(best), stepAt(p, m, y));
    }

    /** Whether a point (score space) is on or near a staff, rather than in the open space around them. */
    public boolean onStaff(float x, float y) {
        if (y < titleHeight || x < 0 || x >= width() || score.parts.isEmpty()) {
            return false;
        }
        int p = (int) ((y - titleHeight) / rowHeight);
        if (p >= score.parts.size()) {
            return false;
        }
        float top = staffTop(p);
        return y >= top - 3 * space && y <= top + 7 * space;
    }

    /** The mark drawn at a screen point last frame, if any. */
    public @Nullable ScoreElement elementAt(double x, double y) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box b = boxes.get(i);
            if (x >= b.x1() && x < b.x2() && y >= b.y1() && y < b.y2()) {
                return b.element();
            }
        }
        return null;
    }

    /** Where a mark was drawn last frame (x1, y1, x2, y2 on screen), or null if it wasn't. */
    public float @Nullable [] boxOf(ScoreElement e) {
        for (Box b : boxes) {
            if (b.element().equals(e)) {
                return new float[] {b.x1(), b.y1(), b.x2(), b.y2()};
            }
        }
        return null;
    }

    private void box(float x1, float y1, float x2, float y2, ScoreElement e) {
        boxes.add(new Box(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2), e));
    }

    private int colorOf(ScoreElement e, int color) {
        return e.equals(selectedElement) ? SELECTED : color;
    }

    public int measureAt(float x) {
        if (x < 0 || x >= width()) {
            return -1;
        }
        int lo = 0, hi = score.measureCount() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (measureX[mid] <= x) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    // --- drawing ----------------------------------------------------------------------------

    /** What the editor wants highlighted. */
    public interface Marks {
        /** Colour for a note (or the chord when {@code note} is -1), or 0 for its voice's colour. */
        int highlight(int part, int measure, int voice, int chord, int note);
    }

    /**
     * Draws the score with its top-left at (left, top) on screen, scrolled by (sx, sy), only what falls in
     * the clip (cx1..cx2, cy1..cy2).
     */
    public void draw(GuiGraphics g, float left, float top, float sx, float sy, float cx1, float cy1, float cx2, float cy2, Marks marks) {
        boxes.clear();
        float ox = left - sx, oy = top - sy;
        // titles
        if (oy + titleHeight > cy1) {
            String title = score.title.isBlank() ? "Untitled Score" : score.title;
            g.pose().pushPose();
            g.pose().translate((cx1 + cx2) / 2, oy + 6, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(font, title, -font.width(title) / 2, 0, INK, false);
            g.pose().popPose();
            if (!score.subtitle.isBlank()) {
                g.drawString(font, score.subtitle, (int) ((cx1 + cx2) / 2 - font.width(score.subtitle) / 2f), (int) (oy + 22), INK, false);
            }
            if (!score.composer.isBlank()) {
                g.drawString(font, score.composer, (int) (cx2 - 8 - font.width(score.composer)), (int) (oy + titleHeight - 16), INK, false);
            }
            if (!score.lyricist.isBlank()) {
                g.drawString(font, score.lyricist, (int) (cx1 + 8), (int) (oy + titleHeight - 16), INK, false);
            }
        }
        int first = Math.max(0, measureAt(Math.max(0, cx1 - ox)));
        int last = measureAt(Math.min(width() - 1, cx2 - ox));
        if (last < 0) {
            last = score.measureCount() - 1;
        }
        for (int p = 0; p < score.parts.size(); p++) {
            float rowTop = oy + titleHeight + p * rowHeight;
            if (rowTop > cy2 || rowTop + rowHeight < cy1) {
                continue;
            }
            for (int m = first; m <= last; m++) {
                drawMeasure(g, p, m, ox + measureX[m], oy, marks);
            }
            drawLines(g, p, ox, oy, first, last);
        }
        drawBarMarks(g, ox, oy, first, last);
    }

    private float lineThickness() {
        return Math.max(1, space / 8);
    }

    private void hLine(GuiGraphics g, float x1, float x2, float y, int color) {
        float t = lineThickness();
        fill(g, x1, y - t / 2, x2, y + t / 2, color);
    }

    private static void fill(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        g.fill(Math.round(x1), Math.round(y1), Math.max(Math.round(x1) + 1, Math.round(x2)), Math.max(Math.round(y1) + 1, Math.round(y2)), color);
    }

    private void drawMeasure(GuiGraphics g, int p, int m, float x, float oy, Marks marks) {
        Part part = score.parts.get(p);
        float top = oy + staffTop(p);
        float right = x + (measureX[m + 1] - measureX[m]);
        for (int i = 0; i < 5; i++) {
            hLine(g, x, right, top + i * space, INK);
        }
        Bar bar = score.bars.get(m);
        // barline at the end
        float bottom = top + 4 * space;
        Barline line = m == score.measureCount() - 1 && bar.barline == Barline.NORMAL ? Barline.FINAL : bar.barline;
        if (bar.repeatTimes >= 2) {
            fill(g, right - 0.5f * space, top, right, bottom, INK);
            fill(g, right - 1.0f * space, top, right - 0.8f * space, bottom, INK);
            dots(g, right - 1.6f * space, top);
        } else {
            switch (line) {
                case DOUBLE -> {
                    fill(g, right - 0.5f * space, top, right - 0.5f * space + lineThickness(), bottom, INK);
                    fill(g, right - lineThickness(), top, right, bottom, INK);
                }
                case FINAL -> {
                    fill(g, right - 0.5f * space, top, right, bottom, INK);
                    fill(g, right - 1.0f * space, top, right - 1.0f * space + lineThickness(), bottom, INK);
                }
                case DASHED -> {
                    for (float y = top; y < bottom; y += space / 2) {
                        fill(g, right - lineThickness(), y, right, Math.min(bottom, y + space / 4), INK);
                    }
                }
                default -> fill(g, right - lineThickness(), top, right, bottom, INK);
            }
        }
        if (bar.repeatStart) {
            float s = x + (m == 0 ? headerWidth[m] - space * 0.4f : 0);
            fill(g, s, top, s + 0.5f * space, bottom, INK);
            fill(g, s + 0.8f * space, top, s + 0.8f * space + lineThickness(), bottom, INK);
            dots(g, s + 1.2f * space, top);
        }
        if (p == 0) {
            g.drawString(font, String.valueOf(m + 1), (int) (x + 1), (int) (top - 2.6f * space - 2), FAINT, false);
        }
        // header: clef, key, time
        float hx = x + space * 0.4f;
        Clef clef = part.clefAt(m);
        if (showsClef(m)) {
            if (m == 0 || part.measures.get(m).clefChange != null) {
                drawClef(g, clef, hx, top, m == 0 ? 1 : 0.8f);
            }
            hx += (m == 0 ? 3.2f : 2.6f) * space;
        }
        if (showsKey(m)) {
            int key = score.keyAt(m);
            int offset = switch (clef) {
                case BASS -> -14;
                case ALTO -> -7;
                case TENOR -> -9;
                default -> 0;
            };
            int[] positions = key >= 0 ? SHARP_POS : FLAT_POS;
            for (int i = 0; i < Math.abs(key); i++) {
                // positions are written for the treble staff; other clefs move them by whole octaves of letters
                float y = top + ((clef.middleLine() + 4) - (positions[i] + offset)) * space / 2;
                ScoreGlyphs.draw(g, key >= 0 ? "sharp" : "flat", hx + i * 1.1f * space, y, scale, INK);
            }
            hx += Math.max(1, Math.abs(key)) * 1.1f * space + 0.6f * space;
        }
        if (showsTime(m)) {
            TimeSig time = score.timeAt(m);
            if (time.symbol() && time.beats() == 4 && time.unit() == 4) {
                ScoreGlyphs.draw(g, "time_common", hx, top + 2 * space, scale, INK);
            } else if (time.symbol() && time.beats() == 2 && time.unit() == 2) {
                ScoreGlyphs.draw(g, "time_cut", hx, top + 2 * space, scale, INK);
            } else {
                drawNumber(g, time.beats(), hx, top + space);
                drawNumber(g, time.unit(), hx, top + 3 * space);
            }
        }
        // the voices
        Part.Measure measure = part.measures.get(m);
        int used = 0;
        for (List<Chord> v : measure.voices) {
            if (v.stream().anyMatch(c -> !c.isRest())) {
                used++;
            }
        }
        Map<Integer, Integer> accidentals = new HashMap<>(); // staff step -> alteration so far in this measure
        for (int v = 0; v < Part.VOICES; v++) {
            drawVoice(g, p, m, v, x, top, used > 1, accidentals, marks);
        }
    }

    private void dots(GuiGraphics g, float x, float top) {
        ScoreGlyphs.draw(g, "dot", x, top + 1.5f * space, scale, INK);
        ScoreGlyphs.draw(g, "dot", x, top + 2.5f * space, scale, INK);
    }

    private void drawNumber(GuiGraphics g, int n, float x, float y) {
        String s = String.valueOf(n);
        for (int i = 0; i < s.length(); i++) {
            ScoreGlyphs.draw(g, "time_" + s.charAt(i), x + i * 1.3f * space, y, scale, INK);
        }
    }

    private void drawClef(GuiGraphics g, Clef clef, float x, float top, float size) {
        float s = scale * size;
        switch (clef) {
            case TREBLE -> ScoreGlyphs.draw(g, "clef_treble", x, top + 3 * space, s, INK);
            case TREBLE_8VB -> ScoreGlyphs.draw(g, "clef_treble_8vb", x, top + 3 * space, s, INK);
            case BASS -> ScoreGlyphs.draw(g, "clef_bass", x, top + space, s, INK);
            case ALTO -> ScoreGlyphs.draw(g, "clef_c", x, top + 2 * space, s, INK);
            case TENOR -> ScoreGlyphs.draw(g, "clef_c", x, top + space, s, INK);
            case PERCUSSION -> ScoreGlyphs.draw(g, "clef_percussion", x + space * 0.5f, top + 2 * space, s, INK);
        }
    }

    /** The key signature's alteration for a letter (0 = C … 6 = B) in a key of {@code fifths}. */
    public static int keyAlter(int fifths, int step) {
        int[] order = fifths >= 0 ? SHARP_STEPS : FLAT_STEPS;
        for (int i = 0; i < Math.abs(fifths); i++) {
            if (order[i] == step) {
                return fifths >= 0 ? 1 : -1;
            }
        }
        return 0;
    }

    private void drawVoice(GuiGraphics g, int p, int m, int v, float x, float top, boolean multi, Map<Integer, Integer> accidentals, Marks marks) {
        Part part = score.parts.get(p);
        List<Chord> voice = part.measures.get(m).voices[v];
        if (voice.isEmpty()) {
            return;
        }
        Frac length = score.lengthOf(m);
        List<Frac> starts = VoiceEdit.starts(voice);
        int middle = part.clefAt(m).middleLine();
        int key = score.keyAt(m);
        boolean percussion = part.clefAt(m) == Clef.PERCUSSION;
        TimeSig time = score.timeAt(m);
        // beams: runs of flagged notes within one beat
        List<List<Integer>> beams = new ArrayList<>();
        List<Integer> run = new ArrayList<>();
        long runBeat = -1;
        for (int i = 0; i < voice.size(); i++) {
            Chord c = voice.get(i);
            long beat = (long) Math.floor(starts.get(i).divide(time.beat()).toDouble() + 1e-9);
            boolean flagged = !c.isRest() && !c.isGrace() && c.duration.log() >= 3;
            if (!flagged || beat != runBeat) {
                if (run.size() > 1) {
                    beams.add(run);
                }
                run = new ArrayList<>();
                runBeat = beat;
            }
            if (flagged) {
                run.add(i);
            }
        }
        if (run.size() > 1) {
            beams.add(run);
        }
        Map<Integer, List<Integer>> beamOf = new HashMap<>();
        for (List<Integer> b : beams) {
            for (int i : b) {
                beamOf.put(i, b);
            }
        }
        float stemLength = 3.5f * space;
        Map<Integer, Float> stemEnd = new HashMap<>();
        Map<Integer, Boolean> stemUp = new HashMap<>();
        // where each beam sits
        for (List<Integer> b : beams) {
            int sum = 0, count = 0;
            for (int i : b) {
                for (Note n : voice.get(i).notes) {
                    sum += n.staffStep() - middle;
                    count++;
                }
            }
            boolean up = multi ? v % 2 == 0 : sum <= 0;
            float y = up ? Float.MAX_VALUE : -Float.MAX_VALUE;
            for (int i : b) {
                for (Note n : voice.get(i).notes) {
                    float ny = yOn(top, p, m, n.staffStep());
                    y = up ? Math.min(y, ny - stemLength) : Math.max(y, ny + stemLength);
                }
            }
            for (int i : b) {
                stemEnd.put(i, y);
                stemUp.put(i, up);
            }
        }
        for (int i = 0; i < voice.size(); i++) {
            Chord c = voice.get(i);
            Frac at = starts.get(i);
            if (at.compareTo(length) >= 0) {
                break;
            }
            float cx = x + xAt(m, at);
            int color = VOICE_COLORS[v];
            int chordMark = marks.highlight(p, m, v, i, -1);
            if (chordMark != 0) {
                color = chordMark;
            }
            if (c.isGrace()) {
                drawGrace(g, p, m, c, cx - 1.6f * space, top, color);
                continue;
            }
            if (c.isRest()) {
                boolean wholeMeasure = v == 0 && voice.size() == 1 && c.length().equals(length);
                float rx = wholeMeasure ? x + (xAt(m, Frac.ZERO) + sliceX[m].get(sliceX[m].size() - 1)) / 2 - 5 * scale : cx;
                drawRest(g, c, rx, top + (multi ? (v % 2 == 0 ? -2 : 2) * space : 0), wholeMeasure, color);
                continue;
            }
            // notes
            boolean up = stemUp.getOrDefault(i, multi ? v % 2 == 0 : averageStep(c) <= middle);
            float headW = ScoreGlyphs.width(headFor(c), scale);
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            List<Note> sorted = new ArrayList<>(c.notes);
            sorted.sort((a, b) -> Integer.compare(a.staffStep(), b.staffStep()));
            int prevStep = Integer.MIN_VALUE;
            boolean shifted = false;
            for (Note n : sorted) {
                int step = n.staffStep();
                float ny = yOn(top, p, m, step);
                minY = Math.min(minY, ny);
                maxY = Math.max(maxY, ny);
                shifted = step - prevStep == 1 && !shifted; // a second: the upper head steps aside
                prevStep = step;
                float hx = cx + (shifted ? (up ? headW : -headW) : 0);
                int noteColor = color;
                int mark = marks.highlight(p, m, v, i, c.notes.indexOf(n));
                if (mark != 0) {
                    noteColor = mark;
                } else if (!percussion && com.minescore.score.Instruments.outsideRange(part, n.pitch() + part.clefAt(m).octaveShift())) {
                    noteColor = OUTSIDE;
                }
                ledgers(g, p, m, step, hx, headW, top);
                String head = n.drum() == NoteInstrument.HAT ? "head_x" : headFor(c);
                ScoreGlyphs.draw(g, head, hx, ny, scale, noteColor);
                // accidentals the key and the measure so far don't already give
                if (!percussion) {
                    int current = accidentals.getOrDefault(step, keyAlter(key, n.step()));
                    if (n.alter() != current) {
                        String glyph = switch (n.alter()) {
                            case 2 -> "double_sharp";
                            case 1 -> "sharp";
                            case -1 -> "flat";
                            case -2 -> "double_flat";
                            default -> "natural";
                        };
                        ScoreGlyphs.draw(g, glyph, hx - ScoreGlyphs.width(glyph, scale) - 1.5f * scale, ny, scale, noteColor);
                        accidentals.put(step, n.alter());
                    }
                }
                for (int d = 0; d < c.duration.dots(); d++) {
                    float dy = (step % 2 == (middle + 4) % 2) ? -space / 2 : 0; // a note on a line puts its dot in the space above
                    ScoreGlyphs.draw(g, "dot", hx + headW + (2 + d * 4) * scale, ny + dy, scale, noteColor);
                }
                if (n.tie()) {
                    drawTie(g, hx + headW, ny, nextX(p, m, v, i, x), up);
                }
            }
            // stem, flags
            if (c.duration.log() >= 1) {
                float sx = up ? cx + headW - lineThickness() : cx;
                float end = stemEnd.getOrDefault(i, up ? minY - stemLength : maxY + stemLength);
                float from = up ? maxY : minY;
                fill(g, sx, Math.min(from, end), sx + lineThickness(), Math.max(from, end), color);
                if (!beamOf.containsKey(i) && c.duration.log() >= 3) {
                    String flag = "flag_" + (c.duration.log() == 3 ? 8 : c.duration.log() == 4 ? 16 : c.duration.log() == 5 ? 32 : 64)
                            + (up ? "_up" : "_down");
                    ScoreGlyphs.draw(g, flag, sx, end, scale, color);
                }
            }
            drawMarks(g, c, p, m, v, i, cx, headW, up ? maxY + 1.4f * space : minY - 1.4f * space, top, color);
            if (c.arpeggio != com.minescore.score.Notation.Arpeggio.NONE) {
                ScoreElement e = ScoreElement.onChord(ScoreElement.Kind.ARPEGGIO, p, m, v, i, 0);
                boolean altered = c.notes.stream().anyMatch(n -> n.alter() != 0);
                float ax = cx - space * (altered ? 2.4f : 1.2f);
                drawArpeggio(g, ax, minY - space * 0.7f, maxY + space * 0.7f, c.arpeggio, space, colorOf(e, color));
                box(ax - space * 0.4f, minY - space, ax + space * 0.6f, maxY + space, e);
            }
            if (c.tuplet != null && (i == 0 || voice.get(i - 1).tuplet == null || voice.get(i - 1).tuplet.id() != c.tuplet.id())) {
                String num = String.valueOf(c.tuplet.actual());
                float ty = up ? stemEnd.getOrDefault(i, minY - stemLength) - space * 1.4f : stemEnd.getOrDefault(i, maxY + stemLength) + space * 0.4f;
                g.drawString(font, num, (int) (cx + headW), (int) ty, color, false);
            }
            // lyrics, chord symbols, text
            float below = lyricY(top, 0);
            for (int verse = 0; verse < c.lyrics.size(); verse++) {
                String syl = c.lyrics.get(verse);
                if (!syl.isEmpty()) {
                    ScoreElement e = ScoreElement.onChord(ScoreElement.Kind.LYRIC, p, m, v, i, verse);
                    text(g, syl, cx + headW / 2 - font.width(syl) / 2f, below + verse * 10, e);
                }
            }
            if (c.chordSymbol != null) {
                text(g, c.chordSymbol, cx, chordSymbolY(top), ScoreElement.onChord(ScoreElement.Kind.CHORD_SYMBOL, p, m, v, i, 0));
            }
            if (c.staffText != null) {
                text(g, c.staffText, cx, staffTextY(top), ScoreElement.onChord(ScoreElement.Kind.STAFF_TEXT, p, m, v, i, 0));
            }
        }
        for (List<Integer> b : beams) {
            drawBeam(g, voice, b, starts, x, m, p, stemEnd, stemUp, VOICE_COLORS[v]);
        }
    }

    /** Screen y of a verse's lyrics, of chord symbols and of staff text, on a staff whose top line is at {@code top}. */
    public float lyricY(float top, int verse) {
        return top + 8.2f * space + verse * 10;
    }

    public float chordSymbolY(float top) {
        return top - 3.4f * space;
    }

    public float staffTextY(float top) {
        return top - 2.4f * space;
    }

    /** Text that can be selected. */
    private void text(GuiGraphics g, String text, float x, float y, ScoreElement e) {
        g.drawString(font, text, (int) x, (int) y, colorOf(e, INK), false);
        box(x - 1, y - 1, x + font.width(text) + 1, y + 9, e);
    }

    private static int averageStep(Chord c) {
        int sum = 0;
        for (Note n : c.notes) {
            sum += n.staffStep();
        }
        return c.notes.isEmpty() ? 0 : Math.round(sum / (float) c.notes.size());
    }

    private static String headFor(Chord c) {
        return switch (c.duration.log()) {
            case -1 -> "head_breve";
            case 0 -> "head_whole";
            case 1 -> "head_half";
            default -> "head_black";
        };
    }

    private void ledgers(GuiGraphics g, int p, int m, int step, float hx, float headW, float top) {
        ledgers(g, p, m, step, hx, headW, top, INK);
    }

    private void ledgers(GuiGraphics g, int p, int m, int step, float hx, float headW, float top, int color) {
        int topLine = score.parts.get(p).clefAt(m).middleLine() + 4;
        int bottomLine = topLine - 8;
        for (int s = topLine + 2; s <= step; s += 2) {
            hLine(g, hx - space * 0.3f, hx + headW + space * 0.3f, yOn(top, p, m, s), color);
        }
        for (int s = bottomLine - 2; s >= step; s -= 2) {
            hLine(g, hx - space * 0.3f, hx + headW + space * 0.3f, yOn(top, p, m, s), color);
        }
    }

    /**
     * Note input's preview (SCOPE.md §8.1): a see-through note of the chosen value where a click would put
     * it, with its ledger lines and stem.
     */
    public void drawGhost(GuiGraphics g, float left, float top, float sx, float sy, Hit hit, int durationLog, int color) {
        float staff = top - sy + staffTop(hit.part());
        float x = left - sx + measureX[hit.measure()] + xAt(hit.measure(), hit.at());
        float y = yOn(staff, hit.part(), hit.measure(), hit.step());
        String head = durationLog <= -1 ? "head_breve" : durationLog == 0 ? "head_whole" : durationLog == 1 ? "head_half" : "head_black";
        float headW = ScoreGlyphs.width(head, scale);
        ledgers(g, hit.part(), hit.measure(), hit.step(), x, headW, staff, color);
        ScoreGlyphs.draw(g, head, x, y, scale, color);
        if (durationLog >= 1) {
            boolean up = hit.step() <= score.parts.get(hit.part()).clefAt(hit.measure()).middleLine();
            float stemX = up ? x + headW - lineThickness() : x;
            float end = up ? y - 3.5f * space : y + 3.5f * space;
            fill(g, stemX, Math.min(y, end), stemX + lineThickness(), Math.max(y, end), color);
            if (durationLog >= 3) {
                String flag = "flag_" + (durationLog == 3 ? 8 : durationLog == 4 ? 16 : durationLog == 5 ? 32 : 64) + (up ? "_up" : "_down");
                ScoreGlyphs.draw(g, flag, stemX, end, scale, color);
            }
        }
    }

    private void drawRest(GuiGraphics g, Chord c, float rx, float top, boolean wholeMeasure, int color) {
        if (wholeMeasure) {
            ScoreGlyphs.draw(g, "rest_whole", rx, top + space, scale, color);
            return;
        }
        switch (c.duration.log()) {
            case -1 -> ScoreGlyphs.draw(g, "rest_breve", rx, top + space, scale, color);
            case 0 -> ScoreGlyphs.draw(g, "rest_whole", rx, top + space, scale, color);
            case 1 -> ScoreGlyphs.draw(g, "rest_half", rx, top + 2 * space, scale, color);
            case 2 -> ScoreGlyphs.draw(g, "rest_quarter", rx, top + 2 * space, scale, color);
            case 3 -> ScoreGlyphs.draw(g, "rest_8", rx, top + 2 * space, scale, color);
            case 4 -> ScoreGlyphs.draw(g, "rest_16", rx, top + 2 * space, scale, color);
            case 5 -> ScoreGlyphs.draw(g, "rest_32", rx, top + 2 * space, scale, color);
            default -> ScoreGlyphs.draw(g, "rest_64", rx, top + 2 * space, scale, color);
        }
        for (int d = 0; d < c.duration.dots(); d++) {
            ScoreGlyphs.draw(g, "dot", rx + 10 * scale + d * 4 * scale, top + 1.5f * space, scale, color);
        }
    }

    private void drawGrace(GuiGraphics g, int p, int m, Chord c, float x, float top, int color) {
        for (Note n : c.notes) {
            float ny = yOn(top, p, m, n.staffStep());
            ScoreGlyphs.draw(g, "head_black", x, ny, scale * 0.6f, color);
            fill(g, x + 5.5f * scale, ny - 2.2f * space, x + 5.5f * scale + lineThickness(), ny, color);
        }
    }

    /** x of the next chord's head in this voice (for ties), on into the next measure. */
    private float nextX(int p, int m, int v, int i, float x) {
        List<Chord> voice = score.parts.get(p).measures.get(m).voices[v];
        List<Frac> starts = VoiceEdit.starts(voice);
        for (int j = i + 1; j < voice.size(); j++) {
            if (!voice.get(j).isGrace()) {
                return x + xAt(m, starts.get(j));
            }
        }
        return m + 1 < score.measureCount() ? measureX[m + 1] - measureX[m] + x + xAt(m + 1, Frac.ZERO) : x + sliceX[m].get(sliceX[m].size() - 1);
    }

    private void drawTie(GuiGraphics g, float x1, float y, float x2, boolean stemUp) {
        float dir = stemUp ? 1 : -1; // ties curve away from the stems
        int steps = Math.max(4, (int) ((x2 - x1) / 2));
        for (int k = 0; k <= steps; k++) {
            float t = k / (float) steps;
            float px = x1 + 1 + (x2 - x1 - 2) * t;
            float py = y + dir * (space * 0.6f + (float) Math.sin(Math.PI * t) * space * 0.6f);
            fill(g, px, py, px + 1, py + lineThickness(), INK);
        }
    }

    private void drawBeam(GuiGraphics g, List<Chord> voice, List<Integer> beam, List<Frac> starts, float x, int m, int p,
                          Map<Integer, Float> stemEnd, Map<Integer, Boolean> stemUp, int color) {
        int first = beam.get(0), last = beam.get(beam.size() - 1);
        boolean up = stemUp.get(first);
        float headW = ScoreGlyphs.width("head_black", scale);
        float x1 = x + xAt(m, starts.get(first)) + (up ? headW - lineThickness() : 0);
        float x2 = x + xAt(m, starts.get(last)) + (up ? headW : lineThickness());
        float y = stemEnd.get(first);
        float thick = space * 0.45f;
        int levels = Integer.MAX_VALUE;
        for (int i : beam) {
            levels = Math.min(levels, voice.get(i).duration.log() - 2);
        }
        for (int l = 0; l < levels; l++) {
            float by = up ? y + l * space * 0.75f : y - l * space * 0.75f - thick;
            fill(g, x1, by, x2, by + thick, color);
        }
        // shorter notes inside the beam get stubs for their extra beams
        for (int i : beam) {
            int extra = voice.get(i).duration.log() - 2 - levels;
            float sx = x + xAt(m, starts.get(i)) + (up ? headW - lineThickness() : 0);
            for (int l = 0; l < extra; l++) {
                float by = up ? y + (levels + l) * space * 0.75f : y - (levels + l) * space * 0.75f - thick;
                float stub = i == last ? -space : space;
                fill(g, Math.min(sx, sx + stub), by, Math.max(sx, sx + stub), by + thick, color);
            }
        }
    }

    private void drawMarks(GuiGraphics g, Chord c, int p, int m, int v, int i, float cx, float headW, float y, float top, int color) {
        float mid = cx + headW / 2;
        float ay = y;
        for (Articulation a : c.articulations) {
            ScoreElement e = ScoreElement.onChord(ScoreElement.Kind.ARTICULATION, p, m, v, i, a.ordinal());
            if (a == Articulation.FERMATA) {
                glyph(g, "fermata", mid, top - space * 0.8f, colorOf(e, color), e);
                continue;
            }
            String glyph = switch (a) {
                case STACCATO -> "staccato";
                case STACCATISSIMO -> "staccatissimo";
                case TENUTO -> "tenuto";
                case ACCENT -> "accent";
                case MARCATO -> "marcato";
                default -> null;
            };
            if (glyph != null) {
                glyph(g, glyph, mid, ay, colorOf(e, color), e);
                ay += (y > top + 2 * space ? 1 : -1) * space;
            }
        }
        if (c.ornament != Ornament.NONE) {
            String glyph = switch (c.ornament) {
                case TRILL -> "trill";
                case MORDENT -> "mordent";
                case INVERTED_MORDENT -> "inverted_mordent";
                case TURN -> "turn";
                default -> null;
            };
            if (glyph != null) {
                ScoreElement e = ScoreElement.onChord(ScoreElement.Kind.ORNAMENT, p, m, v, i, 0);
                glyph(g, glyph, mid, top - space * 1.4f, colorOf(e, color), e);
            }
        }
        if (c.dynamic != null) {
            ScoreElement e = ScoreElement.onChord(ScoreElement.Kind.DYNAMIC, p, m, v, i, 0);
            String letters = c.dynamic.name().toLowerCase(Locale.ROOT);
            float dx = cx - space * 0.5f, dy = dynamicY(top);
            for (char ch : letters.toCharArray()) {
                ScoreGlyphs.draw(g, "dyn_" + ch, dx, dy, scale, colorOf(e, INK));
                dx += (ch == 'm' ? 12 : ch == 'f' ? 8 : 9) * scale;
            }
            box(cx - space * 0.7f, dy - 1.6f * space, dx + space * 0.3f, dy + 0.7f * space, e);
        }
    }

    /**
     * An arpeggio's wavy line from {@code y1} down to {@code y2} at {@code x}, with an arrowhead at the top
     * (rolled up) or the bottom (rolled down) when it has one.
     */
    public static void drawArpeggio(GuiGraphics g, float x, float y1, float y2, com.minescore.score.Notation.Arpeggio type, float space, int color) {
        float amp = space * 0.25f, wave = space * 0.8f, thick = Math.max(1, space / 7);
        for (float y = y1; y <= y2; y += 0.5f) {
            float phase = ((y - y1) % wave) / wave; // a zigzag, one bend per 0.8 spaces
            float off = (phase < 0.5f ? phase * 4 - 1 : 3 - phase * 4) * amp;
            fill(g, x + off, y, x + off + thick, y + 1, color);
        }
        if (type == com.minescore.score.Notation.Arpeggio.UP || type == com.minescore.score.Notation.Arpeggio.DOWN) {
            boolean up = type == com.minescore.score.Notation.Arpeggio.UP;
            float tip = up ? y1 - space * 0.4f : y2 + space * 0.4f;
            for (int k = 0; k < (int) (space * 0.5f) + 1; k++) {
                float y = up ? tip + k : tip - k;
                fill(g, x - k * 0.8f, y, x + thick + k * 0.8f, y + 1, color);
            }
        }
    }

    /** Screen y of the dynamics under a staff whose top line is at {@code top}. */
    public float dynamicY(float top) {
        return top + 7.2f * space;
    }

    /** A selectable symbol. */
    private void glyph(GuiGraphics g, String glyph, float x, float y, int color, ScoreElement e) {
        ScoreGlyphs.draw(g, glyph, x, y, scale, color);
        float w = ScoreGlyphs.width(glyph, scale);
        box(x - space * 0.3f, y - space * 0.9f, x + w + space * 0.3f, y + space * 0.9f, e);
    }

    /** Things above the top staff that belong to the whole measure: tempo, rehearsal marks, endings, jumps. */
    private void drawBarMarks(GuiGraphics g, float ox, float oy, int first, int last) {
        if (score.parts.isEmpty()) {
            return;
        }
        float top = oy + staffTop(0);
        for (int m = first; m <= last && m >= 0; m++) {
            Bar bar = score.bars.get(m);
            float x = ox + measureX[m];
            float right = ox + measureX[m + 1];
            float y = top - 4.6f * space;
            if (m == 0 && bar.tempos.isEmpty()) {
                tempo(g, x + xAt(0, Frac.ZERO), y, score.tempo, "", INK);
            }
            for (int k = 0; k < bar.tempos.size(); k++) {
                Bar.TempoMark t = bar.tempos.get(k);
                ScoreElement e = ScoreElement.onBar(ScoreElement.Kind.TEMPO, m, k);
                float tx = x + xAt(m, t.at());
                float w = tempo(g, tx, y, t.bpm(), t.text(), colorOf(e, INK));
                box(tx - 1, y - 2, tx + w + 1, y + 9, e);
            }
            if (bar.rehearsal != null) {
                ScoreElement e = ScoreElement.onBar(ScoreElement.Kind.REHEARSAL, m, 0);
                int w = font.width(bar.rehearsal) + 6;
                g.renderOutline((int) x, (int) (y - 12), w, 12, colorOf(e, INK));
                g.drawString(font, bar.rehearsal, (int) x + 3, (int) (y - 10), colorOf(e, INK), false);
                box(x, y - 12, x + w, y, e);
            }
            if (bar.endings != 0) {
                ScoreElement e = ScoreElement.onBar(ScoreElement.Kind.ENDING, m, 0);
                int color = colorOf(e, INK);
                boolean starts = m == 0 || score.bars.get(m - 1).endings != bar.endings; // one bracket over a run of measures
                float vy = top - 2.2f * space;
                fill(g, x, vy, right - 2, vy + 1, color);
                if (starts) {
                    StringBuilder label = new StringBuilder();
                    for (int k = 0; k < 8; k++) {
                        if ((bar.endings & (1 << k)) != 0) {
                            label.append(label.isEmpty() ? "" : ", ").append(k + 1);
                        }
                    }
                    fill(g, x, vy, x + 1, vy + space, color);
                    g.drawString(font, label + ".", (int) x + 3, (int) vy + 2, color, false);
                }
                box(x, vy - 2, right, vy + 10, e);
            }
            for (Marker marker : bar.markers) {
                ScoreElement e = ScoreElement.onBar(ScoreElement.Kind.SIGN, m, marker.ordinal());
                switch (marker) {
                    case SEGNO -> glyph(g, "segno", x + space, top - 2.4f * space, colorOf(e, INK), e);
                    case CODA -> glyph(g, "coda", x + space, top - 2.4f * space, colorOf(e, INK), e);
                    default -> {
                        String text = switch (marker) {
                            case TO_CODA -> "To Coda";
                            case FINE -> "Fine";
                            case DC -> "D.C.";
                            case DC_AL_FINE -> "D.C. al Fine";
                            case DC_AL_CODA -> "D.C. al Coda";
                            case DS -> "D.S.";
                            case DS_AL_FINE -> "D.S. al Fine";
                            case DS_AL_CODA -> "D.S. al Coda";
                            default -> "";
                        };
                        float tx = right - font.width(text) - 2, ty = top - 2.2f * space - 10;
                        g.drawString(font, text, (int) tx, (int) ty, colorOf(e, INK), false);
                        box(tx - 1, ty - 1, tx + font.width(text) + 1, ty + 9, e);
                    }
                }
            }
        }
    }

    /** Draws a tempo mark; returns how wide it is. */
    private float tempo(GuiGraphics g, float x, float y, double bpm, String text, int color) {
        String label = text.isBlank() ? "" : text + " ";
        g.drawString(font, label, (int) x, (int) y, color, false);
        float nx = x + font.width(label);
        ScoreGlyphs.draw(g, "head_black", nx, y + 6, scale * 0.7f, color);
        fill(g, nx + 6.5f * scale, y - 2, nx + 6.5f * scale + 1, y + 6, color);
        String value = " = " + Math.round(bpm);
        g.drawString(font, value, (int) (nx + 7 * scale), (int) y, color, false);
        return nx + 7 * scale + font.width(value) - x;
    }

    // --- lines --------------------------------------------------------------------------------

    /** The chord in a voice starting at a point, or null. */
    private @Nullable Chord chordStarting(int p, int voice, com.minescore.score.Spanner.Pos pos) {
        if (pos.measure() < 0 || pos.measure() >= score.measureCount()) {
            return null;
        }
        List<Chord> v = score.parts.get(p).measures.get(pos.measure()).voices[voice];
        List<Frac> starts = VoiceEdit.starts(v);
        for (int i = 0; i < v.size(); i++) {
            if (!v.get(i).isGrace() && starts.get(i).equals(pos.at())) {
                return v.get(i);
            }
        }
        return null;
    }

    private float posX(com.minescore.score.Spanner.Pos pos) {
        int m = Math.max(0, Math.min(score.measureCount() - 1, pos.measure()));
        return measureX[m] + xAt(m, pos.at());
    }

    /** Where the chord at a point stops, in x: the next thing's position, so a hairpin covers its last note. */
    private float posEndX(int p, int voice, com.minescore.score.Spanner.Pos pos) {
        int m = Math.max(0, Math.min(score.measureCount() - 1, pos.measure()));
        Chord c = chordStarting(p, voice, pos);
        Frac len = c != null ? c.length() : Frac.of(1, 4);
        Frac end = Frac.min(pos.at().plus(len), score.lengthOf(m));
        return measureX[m] + (end.equals(score.lengthOf(m)) ? sliceX[m].get(sliceX[m].size() - 1) : xAt(m, end)) - space * 0.4f;
    }

    /** The y of a chord's top (or bottom) note at a point, or the staff's middle for a rest. */
    private float noteY(float top, int p, int voice, com.minescore.score.Spanner.Pos pos, boolean high) {
        Chord c = chordStarting(p, voice, pos);
        int m = Math.max(0, Math.min(score.measureCount() - 1, pos.measure()));
        if (c == null || c.isRest()) {
            return top + 2 * space;
        }
        int step = high ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (Note n : c.notes) {
            step = high ? Math.max(step, n.staffStep()) : Math.min(step, n.staffStep());
        }
        return yOn(top, p, m, step);
    }

    /** Slurs, hairpins, octave lines, glissandos and rit./accel. lines in one part. */
    private void drawLines(GuiGraphics g, int p, float ox, float oy, int first, int last) {
        List<com.minescore.score.Spanner> lines = score.parts.get(p).spanners;
        float top = oy + staffTop(p);
        float headW = ScoreGlyphs.width("head_black", scale);
        for (int k = 0; k < lines.size(); k++) {
            com.minescore.score.Spanner s = lines.get(k);
            if (s.end().measure() < first || s.start().measure() > last) {
                continue;
            }
            ScoreElement e = ScoreElement.line(p, k);
            int color = colorOf(e, VOICE_COLORS[s.voice()]);
            float x1 = ox + posX(s.start()), x2 = ox + posEndX(p, s.voice(), s.end());
            switch (s.type()) {
                case SLUR -> {
                    boolean above = s.voice() % 2 == 0;
                    float sx2 = ox + posX(s.end()) + headW;
                    float y1 = noteY(top, p, s.voice(), s.start(), above) + (above ? -space : space);
                    float y2 = noteY(top, p, s.voice(), s.end(), above) + (above ? -space : space);
                    float bulge = Math.min(2 * space, (sx2 - x1) * 0.12f) + space * 0.4f;
                    curve(g, x1 + headW / 2, y1, sx2 - headW / 2, y2, above ? -bulge : bulge, color);
                    box(x1, Math.min(y1, y2) - (above ? bulge + space : 0), sx2, Math.max(y1, y2) + (above ? 0 : bulge + space), e);
                }
                case CRESCENDO, DIMINUENDO -> {
                    float y = top + 6.4f * space, open = 0.7f * space;
                    boolean cresc = s.type() == com.minescore.score.Notation.SpannerType.CRESCENDO;
                    line(g, x1, y + (cresc ? 0 : -open), x2, y + (cresc ? -open : 0), color);
                    line(g, x1, y + (cresc ? 0 : open), x2, y + (cresc ? open : 0), color);
                    box(x1, y - open - 2, x2, y + open + 2, e);
                }
                case OTTAVA_8VA, OTTAVA_15MA, OTTAVA_8VB -> {
                    boolean below = s.type() == com.minescore.score.Notation.SpannerType.OTTAVA_8VB;
                    String label = switch (s.type()) {
                        case OTTAVA_15MA -> "15ma";
                        case OTTAVA_8VB -> "8vb";
                        default -> "8va";
                    };
                    float y = below ? top + 5.6f * space : top - 3.2f * space;
                    g.drawString(font, label, (int) x1, (int) (y - 4), color, false);
                    dashed(g, x1 + font.width(label) + 2, x2, y, color);
                    fill(g, x2 - 1, below ? y - space : y, x2, below ? y : y + space, color);
                    box(x1, y - 5, x2, y + 6, e);
                }
                case GLISSANDO -> {
                    float y1 = noteY(top, p, s.voice(), s.start(), true);
                    float gx2 = ox + posX(s.end()) - space * 0.3f;
                    float y2 = noteY(top, p, s.voice(), s.end(), true);
                    line(g, x1 + headW + space * 0.3f, y1, gx2, y2, color);
                    box(x1 + headW, Math.min(y1, y2) - 3, gx2, Math.max(y1, y2) + 3, e);
                }
                case RITARDANDO, ACCELERANDO -> {
                    String label = s.type() == com.minescore.score.Notation.SpannerType.RITARDANDO ? "rit." : "accel.";
                    float y = top - 3.9f * space;
                    g.drawString(font, label, (int) x1, (int) (y - 4), color, false);
                    if (x2 > x1 + font.width(label) + 4) {
                        dashed(g, x1 + font.width(label) + 2, x2, y, color);
                    }
                    box(x1, y - 5, Math.max(x2, x1 + font.width(label)), y + 6, e);
                }
            }
        }
    }

    private void dashed(GuiGraphics g, float x1, float x2, float y, int color) {
        for (float x = x1; x < x2; x += space) {
            fill(g, x, y, Math.min(x2, x + space * 0.5f), y + lineThickness(), color);
        }
    }

    /** A straight line, drawn in short steps. */
    private void line(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        int steps = Math.max(1, (int) Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int k = 0; k <= steps; k++) {
            float t = k / (float) steps;
            float x = x1 + (x2 - x1) * t, y = y1 + (y2 - y1) * t;
            fill(g, x, y, x + 1, y + lineThickness(), color);
        }
    }

    /** A slur's curve from one point to another, bowed by {@code bulge} (negative is up), thicker in the middle. */
    private void curve(GuiGraphics g, float x1, float y1, float x2, float y2, float bulge, int color) {
        int steps = Math.max(6, (int) ((x2 - x1) / 1.5f));
        for (int k = 0; k <= steps; k++) {
            float t = k / (float) steps;
            float x = x1 + (x2 - x1) * t;
            float bow = (float) Math.sin(Math.PI * t);
            float y = y1 + (y2 - y1) * t + bulge * bow;
            float thick = lineThickness() * (1 + bow);
            fill(g, x, bulge < 0 ? y : y - thick + lineThickness(), x + 1.2f, (bulge < 0 ? y : y - thick + lineThickness()) + thick, color);
        }
    }
}
