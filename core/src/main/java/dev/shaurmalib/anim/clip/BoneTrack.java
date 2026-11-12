package dev.shaurmalib.anim.clip;

/** Канали однієї кістки в кліпі. {@code null} — канал не анімується (кістка прозора для нього). */
public record BoneTrack(Channel rotation, Channel position, Channel scale) {

    public boolean has(ChannelType type) {
        return get(type) != null;
    }

    public Channel get(ChannelType type) {
        return switch (type) {
            case ROTATION -> rotation;
            case POSITION -> position;
            case SCALE -> scale;
        };
    }

    public float lastTime() {
        float t = 0;
        if (rotation != null) t = Math.max(t, rotation.lastTime());
        if (position != null) t = Math.max(t, position.lastTime());
        if (scale != null) t = Math.max(t, scale.lastTime());
        return t;
    }

    public enum ChannelType { ROTATION, POSITION, SCALE }
}
