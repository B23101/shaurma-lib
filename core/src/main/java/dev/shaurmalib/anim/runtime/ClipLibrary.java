package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.mesh.GeoMesh;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Усі завантажені кліпи й моделі пропсів. Перезавантаження ресурсів (F3+T) атомарно
 * підміняє вміст ({@link #replaceAll}); програвання, що вже йдуть, доживають зі старим кліпом,
 * а нові запити беруть новий — без NPE посеред кадру.
 */
public final class ClipLibrary {

    private volatile Map<String, AnimationClip> clips = new ConcurrentHashMap<>();
    private volatile Map<String, GeoMesh> meshes = new ConcurrentHashMap<>();

    public AnimationClip get(String id) {
        return id == null ? null : clips.get(id);
    }

    public boolean has(String id) { return get(id) != null; }

    public GeoMesh mesh(String id) { return id == null ? null : meshes.get(id); }

    public void register(AnimationClip clip) { clips.put(clip.id(), clip); }

    public void registerMesh(String id, GeoMesh mesh) { meshes.put(id, mesh); }

    public void replaceAll(Map<String, AnimationClip> newClips, Map<String, GeoMesh> newMeshes) {
        this.clips = new ConcurrentHashMap<>(newClips);
        this.meshes = new ConcurrentHashMap<>(newMeshes);
    }

    public Collection<AnimationClip> all() { return clips.values(); }

    public int size() { return clips.size(); }
}
