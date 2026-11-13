package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.mesh.GeoMesh;

import java.util.Collection;
import java.util.List;
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

    public void register(AnimationClip clip) {
        clips.put(clip.id(), clip);
        holdBindings = buildHoldBindings(clips.values());
    }

    public void registerMesh(String id, GeoMesh mesh) { meshes.put(id, mesh); }

    public void replaceAll(Map<String, AnimationClip> newClips, Map<String, GeoMesh> newMeshes) {
        this.clips = new ConcurrentHashMap<>(newClips);
        this.meshes = new ConcurrentHashMap<>(newMeshes);
        this.holdBindings = buildHoldBindings(newClips.values());
    }

    private volatile List<AnimBinding> holdBindings = List.of();

    /**
     * Правила зі «станів тримання» кліпів ({@code "hold"} у файлі): перебудовуються при кожному
     * перезавантаженні ресурсів, тож новий кліп з Blockbench працює після F3+T без жодного коду.
     */
    public List<AnimBinding> holdBindings() { return holdBindings; }

    private static List<AnimBinding> buildHoldBindings(Collection<AnimationClip> all) {
        List<AnimBinding> out = new java.util.ArrayList<>();
        for (AnimationClip c : all) {
            dev.shaurmalib.anim.clip.HoldSpec h = c.hold();
            if (h == null) continue;
            out.add(AnimBinding.when("hold:" + c.id(), s -> h.matches(s.inputs().mainHandItem, s.inputs().offHandItem))
                .play(c.id()).slot(Slot.parse(c.defaults().slot(), Slot.HOLD)).priority(h.priority()).rig(c.rig()));
        }
        out.sort(java.util.Comparator.comparing(AnimBinding::id));
        return List.copyOf(out);
    }

    public Collection<AnimationClip> all() { return clips.values(); }

    public int size() { return clips.size(); }
}
