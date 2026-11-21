package com.minescore.client.score;

import com.minescore.audio.notes.NoteInstrument;
import com.minescore.score.Chord;
import com.minescore.score.Instruments;
import com.minescore.score.Note;
import com.minescore.score.Part;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Lets the player hear a note as they place, select or move it, as MuseScore does (SCOPE.md §9.4): one
 * note-block hit in the part's instrument, heard only by them.
 */
public final class Audition {
    private Audition() {}

    public static void play(Part part, Chord chord, int octaveShift) {
        if (chord == null || chord.isRest()) {
            return;
        }
        for (Note note : chord.notes) {
            play(part, note, octaveShift);
        }
    }

    public static void play(Part part, Note note, int octaveShift) {
        Instruments.Custom custom = note.drum() == null ? Instruments.custom(part.custom) : null;
        if (custom != null) { // a resource pack's instrument: its own sound, at the nearest pitch a sound can reach
            double rate = custom.pitched() ? Instruments.rate(custom, note.pitch() + octaveShift) : 1;
            while (rate > 2) {
                rate /= 2;
            }
            while (rate < 0.5) {
                rate *= 2;
            }
            SoundEvent sound = SoundEvent.createVariableRangeEvent(new ResourceLocation(custom.sound()));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, (float) rate, 0.7f * Math.max(0.2f, part.volume)));
            return;
        }
        NoteInstrument instrument = note.drum() != null ? note.drum() : part.instrument;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation("minecraft", instrument.soundEvent()));
        if (sound == null) {
            return;
        }
        // A note block can only play 0.5–2× its sample, so notes beyond that sound an octave or two closer.
        float pitch = (float) (instrument.pitched() ? instrument.rateFor(note.pitch() + octaveShift) : 1);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.7f * Math.max(0.2f, part.volume)));
    }
}
