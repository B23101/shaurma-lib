package dev.shaurmalib.anim.runtime;

import dev.shaurmalib.anim.clip.AnimationClip;
import dev.shaurmalib.anim.clip.ClipEvent;

/** Отримувач подій таймлайну (звуки, частинки, мітки). Адаптер версії реалізує звук/частинки. */
@FunctionalInterface
public interface AnimEventSink {
    void onEvent(AnimSubject subject, AnimationClip clip, ClipEvent event);
}
