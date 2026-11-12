package dev.shaurmalib.forge.animation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.shaurmalib.common.animation.DeltaAngleFrame;
import dev.shaurmalib.common.animation.RecordedDeltaPath;
import dev.shaurmalib.common.animation.RecordedPath;
import dev.shaurmalib.common.animation.RecordedPathFrame;

/**
 * JSON-кодек для {@link RecordedPath}/{@link RecordedDeltaPath} (план,
 * п. 3.16) — навмисно тримає той самий формат файлу, що й старі
 * {@code RecordedPath}/{@code SDLookaroundPath} snipers_shaurma
 * ({@code {"frames":[{"x":...,"y":...,"z":...,"yaw":...,"pitch":...}]}} і
 * {@code {"frames":[{"yawDelta":...,"pitchDelta":...}]}} відповідно), щоб
 * усі вже записані анімаційні файли (в т.ч. старі з зайвим полем
 * {@code bodyYaw}, яке тут просто ігнорується) лишались читабельними без
 * жодної міграції даних на диску. Gson доступний транзитивно через
 * Minecraft/Forge classpath — та сама залежність, що вже неявно
 * використовувалась в оригіналі, тут не додає нового explicit-requirement
 * у {@code build.gradle}.
 */
public final class RecordedPathJson {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecordedPathJson() {}

    public static String toJson(RecordedPath path) {
        JsonObject root = new JsonObject();
        JsonArray frames = new JsonArray();
        for (RecordedPathFrame f : path.frames()) {
            JsonObject o = new JsonObject();
            o.addProperty("x", f.x());
            o.addProperty("y", f.y());
            o.addProperty("z", f.z());
            o.addProperty("yaw", f.yaw());
            o.addProperty("pitch", f.pitch());
            frames.add(o);
        }
        root.add("frames", frames);
        return GSON.toJson(root);
    }

    public static RecordedPath fromJson(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        RecordedPath path = new RecordedPath();
        if (root == null || !root.has("frames")) return path;
        for (JsonElement el : root.getAsJsonArray("frames")) {
            JsonObject o = el.getAsJsonObject();
            path.addFrame(
                    o.get("x").getAsDouble(),
                    o.get("y").getAsDouble(),
                    o.get("z").getAsDouble(),
                    o.get("yaw").getAsFloat(),
                    o.get("pitch").getAsFloat()
            );
        }
        return path;
    }

    public static String toJson(RecordedDeltaPath path) {
        JsonObject root = new JsonObject();
        JsonArray frames = new JsonArray();
        for (DeltaAngleFrame f : path.frames()) {
            JsonObject o = new JsonObject();
            o.addProperty("yawDelta", f.yawDelta());
            o.addProperty("pitchDelta", f.pitchDelta());
            frames.add(o);
        }
        root.add("frames", frames);
        return GSON.toJson(root);
    }

    public static RecordedDeltaPath fromDeltaJson(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        RecordedDeltaPath path = new RecordedDeltaPath();
        if (root == null || !root.has("frames")) return path;
        for (JsonElement el : root.getAsJsonArray("frames")) {
            JsonObject o = el.getAsJsonObject();
            path.addFrame(o.get("yawDelta").getAsFloat(), o.get("pitchDelta").getAsFloat());
        }
        return path;
    }
}
