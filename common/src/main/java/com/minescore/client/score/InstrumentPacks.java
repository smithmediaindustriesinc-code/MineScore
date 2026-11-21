package com.minescore.client.score;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minescore.MineScore;
import com.minescore.score.Instruments;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads the instruments resource packs add (SCOPE.md §9.11), from {@code assets/<namespace>/minescore/instruments.json}
 * in every pack:
 *
 * <pre>{"instruments": [{"id": "violin", "name": "Violin", "sound": "mypack:violin", "center": 69, "low": 55, "high": 100}]}</pre>
 *
 * {@code id} gets the namespace in front if it hasn't one; {@code sound} is a sound event from the pack's
 * {@code sounds.json}, {@code center} the MIDI note it plays at as recorded, and {@code low}/{@code high} its
 * range. {@code "pitched": false} makes it a drum. A later pack's instrument with the same id wins.
 */
public final class InstrumentPacks {
    private InstrumentPacks() {}

    /** Reads every pack's instruments again (cheap; done when the editor opens and before a score is rendered). */
    public static void reload() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        Map<String, Instruments.Custom> found = new LinkedHashMap<>();
        for (String namespace : resources.getNamespaces()) {
            ResourceLocation file = new ResourceLocation(namespace, "minescore/instruments.json");
            for (Resource resource : resources.getResourceStack(file)) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (JsonElement element : json.getAsJsonArray("instruments")) {
                        Instruments.Custom c = parse(namespace, element.getAsJsonObject());
                        found.put(c.id(), c);
                    }
                } catch (Exception e) {
                    MineScore.LOGGER.warn("Couldn't read the instruments in {} ({})", file, resource.sourcePackId(), e);
                }
            }
        }
        Instruments.setCustom(found.values());
    }

    private static Instruments.Custom parse(String namespace, JsonObject o) {
        String id = o.get("id").getAsString();
        if (!id.contains(":")) {
            id = namespace + ":" + id;
        }
        String name = o.has("name") ? o.get("name").getAsString() : id.substring(id.indexOf(':') + 1);
        String sound = o.get("sound").getAsString();
        int center = o.has("center") ? o.get("center").getAsInt() : 66;
        int low = o.has("low") ? o.get("low").getAsInt() : center - 12;
        int high = o.has("high") ? o.get("high").getAsInt() : center + 12;
        boolean pitched = !o.has("pitched") || o.get("pitched").getAsBoolean();
        return new Instruments.Custom(id, name.length() > 32 ? name.substring(0, 32) : name, sound, center, Math.min(low, high),
                Math.max(low, high), pitched);
    }
}
