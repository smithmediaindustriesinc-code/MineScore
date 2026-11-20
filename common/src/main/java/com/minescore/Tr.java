package com.minescore;

import java.util.Locale;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * Text written in English in the code (palette names, key-binding names, setting names and help, editor
 * buttons), shown in the game's language. Each English string's key is made from its words, e.g.
 * "Insert before" is {@code minescore.text.insert_before}, so the language files can translate it; anything
 * a language doesn't have stays English. tools/gen_resources.py collects every such string (TranslationSourceTest
 * lists the ones built from data) and writes the translations.
 */
public final class Tr {
    private static final int MAX_KEY = 72;

    private Tr() {}

    /** The language-file key for an English string. Must match gen_resources.py text_key. */
    public static String key(String english) {
        String slug = english.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.length() > MAX_KEY) {
            slug = slug.substring(0, MAX_KEY) + "_" + Integer.toHexString(english.hashCode());
        }
        return "minescore.text." + (slug.isEmpty() ? Integer.toHexString(english.hashCode()) : slug);
    }

    /** Marks English kept as data (shown later through {@link #s}), so the extractor finds it. Returns it unchanged. */
    public static String en(String english) {
        return english;
    }

    /** {@code english} in the game's language. */
    public static String s(String english) {
        return Language.getInstance().getOrDefault(key(english), english);
    }

    /** {@code english} (which may have %s for {@code args}) as a translatable component. */
    public static Component c(String english, Object... args) {
        return Component.translatableWithFallback(key(english), english, args);
    }
}
