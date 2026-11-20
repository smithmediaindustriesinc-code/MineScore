package com.minescore.score;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One step of an edit to a score, as it travels between an editor and the server (SCOPE.md §8): whole
 * measures, bars and headers are sent as they now are, so applying a change twice does no harm and the
 * last change to a measure wins. {@link #inverse} gives what undoes it.
 */
public sealed interface ScoreChange {
    /** Applies it; false (and nothing changed) if it doesn't fit this score. */
    boolean apply(Score score);

    /** The changes that put {@code before} back as it was, worked out before this one is applied. */
    List<ScoreChange> inverse(Score before);

    void write(FriendlyByteBuf buf);

    /** One part's measure, as it now reads. */
    record MeasureContent(int part, int measure, CompoundTag data) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            if (part < 0 || part >= score.parts.size() || measure < 0 || measure >= score.measureCount()) {
                return false;
            }
            score.parts.get(part).measures.set(measure, Part.Measure.load(data));
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            if (part < 0 || part >= before.parts.size() || measure < 0 || measure >= before.measureCount()) {
                return List.of();
            }
            return List.of(new MeasureContent(part, measure, before.parts.get(part).measures.get(measure).save()));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(0);
            buf.writeVarInt(part);
            buf.writeVarInt(measure);
            buf.writeNbt(data);
        }
    }

    /** A measure's shared marks (time, key, tempo, repeats…), as they now read. */
    record BarContent(int measure, CompoundTag data) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            if (measure < 0 || measure >= score.measureCount()) {
                return false;
            }
            score.bars.set(measure, Bar.load(data));
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            return measure < 0 || measure >= before.measureCount() ? List.of() : List.of(new BarContent(measure, before.bars.get(measure).save()));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(1);
            buf.writeVarInt(measure);
            buf.writeNbt(data);
        }
    }

    /** The titles, starting time and key signatures, and tempo. */
    record Header(CompoundTag data) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            score.loadHeader(data);
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            return List.of(new Header(before.saveHeader()));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(2);
            buf.writeNbt(data);
        }
    }

    /** A part's name, instrument, clef, mixer settings and lines (everything but its measures). */
    record PartHeader(int part, CompoundTag data) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            if (part < 0 || part >= score.parts.size()) {
                return false;
            }
            score.parts.get(part).loadHeader(data);
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            return part < 0 || part >= before.parts.size() ? List.of() : List.of(new PartHeader(part, before.parts.get(part).saveHeader()));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(3);
            buf.writeVarInt(part);
            buf.writeNbt(data);
        }
    }

    /** An empty measure before {@code index} in every part. */
    record InsertMeasure(int index) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            return score.insertMeasure(index);
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            return List.of(new DeleteMeasure(index));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(4);
            buf.writeVarInt(index);
        }
    }

    /** Removes a measure from every part. */
    record DeleteMeasure(int index) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            return score.deleteMeasure(index);
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            if (index < 0 || index >= before.measureCount() || before.measureCount() <= 1) {
                return List.of();
            }
            List<ScoreChange> back = new ArrayList<>();
            back.add(new InsertMeasure(index));
            back.add(new BarContent(index, before.bars.get(index).save()));
            for (int p = 0; p < before.parts.size(); p++) {
                back.add(new MeasureContent(p, index, before.parts.get(p).measures.get(index).save()));
                back.add(new PartHeader(p, before.parts.get(p).saveHeader())); // its lines that touched the measure
            }
            return back;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(5);
            buf.writeVarInt(index);
        }
    }

    /** A new part at the bottom, with its measures. */
    record AddPart(CompoundTag data) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            if (score.parts.size() >= Score.MAX_PARTS) {
                return false;
            }
            Part part = Part.load(data);
            while (part.measures.size() > score.measureCount()) {
                part.measures.remove(part.measures.size() - 1);
            }
            while (part.measures.size() < score.measureCount()) {
                part.measures.add(Part.Measure.rest(score.lengthOf(part.measures.size())));
            }
            score.parts.add(part);
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            return List.of(new RemovePart(before.parts.size()));
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(6);
            buf.writeNbt(data);
        }
    }

    /** Removes a part (a score keeps at least one). */
    record RemovePart(int index) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            if (index < 0 || index >= score.parts.size() || score.parts.size() <= 1) {
                return false;
            }
            score.parts.remove(index);
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            if (index < 0 || index >= before.parts.size() || before.parts.size() <= 1) {
                return List.of();
            }
            // Put it back at the bottom, then move it up into place one swap at a time.
            List<ScoreChange> back = new ArrayList<>();
            back.add(new AddPart(before.parts.get(index).save()));
            for (int i = before.parts.size() - 1; i > index; i--) {
                back.add(new SwapParts(i - 1, i));
            }
            return back;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(7);
            buf.writeVarInt(index);
        }
    }

    /** Swaps two parts' places (moving a part up or down). */
    record SwapParts(int a, int b) implements ScoreChange {
        @Override
        public boolean apply(Score score) {
            if (a < 0 || b < 0 || a >= score.parts.size() || b >= score.parts.size()) {
                return false;
            }
            java.util.Collections.swap(score.parts, a, b);
            return true;
        }

        @Override
        public List<ScoreChange> inverse(Score before) {
            return List.of(this);
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(8);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
        }
    }

    static ScoreChange read(FriendlyByteBuf buf) {
        int kind = buf.readByte();
        return switch (kind) {
            case 0 -> new MeasureContent(buf.readVarInt(), buf.readVarInt(), nbt(buf));
            case 1 -> new BarContent(buf.readVarInt(), nbt(buf));
            case 2 -> new Header(nbt(buf));
            case 3 -> new PartHeader(buf.readVarInt(), nbt(buf));
            case 4 -> new InsertMeasure(buf.readVarInt());
            case 5 -> new DeleteMeasure(buf.readVarInt());
            case 6 -> new AddPart(nbt(buf));
            case 7 -> new RemovePart(buf.readVarInt());
            case 8 -> new SwapParts(buf.readVarInt(), buf.readVarInt());
            default -> throw new IllegalArgumentException("Unknown score change " + kind);
        };
    }

    private static CompoundTag nbt(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }

    static void writeAll(FriendlyByteBuf buf, List<ScoreChange> changes) {
        buf.writeVarInt(changes.size());
        changes.forEach(c -> c.write(buf));
    }

    static List<ScoreChange> readAll(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > 20_000) {
            throw new IllegalArgumentException("Too many score changes: " + n);
        }
        List<ScoreChange> changes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            changes.add(read(buf));
        }
        return changes;
    }

    /**
     * Applies changes in order to a score, collecting what undoes them (in the order to apply it). Stops
     * at the first that doesn't fit; returns null then, with the score already restored.
     */
    static List<ScoreChange> applyAll(Score score, List<ScoreChange> changes) {
        List<List<ScoreChange>> undo = new ArrayList<>();
        for (ScoreChange change : changes) {
            List<ScoreChange> back = change.inverse(score);
            if (!change.apply(score)) {
                for (int i = undo.size() - 1; i >= 0; i--) {
                    undo.get(i).forEach(c -> c.apply(score));
                }
                return null;
            }
            undo.add(back);
        }
        List<ScoreChange> all = new ArrayList<>();
        for (int i = undo.size() - 1; i >= 0; i--) {
            all.addAll(undo.get(i));
        }
        return all;
    }
}
