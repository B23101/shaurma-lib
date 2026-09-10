package dev.shaurmalib.common.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Записаний дельта-шлях lookaround-анімації — узагальнення
 * {@code SDLookaroundPath} snipers_shaurma (план, п. 3.16). Симетричний до
 * {@link RecordedPath}, але тримає {@link DeltaAngleFrame} (лише yaw/pitch
 * дельта) замість абсолютних координат — формат, придатний для
 * застосування до довільної кількості spawn-точок з різним базовим кутом.
 */
public final class RecordedDeltaPath {

    private final List<DeltaAngleFrame> frames;

    public RecordedDeltaPath() {
        this.frames = new ArrayList<>();
    }

    public RecordedDeltaPath(List<DeltaAngleFrame> frames) {
        this.frames = new ArrayList<>(frames);
    }

    public void addFrame(float yawDelta, float pitchDelta) {
        frames.add(new DeltaAngleFrame(yawDelta, pitchDelta));
    }

    public List<DeltaAngleFrame> frames() {
        return Collections.unmodifiableList(frames);
    }

    public int size() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public double durationSeconds() {
        return frames.size() / 20.0;
    }
}
