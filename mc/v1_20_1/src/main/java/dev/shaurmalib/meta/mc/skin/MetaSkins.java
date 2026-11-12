package dev.shaurmalib.meta.mc.skin;

import dev.shaurmalib.meta.MetaMode;
import dev.shaurmalib.meta.client.MetaClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Скіни режимів (клієнт). Режим без власного скіна отримує {@link GlassSkin} зі своєю палітрою
 * ({@code MetaMode#theme}) — працює, але «рідним» меню стане лише з власним скіном.
 */
public final class MetaSkins {

    private static final Map<String, MetaSkin> SKINS = new ConcurrentHashMap<>();
    private static final Map<String, MetaSkin> FALLBACK = new ConcurrentHashMap<>();

    private MetaSkins() {}

    public static void register(String modeId, MetaSkin skin) { SKINS.put(modeId, skin); }

    public static MetaSkin of(MetaMode mode) {
        if (mode == null) return FALLBACK.computeIfAbsent("", k -> new GlassSkin(dev.shaurmalib.meta.MetaTheme.glass()));
        MetaSkin s = SKINS.get(mode.id);
        return s != null ? s : FALLBACK.computeIfAbsent(mode.id, k -> new GlassSkin(mode.theme));
    }

    public static MetaSkin current() { return of(MetaClient.mode()); }
}
