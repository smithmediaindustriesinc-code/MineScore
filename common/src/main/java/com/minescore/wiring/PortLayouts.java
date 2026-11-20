package com.minescore.wiring;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Jack layouts per block, loaded from {@code /minescore/ports.json}. */
public final class PortLayouts {
    private static final String RESOURCE = "/minescore/ports.json";
    private static @Nullable Map<String, Layout> layouts;

    private PortLayouts() {}

    /**
     * @param front the centre of the block's front face in model pixels (where a speaker's sound comes from)
     */
    public record Layout(BlockKind kind, List<Port> ports, Vec3 front) {
        public Optional<Port> port(String id) {
            return ports.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        /** The jack nearest a model-space point, if it lies within {@code tolerance} pixels. */
        public Optional<Port> nearest(Vec3 px, double tolerance) {
            Port best = null;
            double bestDistance = tolerance;
            for (Port port : ports) {
                double d = port.distanceTo(px);
                if (d <= bestDistance) {
                    best = port;
                    bestDistance = d;
                }
            }
            return Optional.ofNullable(best);
        }
    }

    /** The layout for a block, by registry path (e.g. {@code "amplifier"}). */
    public static @Nullable Layout get(String blockPath) {
        return all().get(blockPath);
    }

    public static synchronized Map<String, Layout> all() {
        if (layouts == null) {
            layouts = load();
        }
        return layouts;
    }

    private static Map<String, Layout> load() {
        try (InputStream in = PortLayouts.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Layout> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue;
                }
                result.put(entry.getKey(), parseLayout(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + RESOURCE, e);
        }
    }

    private static Layout parseLayout(String block, JsonObject json) {
        List<Port> ports = new ArrayList<>();
        Map<String, Port> seen = new HashMap<>();
        for (JsonElement element : json.getAsJsonArray("ports")) {
            JsonObject p = element.getAsJsonObject();
            Vec3 from = vec(p.getAsJsonArray("from"));
            Vec3 to = vec(p.getAsJsonArray("to"));
            Direction face = p.has("face") ? Direction.byName(p.get("face").getAsString()) : Direction.SOUTH;
            Port port = new Port(p.get("id").getAsString(), "out".equals(p.get("dir").getAsString()),
                    Signal.byId(p.get("signal").getAsString()), new AABB(from, to), face);
            if (seen.put(port.id(), port) != null) {
                throw new IllegalStateException("Duplicate jack " + port.id() + " on " + block);
            }
            ports.add(port);
        }
        Vec3 front = json.has("front") ? vec(json.getAsJsonArray("front")) : new Vec3(8, 8, 0);
        return new Layout(BlockKind.byId(json.get("kind").getAsString()), List.copyOf(ports), front);
    }

    private static Vec3 vec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}
