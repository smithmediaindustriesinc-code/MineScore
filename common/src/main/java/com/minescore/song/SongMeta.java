package com.minescore.song;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.regex.Pattern;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A song the server holds.
 *
 * @param id      SHA-256 of the song file, in hex
 * @param format  file extension the song is stored as: mp3, ogg, flac or msad ({@link com.minescore.audio.AdpcmCodec})
 * @param title   the title it was first cut with
 * @param seconds its length
 */
public record SongMeta(String id, String format, String title, double seconds) {
    private static final Pattern ID = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FORMAT = Pattern.compile("mp3|ogg|flac|msad");

    /** Song ids name files, so only accept exactly what a SHA-256 hash looks like. */
    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static boolean validFormat(String format) {
        return format != null && FORMAT.matcher(format).matches();
    }

    /** The track id clients play it by. */
    public String trackId() {
        return "song:" + id;
    }

    public String toJson(int copies) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("format", format);
        json.addProperty("title", title);
        json.addProperty("seconds", seconds);
        json.addProperty("copies", copies);
        return json.toString();
    }

    public static SongMeta fromJson(String text) {
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        return new SongMeta(json.get("id").getAsString(), json.get("format").getAsString(), json.get("title").getAsString(),
                json.get("seconds").getAsDouble());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(format);
        buf.writeUtf(title);
        buf.writeDouble(seconds);
    }

    public static SongMeta read(FriendlyByteBuf buf) {
        return new SongMeta(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readDouble());
    }
}
