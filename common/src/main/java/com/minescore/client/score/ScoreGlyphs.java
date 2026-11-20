package com.minescore.client.score;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minescore.MineScore;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The score editor's notation symbols (SCOPE.md §8): original pixel art in one atlas
 * ({@code textures/gui/score_glyphs.png}) that a resource pack can redraw. The symbols are white, so each
 * is drawn in whatever colour it's given. One staff space is 8 atlas pixels.
 */
public final class ScoreGlyphs {
    public static final ResourceLocation ATLAS = MineScore.id("textures/gui/score_glyphs.png");
    /** A staff space in atlas pixels. */
    public static final float SPACE = 8;
    private static final Map<String, float[]> LAYOUT = new HashMap<>();

    private static final ResourceLocation LAYOUT_FILE = MineScore.id("score_glyphs.json");

    private ScoreGlyphs() {}

    /** Reads the layout again next time (the resource packs may have changed). */
    public static void reload() {
        LAYOUT.clear();
    }

    private static float[] cell(String name) {
        if (LAYOUT.isEmpty()) {
            // from the resource packs, so a pack that redraws the atlas can lay it out its own way
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(LAYOUT_FILE)) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    JsonArray a = e.getValue().getAsJsonArray();
                    float[] cell = new float[6];
                    for (int i = 0; i < 6; i++) {
                        cell[i] = a.get(i).getAsFloat();
                    }
                    LAYOUT.put(e.getKey(), cell);
                }
            } catch (Exception e) {
                MineScore.LOGGER.error("Couldn't read the score glyph layout", e);
            }
        }
        return LAYOUT.get(name);
    }

    /** How wide a glyph is, in GUI pixels at {@code scale}. */
    public static float width(String name, float scale) {
        float[] cell = cell(name);
        return cell == null ? 0 : cell[2] * scale;
    }

    /** Draws a glyph with its anchor at (x, y), {@code scale} GUI pixels per atlas pixel, in {@code argb}. */
    public static void draw(GuiGraphics g, String name, float x, float y, float scale, int argb) {
        float[] cell = cell(name);
        if (cell == null) {
            return;
        }
        RenderSystem.enableBlend();
        g.setColor((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        g.pose().pushPose();
        g.pose().translate(x - cell[4] * scale, y - cell[5] * scale, 0);
        g.pose().scale(scale, scale, 1);
        g.blit(ATLAS, 0, 0, cell[0], cell[1], (int) cell[2], (int) cell[3], 256, 256);
        g.pose().popPose();
        g.setColor(1, 1, 1, 1);
    }
}
