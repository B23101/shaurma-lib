package dev.shaurmalib.platform.service;

import dev.shaurmalib.platform.text.Text;

/** Конвертація {@link Text} ⇄ рідний Component (+ JSON для мережі/збереження). */
public interface TextBridge {
    Object toNative(Text text);
    Text fromNative(Object nativeComponent);
    String toJson(Text text);
    Text fromJson(String json);
}
