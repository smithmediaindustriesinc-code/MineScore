package com.minescore.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.minescore.MineScore;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The colours of the screens MineScore draws in code (SCOPE.md §11.1), from {@code assets/minescore/minescore/theme.json}:
 * <pre>{"panel": "C0101014", "text": "FFE8E8EE", ...}</pre>
 * as ARGB hex. The mod's own file has every key; a resource pack's (the built-in Retro and Modern packs, or any
 * other) changes just the keys it lists, later packs winning. Read again whenever resources reload.
 */
public final class UiTheme {
    private static final ResourceLocation FILE = MineScore.id("minescore/theme.json");
    private static final int MISSING = 0xFFFF00FF;
    private static volatile Map<String, Integer> colors = Map.of();

    private UiTheme() {}

    /** The colour for {@code key}, magenta if no theme file has it. */
    public static int c(String key) {
        Integer color = colors.get(key);
        return color != null ? color : MISSING;
    }

    public static void reload(ResourceManager resources) {
        Map<String, Integer> read = new HashMap<>();
        for (Resource resource : resources.getResourceStack(FILE)) { // lowest-priority pack first
            try (Reader reader = resource.openAsReader()) {
                for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                    read.put(e.getKey(), (int) Long.parseLong(e.getValue().getAsString(), 16));
                }
            } catch (Exception e) {
                MineScore.LOGGER.warn("Couldn't read the screen colours in {} ({})", FILE, resource.sourcePackId(), e);
            }
        }
        colors = Map.copyOf(read);
    }
}
