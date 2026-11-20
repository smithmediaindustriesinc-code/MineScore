package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.EffectChain;
import java.util.List;

/** Lets tests outside the package reach {@link EffectProcessor#expressed}. */
public final class EffectProcessorAccess {
    private EffectProcessorAccess() {}

    public static List<CardSettings> expressed(EffectChain chain) {
        return EffectProcessor.expressed(chain);
    }
}
