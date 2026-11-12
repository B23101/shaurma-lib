package dev.shaurmalib.anim.io;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.mesh.GeoMesh;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Збирає бібліотеку з ресурсів, нічого не знаючи про ResourceManager гри: адаптер передає
 * пари «шлях → текст». Куди що класти:
 *
 * <pre>
 * assets/&lt;ns&gt;/shaurma_anim/**.json          кліпи (власний формат або Bedrock), риг з файлу, інакше player
 * assets/&lt;ns&gt;/shaurma_anim/**.geo.json      моделі пропсів, id = ns:шлях_без_.geo.json
 * assets/&lt;ns&gt;/player_animation(s)/*.json    старі PlayerAnimator-кліпи — читаються як риг player
 * assets/&lt;ns&gt;/animations/**.animation.json  GeckoLib-кліпи geo-моделей, риг geo:ns:файл, id ns:файл/ім'я
 * </pre>
 */
public final class AnimResourceLoader {

    public static final String ROOT = "shaurma_anim/";

    private final Map<String, AnimationClip> clips = new LinkedHashMap<>();
    private final Map<String, GeoMesh> meshes = new LinkedHashMap<>();
    private final java.util.Set<String> fromRoot = new java.util.HashSet<>();
    private final Consumer<String> warn;
    private int files;

    public AnimResourceLoader(Consumer<String> warn) { this.warn = warn; }

    /** Чи цікавить рушій цей шлях (щоб адаптер не читав усі json пакету ресурсів). */
    public static boolean accepts(String path) {
        if (!path.endsWith(".json")) return false;
        return path.startsWith(ROOT) || path.startsWith("player_animation/") || path.startsWith("player_animations/")
            || (path.startsWith("animations/") && path.endsWith(".animation.json"));
    }

    /** @param path шлях усередині {@code assets/<ns>/} */
    public void accept(String namespace, String path, String content) {
        if (!accepts(path)) return;
        files++;
        String file = path.substring(path.lastIndexOf('/') + 1);
        try {
            if (path.startsWith(ROOT) && path.endsWith(".geo.json")) {
                String id = namespace + ":" + path.substring(ROOT.length(), path.length() - ".geo.json".length());
                meshes.put(id, GeoMesh.parse(content));
                return;
            }
            String rig;
            String base;
            if (path.startsWith("animations/")) {
                base = file.substring(0, file.length() - ".animation.json".length());
                rig = "geo:" + namespace + ":" + base;
            } else {
                base = file.substring(0, file.length() - ".json".length());
                rig = "player";
            }
            boolean primary = path.startsWith(ROOT);
            for (AnimationClip c : ClipParser.parse(content, namespace, base, rig, warn)) {
                // shaurma_anim/ пріоритетніший за старі теки незалежно від порядку читання файлів
                if (!primary && fromRoot.contains(c.id())) continue;
                clips.put(c.id(), c);
                if (primary) fromRoot.add(c.id());
            }
        } catch (RuntimeException e) {
            warn.accept(namespace + ":" + path + " — " + e.getMessage());
        }
    }

    public Map<String, AnimationClip> clips() { return clips; }

    public Map<String, GeoMesh> meshes() { return meshes; }

    public int files() { return files; }
}
