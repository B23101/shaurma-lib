package dev.shaurmalib.anim.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Шар: поточний кліп + ті, що ще догасають після переходу (кросфейд). Старші — на початку списку.
 * Обмеження в 4 записи: якщо кліпи міняють щокадру, старі обрізаються, а не накопичуються.
 */
final class AnimLayer {

    private static final int MAX_ENTRIES = 4;

    final Slot slot;
    final List<ClipPlayer> entries = new ArrayList<>(MAX_ENTRIES);

    AnimLayer(Slot slot) { this.slot = slot; }

    ClipPlayer current() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            ClipPlayer p = entries.get(i);
            if (!p.fadingOut()) return p;
        }
        return null;
    }

    void play(ClipPlayer p, double clock) {
        for (ClipPlayer e : entries) e.beginFadeOut(clock, Math.max(p.fadeIn, 1e-3f));
        entries.add(p);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
    }

    void stop(double clock, float fadeOut, String tagOrNull) {
        for (ClipPlayer e : entries) {
            if (tagOrNull == null || tagOrNull.equals(e.tag)) {
                e.beginFadeOut(clock, Float.isNaN(fadeOut) ? e.fadeOut : fadeOut);
            }
        }
    }

    void prune(double clock) {
        entries.removeIf(e -> e.finished(clock));
    }

    boolean isEmpty() { return entries.isEmpty(); }
}
